package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

const val POPULAR_BOOKS_PER_PAGE = 100

class PopularBooksExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getPopularBooks(
        period: PopularBooksPeriod,
        startPage: Int,
        endPage: Int,
    ): McpResponse<List<PopularBook>> {
        val (payload, isLastPage, errors) = getWithPaginationParallel(
            "$POPULAR_BOOKS_URL/${period.suffix}",
            POPULAR_BOOKS_PER_PAGE,
            httpHelper,
            ::parse,
            mapOf(),
            startPage,
            endPage,
        )
        return McpResponse(
            payload = payload,
            errors = errors,
        )
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<PopularBook> {
        val doc = Jsoup.parse(rawHtml, baseUrl)

        val listItems =
            doc.select("h1.title + ul > li").ifEmpty {
                doc.select("h1.title ~ ul > li")
            }

        return listItems.mapNotNull { li ->
            val bookLink = li.selectFirst("a[href^=/b/]") ?: return@mapNotNull null

            val bookHref = bookLink.attr("href")
            // TODO: extract this to common method
            val bookUrl = bookLink.absUrl("href").ifBlank { bookHref }
            val bookId = bookHref.substringAfterLast('/').toIntOrNull()
            val bookTitle = bookLink.text().trim()

            val authors = li.select("a[href^=/a/]").map { a ->
                extractAuthorInfo(a, false)
            }

            PopularBook(
                book = null,
                authors = authors,
            )
        }
    }
}
