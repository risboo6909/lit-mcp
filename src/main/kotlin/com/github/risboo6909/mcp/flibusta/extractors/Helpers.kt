package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor.Companion.LOG
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.addPagination
import com.github.risboo6909.utils.joinKeyValueParams
import com.github.risboo6909.utils.logAndCollectError
import org.jsoup.Jsoup

data class PaginatedExtraction<T>(
    val items: List<T>,
    val totalPages: Int?,
    val errors: List<String>,
)

/**
 * Fetches pages in parallel from startPage to endPage (exclusive). The same responses are used
 * both to parse items and to determine the total number of pages, avoiding a separate pager request.
 *
 * This is preferable to the serial version for large page ranges.
 */
suspend fun <T> getWithPaginationParallel(
    url: String,
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    params: Map<String, String>,
    startPage: Int,
    endPage: Int,
): PaginatedExtraction<T> {
    val allResults = mutableListOf<T>()
    val parseErrors = mutableListOf<String>()

    val url = joinKeyValueParams(url, params)
    val urls = (startPage until endPage).map {
        addPagination(url, it)
    }

    val (payloads, networkErrors) = httpHelper.fetchMultiplePages(urls)
    payloads.forEach {
        processRawHtml(allResults, parseErrors, url, it, parser)
    }

    val pagerHtml = payloads.firstOrNull { it.isNotBlank() }
    val (totalPages, pagerError) = if (pagerHtml == null) {
        null to "Failed to extract total pages: all fetched responses were empty"
    } else {
        extractLastPageNumber(Jsoup.parse(pagerHtml))
    }

    return PaginatedExtraction(
        items = allResults,
        totalPages = totalPages,
        errors = parseErrors + networkErrors + listOfNotNull(pagerError),
    )
}

/**
 * This function fetches recommendation pages serially until it reaches the end page,
 * or until the parser returns empty results or less than a full page of results.
 *
 * It may be slow for large page ranges, use parallel version instead.
 */
private suspend fun <T> getWithPaginationSerial(
    url: String,
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    params: Map<String, String>,
    startPage: Int,
    endPage: Int,
): Pair<List<T>, List<String>> {
    val allResults = mutableListOf<T>()
    val allErrors = mutableListOf<String>()

    val url = joinKeyValueParams(url, params)

    for (page in startPage until endPage) {
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
            continue
        }
        processRawHtml(allResults, allErrors, url, rawHtml, parser)
    }
    return allResults to allErrors
}

// Helper to process raw HTML and update recommendations list
private fun <T> processRawHtml(
    allResults: MutableList<T>,
    allErrors: MutableList<String>,
    url: String,
    rawHtml: String,
    parser: (String) -> List<T>,
) {
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
        return
    }
    allResults.addAll(parsed)
}
