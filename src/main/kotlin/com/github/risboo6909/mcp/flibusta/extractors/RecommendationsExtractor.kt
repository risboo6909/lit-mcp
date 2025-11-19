package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.joinParams
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.math.min

const val NO_PAGE_LIMIT = -1

class RecommendationsExtractor(private val httpHelper: HttpClientInterface) {

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(RecommendationsExtractor::class.java.name)
    }

    suspend fun getRecommendedBooks(
        params: Map<String, String>,
        recommendationsRequired: Int,
        startPage: Int,
    ): List<BookRecommendation> {
        return getRecommendationsSerial(
            httpHelper,
            ::parseRecommendedBooks,
            params,
            recommendationsRequired,
            startPage,
        )
    }

    suspend fun getRecommendedAuthors(
        params: Map<String, String>,
        recommendationsRequired: Int,
        startPage: Int,
    ): List<AuthorRecommendation> {
        return getRecommendationsSerial(
            httpHelper,
            ::parseRecommendedAuthors,
            params,
            recommendationsRequired,
            startPage,
        )
    }

    private suspend fun <T> getRecommendationsSerial(
        httpHelper: HttpClientInterface,
        parser: (String) -> List<T>,
        params: Map<String, String>,
        recommendationsRequired: Int,
        startPage: Int,
    ): List<T> {
        val allRecommendations = mutableListOf<T>()
        val url = joinParams(RECOMMENDATIONS_URL, params)
        var page = startPage

        while (recommendationsRequired == NO_PAGE_LIMIT ||
            allRecommendations.size < recommendationsRequired
        ) {
            val urlWithPage = if (url.contains("?")) {
                url + "&page=${page++}"
            } else {
                url + "?page=${page++}"
            }
            try {
                val rawHtml = httpHelper.queryGet(urlWithPage)
                val parsed = try {
                    parser(rawHtml)
                } catch (pe: Throwable) {
                    LOG.error("Parser error while parsing page=$page for url=$urlWithPage, skipping this page", pe)
                    continue
                }
                if (parsed.isEmpty()) {
                    LOG.info("Parser returned empty results for page=$page url=$urlWithPage, stopping pagination")
                    break
                }
                allRecommendations.addAll(parsed)
            } catch (e: Exception) {
                // Log HTTP or other fetch errors and stop
                LOG.error("HTTP error while fetching url=$urlWithPage", e)
                break
            }
        }

        return allRecommendations.subList(
            0,
            min(recommendationsRequired, allRecommendations.size),
        )
    }

    private fun parseRecommendedAuthors(rawHtml: String): List<AuthorRecommendation> {
        val doc: Document = Jsoup.parse(rawHtml)

        val dataTable: Element = doc.select("form[name=formrecs] table").last() ?: return emptyList()

        val allRows = dataTable.select("> tbody > tr, > tr")
        if (allRows.isEmpty()) return emptyList()

        val rows = allRows.drop(1)

        fun extractInt(text: String): Int = Regex("""\d+""").find(text)?.value?.toInt() ?: 0

        return rows.mapNotNull { tr ->
            val tds = tr.select("> td")
            if (tds.size != 4) return@mapNotNull null

            val authorCell = tds[0]
            val booksCell = tds[1]
            val usersCell = tds[2]
            val recsCell = tds[3]

            val authorLink = authorCell.selectFirst("a") ?: return@mapNotNull null
            val authorName = authorLink.text().trim()
            val authorUrl = authorLink.absUrl("href").ifBlank { authorLink.attr("href") }
            val authorId = authorLink.attr("href")
                .substringAfter("/a/", "")
                .takeWhile { it.isDigit() }
                .toIntOrNull()

            AuthorRecommendation(
                AuthorRef(
                    id = authorId,
                    name = authorName,
                    url = authorUrl,
                    isTranslator = false,
                ),
                booksCount = extractInt(booksCell.text()),
                usersCount = extractInt(usersCell.text()),
                recsCount = extractInt(recsCell.text()),
            )
        }
    }

    private fun parseRecommendedBooks(html: String, baseUrl: String = FLIBUSTA_BASE_URL): List<BookRecommendation> {
        val doc: Document = Jsoup.parse(html, baseUrl)

        val dataTable: Element = doc.select("form[name=formrecs] table").last() ?: return emptyList()

        val allRows = dataTable.select("> tbody > tr, > tr")
        if (allRows.isEmpty()) return emptyList()
        val rows = allRows.drop(1) // пропускаем шапку

        return rows.mapNotNull { tr ->
            val tds = tr.select("> td")
            if (tds.size != 4) return@mapNotNull null

            val authorCell = tds[0]
            val bookCell = tds[1]
            val genreCell = tds[2]
            val recsCell = tds[3]

            val authors = authorCell.select("a[href^=/a/]").map {
                extractAuthorInfo(it, false)
            }

            val bookA = bookCell.select("a[href^=/b/]").last() ?: return@mapNotNull null
            val book = BookRef(
                id = extractIdFromHref(bookA.attr("href"), "/b"),
                title = bookA.text().trim(),
                url = bookA.absUrl("href").ifBlank { bookA.attr("href") },
            )

            val genres = genreCell.select("a[href^=/g/]").map {
                    a ->
                extractGenreInfo(a)
            }

            val recs = extractFirstInt(recsCell.text())

            BookRecommendation(
                authors = authors,
                book = book,
                genres = genres,
                recommendationsCount = recs,
            )
        }
    }

    private fun extractFirstInt(s: String): Int = Regex("""\d+""").find(s)?.value?.toInt() ?: 0
}
