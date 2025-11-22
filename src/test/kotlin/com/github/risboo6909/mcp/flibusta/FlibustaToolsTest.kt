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
        // HTML без таблицы (пустой список рекомендаций) но с pager чтобы не было ошибки парсинга пагинации
        val rawHtml = """
            <html><body>
            <ul class='pager'><li class='pager-last'><a href='/rec?view=authors&page=5'>5</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(Result.success(rawHtml))
        whenever(httpHelper.fetchMultiplePages(any())).thenAnswer { _ ->
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>()
        }
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
        // HTML с валидной таблицей рекомендаций + pager
        val rawHtml = """
            <html><body>
            <form name='formrecs'>
              <table>
                <tr><th>Author</th><th>Book</th><th>Genre</th><th>Recs</th></tr>
                <tr>
                  <td><a href='/a/123'>Author Name</a></td>
                  <td><a href='/b/456'>Book Title</a></td>
                  <td><a href='/g/genre-slug'>Genre Name</a></td>
                  <td>12</td>
                </tr>
              </table>
            </form>
            <ul class='pager'><li class='pager-last'><a href='/rec?view=books&page=5'>5</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any())).thenReturn(Result.success(rawHtml))
        whenever(httpHelper.fetchMultiplePages(any())).thenAnswer { _ ->
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>()
        }
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
