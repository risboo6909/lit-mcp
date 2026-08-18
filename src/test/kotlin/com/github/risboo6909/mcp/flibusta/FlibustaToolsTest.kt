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
        assertEquals(null, response.payload!!.popularBooks!!.single().genres)
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
    fun topBooksByGenre_returnsErrorForUnknownGenreSlug() = runBlocking {
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul><li><a href='/g/sf'>Научная фантастика</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(genresHtml))

        val response = flibustaTools.getTopBooksByGenre(
            genreSlugs = listOf("not-a-genre"),
        )

        assertEquals(listOf("Unknown genre slugs: not-a-genre"), response.errors)
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
    fun topBooksByGenre_scansPagesUntilRequestedLimitIsReached() = runBlocking {
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul>
              <li><a href='/g/sf'>Научная фантастика</a></li>
              <li><a href='/g/sf_fantasy'>Фэнтези</a></li>
            </ul>
            </body></html>
        """.trimIndent()
        val pagerHtml = """
            <html><body>
            <ul class='pager'><li class='pager-last'><a href='/stat/b?page=3'>3</a></li></ul>
            </body></html>
        """.trimIndent()
        val popularBooksPages = mapOf(
            0 to """
                <html><body>
                <h1 class='title'>Популярные книги</h1>
                <ul>
                  <li><a href='/a/1'>Fantasy Author</a> - <a href='/b/101'>Fantasy Book</a></li>
                  <li><a href='/a/2'>SF Author One</a> - <a href='/b/102'>SF Book One</a></li>
                </ul>
                </body></html>
            """.trimIndent(),
            1 to """
                <html><body>
                <h1 class='title'>Популярные книги</h1>
                <ul>
                  <li><a href='/a/3'>SF Author Two</a> - <a href='/b/103'>SF Book Two</a></li>
                  <li><a href='/a/4'>SF Author Three</a> - <a href='/b/104'>SF Book Three</a></li>
                </ul>
                </body></html>
            """.trimIndent(),
        )
        val bookGenres = mapOf(
            101 to "Фэнтези",
            102 to "Научная фантастика",
            103 to "Научная фантастика",
            104 to "Научная фантастика",
        )
        val scannedPages = mutableListOf<Int>()

        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))
        whenever(httpHelper.queryGet(eq("https://flibusta.is/stat/b"), any<Int>()))
            .thenReturn(Result.success(pagerHtml))
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            val urls = invocation.getArgument<List<String>>(0)
            if (urls.first().contains("/stat/")) {
                val page = urls.single().substringAfter("page=").toInt()
                scannedPages += page
                listOf(popularBooksPages.getValue(page)) to emptyList<String>()
            } else {
                urls.map { url ->
                    val bookId = url.substringAfterLast('/').toInt()
                    """
                        <html><body>
                        <a class='genre' href='/g/$bookId'>${bookGenres.getValue(bookId)}</a>
                        </body></html>
                    """.trimIndent()
                } to emptyList<String>()
            }
        }

        val response = flibustaTools.getTopBooksByGenre(
            genreSlugs = listOf("sf"),
            limit = 3,
        )

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(listOf(0, 1), scannedPages)
        assertEquals(3, response.payload!!.requestedLimit)
        assertEquals(DEFAULT_TOP_BOOKS_SCAN_PAGES, response.payload!!.maxScanPages)
        assertEquals(2, response.payload!!.scannedPages)
        assertEquals(true, response.payload!!.limitReached)
        assertEquals(false, response.payload!!.scanLimitReached)
        assertEquals(listOf(102, 103, 104), response.payload!!.popularBooks.map { it.book!!.id })
        verify(httpHelper, times(1)).queryGet(eq("https://flibusta.is/stat/b"), any<Int>())
    }

    @Test
    fun topBooksByGenre_returnsPartialResult_whenScanLimitIsReached() = runBlocking {
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul>
              <li><a href='/g/sf'>Научная фантастика</a></li>
              <li><a href='/g/sf_fantasy'>Фэнтези</a></li>
            </ul>
            </body></html>
        """.trimIndent()
        val pagerHtml = """
            <html><body>
            <ul class='pager'><li class='pager-last'><a href='/stat/b?page=3'>3</a></li></ul>
            </body></html>
        """.trimIndent()
        val popularBooksHtml = """
            <html><body>
            <h1 class='title'>Популярные книги</h1>
            <ul><li><a href='/a/1'>Fantasy Author</a> - <a href='/b/101'>Fantasy Book</a></li></ul>
            </body></html>
        """.trimIndent()
        val fantasyBookHtml = """
            <html><body>
            <a class='genre' href='/g/101'>Фэнтези</a>
            </body></html>
        """.trimIndent()

        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))
        whenever(httpHelper.queryGet(eq("https://flibusta.is/stat/b"), any<Int>()))
            .thenReturn(Result.success(pagerHtml))
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            val urls = invocation.getArgument<List<String>>(0)
            if (urls.first().contains("/stat/")) {
                listOf(popularBooksHtml) to emptyList<String>()
            } else {
                listOf(fantasyBookHtml) to emptyList<String>()
            }
        }

        val response = flibustaTools.getTopBooksByGenre(
            genreSlugs = listOf("sf"),
            limit = 1,
            maxScanPages = 1,
        )

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(emptyList<Int>(), response.payload!!.popularBooks.mapNotNull { it.book?.id })
        assertEquals(1, response.payload!!.maxScanPages)
        assertEquals(1, response.payload!!.scannedPages)
        assertEquals(false, response.payload!!.limitReached)
        assertEquals(true, response.payload!!.scanLimitReached)
    }

    @Test
    fun topBooksByGenre_returnsError_whenLimitExceedsMaximum() = runBlocking {
        val response = flibustaTools.getTopBooksByGenre(
            genreSlugs = listOf("sf"),
            limit = MAX_TOP_BOOKS_LIMIT + 1,
        )

        assertEquals(
            listOf("Error: Limit must be between 1 and $MAX_TOP_BOOKS_LIMIT"),
            response.errors,
        )
    }

    @Test
    fun topBooksByGenre_returnsError_whenMaxScanPagesExceedsMaximum() = runBlocking {
        val response = flibustaTools.getTopBooksByGenre(
            genreSlugs = listOf("sf"),
            maxScanPages = MAX_TOP_BOOKS_SCAN_PAGES + 1,
        )

        assertEquals(
            listOf("Error: Max scan pages must be between 1 and $MAX_TOP_BOOKS_SCAN_PAGES"),
            response.errors,
        )
    }

    @Test
    fun topBooksByGenre_returnsError_whenGenresAreEmpty() = runBlocking {
        val response = flibustaTools.getTopBooksByGenre(genreSlugs = emptyList())

        assertEquals(listOf("Error: At least one genre slug is required"), response.errors)
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
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { _ ->
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>()
        }
        val response = flibustaTools.getRecommendedBooks(0, 3)
        assertEquals(emptyList<String>(), response.errors)
        assertEquals(12, response.payload!!.bookRecommendations!!.first().genres.single().id)
        assertEquals("genre-slug", response.payload!!.bookRecommendations!!.first().genres.single().slug)
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
