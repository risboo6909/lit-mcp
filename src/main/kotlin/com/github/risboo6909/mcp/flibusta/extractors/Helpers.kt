package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.addPagination
import com.github.risboo6909.utils.joinKeyValueParams
import com.github.risboo6909.utils.logAndCollectError
import org.jsoup.Jsoup
import org.slf4j.LoggerFactory

private val LOG = LoggerFactory.getLogger("com.github.risboo6909.mcp.flibusta.extractors.Pagination")

data class PaginatedExtraction<T>(
    val items: List<T>,
    val totalPages: Int?,
    val errors: List<String>,
)

/**
 * Fetches pages in parallel from startPage to endPage (exclusive). The same responses are used
 * both to parse items and to determine the total number of pages, avoiding a separate pager request.
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
