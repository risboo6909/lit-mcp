package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class FlibustaToolsTest {

    private val httpHelper = mock<HttpClientInterface>()
    private val flibustaTools = FlibustaTools(httpHelper, DEFAULT_TOOL_TIMEOUT_MILLIS)

    @Test
    fun genresList_cachesSuccessfulResponse() = runBlocking {
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul><li><a href='/g/sf'>Научная фантастика</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))

        val firstResponse = flibustaTools.getGenresList()
        val secondResponse = flibustaTools.getGenresList()

        assertEquals(firstResponse, secondResponse)
        assertEquals("sf", secondResponse.payload!!.single().slug)
        verify(httpHelper, times(1)).queryGet(eq("https://flibusta.is/g"), any<Int>())
    }

    @Test
    fun popularBooks_keepsFastPathWithoutGenreOptions() = runBlocking {
        val popularBooksHtml = """
            <html><body>
            <h1 class='title'>Популярные книги</h1>
            <ul><li><a href='/a/1'>Author</a> - <a href='/b/101'>Book</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(popularBooksHtml))
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>()))
            .thenReturn(listOf(popularBooksHtml) to emptyList())

        val response = flibustaTools.getPopularBooksList()

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(101, response.payload!!.popularBooks!!.single().book!!.id)
        verify(httpHelper, times(1)).fetchMultiplePages(any<List<String>>(), any<Int>())
    }

    @Test
    fun bookInfo_enrichesNumericGenreWithSlug() = runBlocking {
        val bookHtml = """
            <html><body>
            <h1 class='title'>Science Fiction Book</h1>
            <a class='genre' href='/g/12'>Научная фантастика</a>
            </body></html>
        """.trimIndent()
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul><li><a href='/g/sf'>Научная фантастика</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>()))
            .thenReturn(listOf(bookHtml) to emptyList())
        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))

        val response = flibustaTools.getBookInfoByIds(listOf(101))

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(12, response.payload!!.single().genres!!.single().id)
        assertEquals("sf", response.payload!!.single().genres!!.single().slug)
    }

    @Test
    fun popularBooks_returnsError_whenStartPageNegative() = runBlocking {
        val response = flibustaTools.getPopularBooksList(startPage = -1, endPage = 1)

        assertEquals(
            listOf("Error: Start page must be 0 or greater"),
            response.errors,
        )
    }

    @Test
    fun popularBooks_returnsError_whenEndPageNegative() = runBlocking {
        val response = flibustaTools.getPopularBooksList(startPage = 0, endPage = -1)

        assertEquals(
            listOf("Error: End page must be 0 or greater"),
            response.errors,
        )
    }

    @Test
    fun recommendationsByAuthor_returnsValidResponse_whenValidInput() = runBlocking {
        // HTML без таблицы (пустой список рекомендаций) но с pager чтобы не было ошибки парсинга пагинации
        val rawHtml = """
            <html><body>
            <ul class='pager'><li class='pager-last'><a href='/rec?view=authors&page=5'>5</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(rawHtml))
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { _ ->
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
                  <td><a href='/g/12'>Genre Name</a></td>
                  <td>12</td>
                </tr>
              </table>
            </form>
            <ul class='pager'><li class='pager-last'><a href='/rec?view=books&page=5'>5</a></li></ul>
            </body></html>
        """.trimIndent()
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul><li><a href='/g/genre-slug'>Genre Name</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(rawHtml))
        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            requestedUrls += invocation.getArgument<List<String>>(0)
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>()
        }
        val response = flibustaTools.getRecommendedBooks(
            startPage = 0,
            endPage = 3,
            genreSlugs = listOf("sf"),
            limit = 2,
        )
        assertEquals(emptyList<String>(), response.errors)
        assertEquals(2, response.payload!!.bookRecommendations!!.size)
        assertEquals(12, response.payload!!.bookRecommendations!!.first().genres.single().id)
        assertEquals("genre-slug", response.payload!!.bookRecommendations!!.first().genres.single().slug)
        assertEquals(true, requestedUrls.all { "srcgenre=sf" in it })
    }

    @Test
    fun recommendationsByBook_returnsError_whenLimitExceedsMaximum() = runBlocking {
        val response = flibustaTools.getRecommendedBooks(limit = MAX_RECOMMENDED_BOOKS_LIMIT + 1)

        assertEquals(
            listOf("Error: Limit must be between 1 and $MAX_RECOMMENDED_BOOKS_LIMIT"),
            response.errors,
        )
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
