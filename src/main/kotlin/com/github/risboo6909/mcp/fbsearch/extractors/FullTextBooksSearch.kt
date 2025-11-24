package com.github.risboo6909.mcp.fbsearch.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class FullTextBooksSearch(private val httpHelper: HttpClientInterface) {

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(FullTextBooksSearch::class.java.name)
    }

    suspend fun searchBooks(query: String, startPage: Int, endPage: Int): McpResponse<SearchResult> {
        return McpResponse(
            SearchResult(
                page = 1,
            ),
            listOf("Not yet implemented"),
        )
    }
}
