package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class FlibustaToolsTest {

    private val httpHelper = mock<HttpClientInterface>()
    private val flibustaTools = FlibustaTools(httpHelper)

    @Test
    fun recommendationsByAuthor_returnsValidResponse_whenValidInput() = runBlocking {
        val rawHtml = "<html><body><div class='rec'>valid recommendations</div></body></html>"
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(
            Result.success(rawHtml),
        )
        whenever(httpHelper.fetchMultiplePages(any())).thenReturn(
            listOf(rawHtml, rawHtml, rawHtml) to emptyList(),
        )
        val response = flibustaTools.getRecommendedAuthors(0, 3)

        assertEquals(emptyList<String>(), response.errors)
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenEndPageBeforeStartPage() = runBlocking {
        val response = flibustaTools.getRecommendedAuthors(10, 5)
        assertEquals(
            listOf("Error: End page must be greater than start page"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenStartPageNegative() = runBlocking {
        val response = flibustaTools.getRecommendedAuthors(-1, 1)
        assertEquals(
            listOf("Error: Start page must be 0 or greater"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenEndPageNegative() = runBlocking {
        val response = flibustaTools.getRecommendedAuthors(0, -1)
        assertEquals(
            listOf("Error: End page must be 0 or greater"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByBook_returnsValidResponse_whenValidInput() = runBlocking {
        val rawHtml = "<html><body><div class='rec'>valid book recommendations</div></body></html>"
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(Result.success(rawHtml))
        whenever(httpHelper.fetchMultiplePages(any())).thenReturn(
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>(),
        )
        val response = flibustaTools.getRecommendedBooks(0, 3)

        assertEquals(emptyList<String>(), response.errors)
    }

    @Test
    fun recommendationsByBook_returnsError_whenEndPageBeforeStartPage() = runBlocking {
        val response = flibustaTools.getRecommendedBooks(10, 5)

        assertEquals(
            listOf("Error: End page must be greater than start page"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByBook_returnsError_whenStartPageNegative() = runBlocking {
        val response = flibustaTools.getRecommendedBooks(-1, 1)

        assertEquals(
            listOf("Error: Start page must be 0 or greater"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByBook_returnsError_whenEndPageNegative() = runBlocking {
        val response = flibustaTools.getRecommendedBooks(0, -1)

        assertEquals(
            listOf("Error: End page must be 0 or greater"),
            response.errors,
        )
    }
}
