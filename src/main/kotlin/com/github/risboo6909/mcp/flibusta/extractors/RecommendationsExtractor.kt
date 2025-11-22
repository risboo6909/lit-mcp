package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
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
        val (payload, isLastPage, errors) = getWithPaginationParallel(
            RECOMMENDATIONS_URL,
            RECOMMENDATIONS_PER_PAGE,
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
        val (payload, isLastPage, errors) = getWithPaginationParallel(
            RECOMMENDATIONS_URL,
            RECOMMENDATIONS_PER_PAGE,
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
