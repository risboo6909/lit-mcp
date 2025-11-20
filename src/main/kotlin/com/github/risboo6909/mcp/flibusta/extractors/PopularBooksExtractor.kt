package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface

class PopularBooksExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getPopularBooks(period: PopularBooksPeriod): McpResponse<List<BookDetails>> {
        return McpResponse(
            null,
            emptyList(),
        )
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<BookDetails> {
        return listOf()
    }
}
