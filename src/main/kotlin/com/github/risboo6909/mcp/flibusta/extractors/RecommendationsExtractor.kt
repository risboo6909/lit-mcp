package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.addPagination
import com.github.risboo6909.utils.joinKeyValueParams
import com.github.risboo6909.utils.logAndCollectError
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.slf4j.Logger
import org.slf4j.LoggerFactory

const val RECOMMENDATIONS_PER_PAGE = 50

class RecommendationsExtractor(private val httpHelper: HttpClientInterface) {

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(RecommendationsExtractor::class.java.name)
    }

    suspend fun getRecommendedBooks(
        params: Map<String, String>,
        startPage: Int,
        endPage: Int,
    ): McpResponse<RecommendationsResponse> {
        val (payload, isLastPage, errors) = getRecommendationsParallel(
            httpHelper,
            ::parseRecommendedBooks,
            params,
            startPage,
            endPage,
        )
        return McpResponse(
            RecommendationsResponse(
                bookRecommendations = payload,
                isLastPage = isLastPage,
            ),
            errors,
        )
    }

    suspend fun getRecommendedAuthors(
        params: Map<String, String>,
        startPage: Int,
        endPage: Int,
    ): McpResponse<RecommendationsResponse> {
        val (payload, isLastPage, errors) = getRecommendationsParallel(
            httpHelper,
            ::parseRecommendedAuthors,
            params,
            startPage,
            endPage,
        )
        return McpResponse(
            RecommendationsResponse(
                authorRecommendations = payload,
                isLastPage = isLastPage,
            ),
            errors,
        )
    }

    // Helper to process raw HTML and update recommendations list
    private fun <T> processRawHtml(
        allRecommendations: MutableList<T>,
        allErrors: MutableList<String>,
        url: String,
        rawHtml: String,
        parser: (String) -> List<T>,
    ): Boolean {
        val parsed = try {
            parser(rawHtml)
        } catch (pe: Throwable) {
            logAndCollectError(
                LOG,
                allErrors,
                "Parser error while parsing page for url=$url, " +
                    "skipping this page",
                pe,
            )
            return false
        }

        allRecommendations.addAll(parsed)

        if (parsed.size < RECOMMENDATIONS_PER_PAGE) {
            LOG.info(
                "Parser returned less than page size (${RECOMMENDATIONS_PER_PAGE})" +
                    " results for url=$url, stopping pagination",
            )
            return true
        }

        return false
    }

    private suspend fun <T> getRecommendationsParallel(
        httpHelper: HttpClientInterface,
        parser: (String) -> List<T>,
        params: Map<String, String>,
        startPage: Int,
        endPage: Int,
    ): Triple<List<T>, Boolean, List<String>> {
        val allRecommendations = mutableListOf<T>()
        val parseErrors = mutableListOf<String>()
        val url = joinKeyValueParams(RECOMMENDATIONS_URL, params)

        val urls = (startPage until endPage).map {
            addPagination(url, it)
        }

        val (payloads, networkErrors) = httpHelper.fetchMultiplePages(urls)
        val containsLastPage = payloads.map {
            processRawHtml(allRecommendations, parseErrors, url, it, parser)
        }.any { it }

        return Triple(allRecommendations, containsLastPage, parseErrors + networkErrors)
    }

    /**
     * This function fetches recommendation pages serially until it reaches the end page,
     * or until the parser returns empty results or less than a full page of results.
     *
     * It may be slow for large page ranges, use parallel version instead.
     */
    private suspend fun <T> getRecommendationsSerial(
        httpHelper: HttpClientInterface,
        parser: (String) -> List<T>,
        params: Map<String, String>,
        startPage: Int,
        endPage: Int,
    ): Triple<List<T>, Boolean, List<String>> {
        val allRecommendations = mutableListOf<T>()
        val allErrors = mutableListOf<String>()
        val url = joinKeyValueParams(RECOMMENDATIONS_URL, params)

        var isLastPage = false
        var page = startPage

        while (page < endPage) {
            val urlWithPage = addPagination(url, page)
            val rawHtml = try {
                httpHelper.queryGet(urlWithPage).getOrThrow()
            } catch (e: Exception) {
                logAndCollectError(
                    LOG,
                    allErrors,
                    "HTTP error while fetching page=$page url=$urlWithPage, skipping this page",
                    e,
                )
                page++
                continue
            }

            if (processRawHtml(allRecommendations, allErrors, url, rawHtml, parser)) {
                isLastPage = true
                break
            }

            page++
        }

        return Triple(allRecommendations, isLastPage, allErrors)
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
                AuthorInfo(
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
            val book = BookInfo(
                id = extractIdFromHref(bookA.attr("href"), "/b"),
                title = bookA.text().trim(),
                url = bookA.absUrl("href").ifBlank { bookA.attr("href") },
            )

            val genres = genreCell.select("a[href^=/g/]").map { a ->
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
