package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor.Companion.LOG
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.addPagination
import com.github.risboo6909.utils.joinKeyValueParams
import com.github.risboo6909.utils.logAndCollectError

suspend fun <T> getWithPaginationParallel(
    url: String,
    resultsPerPage: Int,
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    params: Map<String, String>,
    startPage: Int,
    endPage: Int,
): Triple<List<T>, Boolean, List<String>> {
    val allResults = mutableListOf<T>()
    val parseErrors = mutableListOf<String>()
    val url = joinKeyValueParams(url, params)

    val urls = (startPage until endPage).map {
        addPagination(url, it)
    }

    val (payloads, networkErrors) = httpHelper.fetchMultiplePages(urls)
    val containsLastPage = payloads.map {
        processRawHtml(resultsPerPage, allResults, parseErrors, url, it, parser)
    }.any { it }

    return Triple(allResults, containsLastPage, parseErrors + networkErrors)
}

/**
 * This function fetches recommendation pages serially until it reaches the end page,
 * or until the parser returns empty results or less than a full page of results.
 *
 * It may be slow for large page ranges, use parallel version instead.
 */
private suspend fun <T> getWithPaginationSerial(
    url: String,
    resultsPerPage: Int,
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    params: Map<String, String>,
    startPage: Int,
    endPage: Int,
): Triple<List<T>, Boolean, List<String>> {
    val allResults = mutableListOf<T>()
    val allErrors = mutableListOf<String>()

    val url = joinKeyValueParams(url, params)

    var isLastPage = false

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

        if (processRawHtml(resultsPerPage, allResults, allErrors, url, rawHtml, parser)) {
            isLastPage = true
            break
        }
    }

    return Triple(allResults, isLastPage, allErrors)
}

// Helper to process raw HTML and update recommendations list
private fun <T> processRawHtml(
    resultsPerPage: Int,
    allResults: MutableList<T>,
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

    allResults.addAll(parsed)

    if (parsed.size < resultsPerPage) {
        LOG.info(
            "Parser returned less than page size ($resultsPerPage)" +
                " results for url=$url, stopping pagination",
        )

        return true
    }

    return false
}
