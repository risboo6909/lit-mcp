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
        val rawHtml = "<html>valid recommendations</html>"
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(rawHtml)
        val response = flibustaTools.recommendationsByAuthor(10, 0)

        assertEquals(emptyList<String>(), response.errors)
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenRecommendationsExceedMax() = runBlocking {
        val response = flibustaTools.recommendationsByAuthor(600, 0)
        assertEquals(listOf("Error: Maximum number of recommendations is 500"),
            response.errors)
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenRecommendationsAreZero() = runBlocking {
        val response = flibustaTools.recommendationsByAuthor(0, 0)
        assertEquals(listOf("Error: Number of recommendations must be greater than 0"),
            response.errors)
    }

    @Test
    fun recommendationsByAuthor_returnsError_whenRecommendationsAreNegative() = runBlocking {
        val response = flibustaTools.recommendationsByAuthor(-5, 0)
        assertEquals(listOf("Error: Number of recommendations must be greater than 0"),
            response.errors)
    }

    @Test
    fun recommendationsByBook_returnsValidResponse_whenValidInput() = runBlocking {
        val rawHtml = "<html>valid book recommendations</html>"
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(rawHtml)
        val response = flibustaTools.recommendationsByBook(10, 0)

        assertEquals(emptyList<String>(), response.errors)
    }

    @Test
    fun recommendationsByBook_returnsError_whenRecommendationsExceedMax() = runBlocking {
        val response = flibustaTools.recommendationsByBook(600, 0)

        assertEquals(listOf("Error: Maximum number of recommendations is 500"),
            response.errors)
    }

    @Test
    fun recommendationsByBook_returnsError_whenRecommendationsAreZero() = runBlocking {
        val response = flibustaTools.recommendationsByBook(0, 0)

        assertEquals(listOf("Error: Number of recommendations must be greater than 0"),
            response.errors)
    }

    @Test
    fun recommendationsByBook_returnsError_whenRecommendationsAreNegative() = runBlocking {
        val response = flibustaTools.recommendationsByBook(-1, 0)

        assertEquals(listOf("Error: Number of recommendations must be greater than 0"),
            response.errors)
    }
}
