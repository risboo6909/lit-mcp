package com.github.risboo6909.mcp.fbsearch

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.fbsearch.extractors.FullTextBooksSearch
import com.github.risboo6909.mcp.fbsearch.extractors.SearchResult
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.executeWithTimeout
import org.springaicommunity.mcp.annotation.McpTool

const val FETCH_TIMEOUT_MILLIS: Long = 60 * 1000

class FbSearchTools(private val httpHelper: HttpClientInterface) {
    private val fullTextSearch = FullTextBooksSearch(httpHelper)

    @McpTool(
        name = "fbSearch",
        title = "Full Text Books Search",
        description = "[fbSearch] Full text search for books on fbsearch.ru",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun fullTextBooksSearch(): McpResponse<SearchResult> = executeWithTimeout(FETCH_TIMEOUT_MILLIS) {
        fullTextSearch.searchBooks("test", 1, 1)
    }
}
