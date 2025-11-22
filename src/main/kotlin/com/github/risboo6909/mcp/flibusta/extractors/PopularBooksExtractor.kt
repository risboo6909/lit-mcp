package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

class PopularBooksExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getPopularBooks(
        period: PopularBooksPeriod,
        startPage: Int,
        endPage: Int,
    ): McpResponse<PopularBooksResponse> {
        val url = "$POPULAR_BOOKS_URL/${period.suffix}"
        val (totalPages, pagerError) = getTotalPages(
            url,
            mapOf(),
            httpHelper,
        )
        val (payload, errors) = getWithPaginationParallel(
            url,
            httpHelper,
            ::parse,
            mapOf(),
            startPage,
            endPage,
        )
        return McpResponse(
            PopularBooksResponse(
                payload,
                totalPages,
            ),
            errors = errors + pagerError,
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
            val book = extractBookInfo(bookLink)

            val authors = li.select("a[href^=/a/]").map { a ->
                extractAuthorInfo(a, false)
            }

            PopularBook(
                book = book,
                authors = authors,
            )
        }
    }
}
