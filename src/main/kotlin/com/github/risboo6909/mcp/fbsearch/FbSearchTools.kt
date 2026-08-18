package com.github.risboo6909.mcp.fbsearch

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.fbsearch.extractors.FullTextBooksSearch
import com.github.risboo6909.mcp.fbsearch.extractors.SearchResult
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.executeWithTimeout
import org.springaicommunity.mcp.annotation.McpTool
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

const val DEFAULT_TOOL_TIMEOUT_MILLIS: Long = 120 * 1000

@Service
class FbSearchTools(
    private val httpHelper: HttpClientInterface,
    @Value("\${lit-mcp.tool-timeout-millis:120000}")
    private val toolTimeoutMillis: Long,
) {
    private val fullTextSearch = FullTextBooksSearch(httpHelper)

    @McpTool(
        name = "fbSearch",
        title = "Full Text Books Search",
        description = "[TODO] [fbSearch] Full text search for books on fbsearch.ru",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun fullTextBooksSearch(): McpResponse<SearchResult> = executeWithTimeout(toolTimeoutMillis) {
        fullTextSearch.searchBooks("test", 1, 1)
    }
}
