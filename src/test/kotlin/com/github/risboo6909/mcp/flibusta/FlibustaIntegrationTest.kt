package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookDetails
import com.github.risboo6909.utils.HttpClient
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

/**
 * Integration test: performs a real HTTP request and parses a Flibusta book page.
 * URL: https://flibusta.is/b/776085
 *
 * Note: this is a real test — it will run only if the test environment
 * has internet access. `assumeTrue` is used to mark the test as skipped
 * when offline instead of failing it.
 */
class FlibustaIntegrationTest {

    @Test
    fun getBookInfoById_fetchesAndParsesRealPage() = runBlocking {
        // Check network availability of flibusta.is by attempting a quick request
        // (a HEAD is not required — we use HttpClient.queryGet and accept that it may throw).
        val httpClient = HttpClient()

        // Try a quick request to the site root; if it fails — skip the test
        val reachable = try {
            val root = httpClient.queryGet("https://flibusta.is", retries = 3)
            root.getOrNull()?.isNotBlank() == true
        } catch (_: Exception) {
            false
        }

        assumeTrue(reachable, "Network unreachable or flibusta.is is not accessible — skipping integration test")

        val tools = FlibustaTools(httpClient)
        val response: McpResponse<List<BookDetails>> = tools.getBookInfoByIds(listOf(776085))

        assertTrue(response.errors.isEmpty(), "Expected no errors, but got: ${response.errors}")

        val booksList = response.payload
        assertNotNull(booksList, "Expected payload to be a list of BookDetails")
        assertEquals(1, booksList!!.size, "Expected exactly 1 book in the response")

        val details = booksList[0]

        // Safe check: downloads can be nullable
        val downloadsNotEmpty = details.downloads?.isNotEmpty() ?: false
        assertTrue(
            downloadsNotEmpty || (details.coverUrl != null) || (details.annotation != null),
            "Expected at least downloads, coverUrl or annotation to be present for real book page",
        )
    }
}
