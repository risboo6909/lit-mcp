package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor.Companion.LOG
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.addPagination
import com.github.risboo6909.utils.joinKeyValueParams
import com.github.risboo6909.utils.logAndCollectError
import org.jsoup.Jsoup

suspend fun getTotalPages(
    url: String,
    params: Map<String, String>,
    httpHelper: HttpClientInterface,
): Pair<Int?, List<String>> {
    val fullUrl = joinKeyValueParams(url, params)
    val res = httpHelper.queryGet(fullUrl)

    val rawHtml = res.getOrElse { e ->
        return null to listOf(
            "Network error while fetching '$fullUrl': " +
                "${e.message ?: e::class.simpleName}",
        )
    }

    if (rawHtml.isBlank()) {
        return null to listOf("Empty response body from '$fullUrl'")
    }

    val doc = Jsoup.parse(rawHtml)
    val (pages, error) = extractLastPageNumber(doc)

    if (pages != null) {
        return pages to emptyList()
    }

    return null to listOf(error ?: "Failed to extract total pages from document")
}

suspend fun <T> getWithPaginationParallel(
    url: String,
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    params: Map<String, String>,
    startPage: Int,
    endPage: Int,
): Pair<List<T>, List<String>> {
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

    return allResults to parseErrors + networkErrors
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
