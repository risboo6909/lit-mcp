package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    fun searchBooksByName_usesOpdsAndPreservesResultOrder() = runBlocking {
        val opdsXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom" xmlns:dc="http://purl.org/dc/terms/">
              <entry>
                <title>Example Book: Alternate Edition</title>
                <author><name>Example Author</name><uri>/a/201</uri></author>
                <category term="Science Fiction" label="Science Fiction" />
                <category term="Adventure" label="Adventure" />
                <dc:language>en</dc:language>
                <dc:format>fb2+zip</dc:format>
                <dc:issued>2024</dc:issued>
                <content type="text/html">&lt;p&gt;A useful description.&lt;/p&gt;&lt;br/&gt;Downloads: 12 345&lt;br/&gt;Format: fb2</content>
                <link href="/covers/101.jpg" rel="http://opds-spec.org/image" type="image/jpeg" />
                <link href="/b/101/fb2" rel="http://opds-spec.org/acquisition/open-access"
                      type="application/fb2+zip" />
                <link href="/b/101/epub" rel="http://opds-spec.org/acquisition/open-access"
                      type="application/epub+zip" />
                <link href="/b/101" rel="alternate" type="text/html" />
                <id>tag:book:not-a-flibusta-id</id>
              </entry>
              <entry>
                <title>Example Book</title>
                <author><name>Example Author</name><uri>/a/201</uri></author>
                <link href="/b/102" rel="alternate" type="text/html" />
                <id>tag:book:also-not-a-flibusta-id</id>
              </entry>
            </feed>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(opdsXml))

        val response = flibustaTools.searchBooksByName("Example Book", includeDescription = true)

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(listOf(101, 102), response.payload!!.map { it.id })
        assertEquals(listOf("Example Book: Alternate Edition", "Example Book"), response.payload!!.map { it.title })
        assertEquals("Example Author", response.payload!![0].authors!!.single().name)
        assertEquals(201, response.payload!![0].authors!!.single().id)
        assertEquals("https://flibusta.is/a/201", response.payload!![0].authors!!.single().url)
        assertEquals("https://flibusta.is/b/102", response.payload!![1].url)
        assertEquals(listOf("Science Fiction", "Adventure"), response.payload!![0].genres)
        assertEquals("A useful description.", response.payload!![0].description)
        assertEquals("en", response.payload!![0].language)
        assertEquals(2024, response.payload!![0].publishYear)
        assertEquals("https://flibusta.is/covers/101.jpg", response.payload!![0].coverUrl)
        assertEquals(listOf("fb2", "epub"), response.payload!![0].downloads!!.map { it.format })
        assertEquals(
            listOf("https://flibusta.is/b/101/fb2", "https://flibusta.is/b/101/epub"),
            response.payload!![0].downloads!!.map { it.url },
        )
        assertEquals(12345, response.payload!![0].downloadsCount)

        val url = argumentCaptor<String>()
        verify(httpHelper, times(1)).queryGet(url.capture(), any<Int>())
        assertEquals(true, url.firstValue.startsWith("https://flibusta.is/opds/search?searchType=books&"))
        assertEquals(true, "searchTerm=Example+Book" in url.firstValue)
    }

    @Test
    fun searchBooksByName_returnsErrorWhenNameIsBlank() = runBlocking {
        val response = flibustaTools.searchBooksByName("  ")

        assertEquals(listOf("Error: Book name must not be blank"), response.errors)
        verify(httpHelper, times(0)).queryGet(any<String>(), any<Int>())
    }

    @Test
    fun searchBooksByName_followsNextLinksUntilLimit() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenAnswer { invocation ->
            val url = invocation.getArgument<String>(0)
            requestedUrls += url
            when (requestedUrls.size) {
                1 -> opdsBooksFeed(1..20, nextHref = "/opds/search/books/page-2")
                else -> opdsBooksFeed(21..40, nextHref = "/opds/search/books/page-3")
            }
        }

        val response = flibustaTools.searchBooksByName("Example Book", limit = 25)

        assertEquals(emptyList<String>(), response.errors)
        assertEquals((1..25).toList(), response.payload!!.map { it.id })
        assertEquals(
            listOf(
                "https://flibusta.is/opds/search?searchType=books&searchTerm=Example+Book",
                "https://flibusta.is/opds/search/books/page-2",
            ),
            requestedUrls,
        )
    }

    @Test
    fun searchBooksByName_returnsErrorWhenLimitExceedsMaximum() = runBlocking {
        val response = flibustaTools.searchBooksByName("Example Book", limit = MAX_OPDS_BOOKS_LIMIT + 1)

        assertEquals(
            listOf("Error: Limit must be between 1 and $MAX_OPDS_BOOKS_LIMIT"),
            response.errors,
        )
        verify(httpHelper, times(0)).queryGet(any<String>(), any<Int>())
    }

    @Test
    fun getNewBooks_fetchesOnlyPagesNeededForLimitAndPreservesOrder() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            val urls = invocation.getArgument<List<String>>(0)
            requestedUrls += urls
            listOf(
                opdsBooksFeed(1..20),
                opdsBooksFeed(21..40),
            ) to emptyList<String>()
        }

        val response = flibustaTools.getNewBooks(limit = 25)

        assertEquals(emptyList<String>(), response.errors)
        assertEquals((1..25).toList(), response.payload!!.map { it.id })
        assertEquals(List(25) { null }, response.payload!!.map { it.description })
        assertEquals(
            listOf(
                "https://flibusta.is/opds/new/0/new",
                "https://flibusta.is/opds/new/1/new/",
            ),
            requestedUrls,
        )
    }

    @Test
    fun getNewBooks_returnsErrorWhenLimitExceedsMaximum() = runBlocking {
        val response = flibustaTools.getNewBooks(limit = MAX_OPDS_BOOKS_LIMIT + 1)

        assertEquals(
            listOf("Error: Limit must be between 1 and $MAX_OPDS_BOOKS_LIMIT"),
            response.errors,
        )
        verify(httpHelper, times(0)).fetchMultiplePages(any<List<String>>(), any<Int>())
    }

    @Test
    fun searchAuthorsByName_parsesOpdsAuthorEntries() = runBlocking {
        val opdsXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <entry>
                <id>tag:author:201</id>
                <title>Example Author</title>
                <content type="text">42 books</content>
                <link href="/opds/author/201" type="application/atom+xml" />
                <link href="/images/author.jpg" rel="http://opds-spec.org/image" type="image/jpeg" />
              </entry>
            </feed>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(opdsXml))

        val response = flibustaTools.searchAuthorsByName("Example Author")

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(201, response.payload!!.single().id)
        assertEquals("Example Author", response.payload!!.single().name)
        assertEquals("https://flibusta.is/a/201", response.payload!!.single().url)
        assertEquals(42, response.payload!!.single().booksCount)
        assertEquals("https://flibusta.is/images/author.jpg", response.payload!!.single().imageUrl)

        val url = argumentCaptor<String>()
        verify(httpHelper, times(1)).queryGet(url.capture(), any<Int>())
        assertEquals(true, "searchType=authors" in url.firstValue)
        assertEquals(true, "searchTerm=Example+Author" in url.firstValue)
    }

    @Test
    fun searchAuthorsByName_returnsErrorWhenNameIsBlank() = runBlocking {
        val response = flibustaTools.searchAuthorsByName("  ")

        assertEquals(listOf("Error: Author name must not be blank"), response.errors)
        verify(httpHelper, times(0)).queryGet(any<String>(), any<Int>())
    }

    @Test
    fun getBooksByAuthorId_fetchesAlphabeticalPagesNeededForLimit() = runBlocking {
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            val urls = invocation.getArgument<List<String>>(0)
            requestedUrls += urls
            listOf(
                opdsBooksFeed(1..20),
                opdsBooksFeed(21..40),
            ) to emptyList<String>()
        }

        val response = flibustaTools.getBooksByAuthorId(authorId = 201, limit = 21)

        assertEquals(emptyList<String>(), response.errors)
        assertEquals((1..21).toList(), response.payload!!.map { it.id })
        assertEquals(
            listOf(
                "https://flibusta.is/opds/author/201/alphabet",
                "https://flibusta.is/opds/author/201/alphabet/1",
            ),
            requestedUrls,
        )
    }

    @Test
    fun getBooksByAuthorId_returnsErrorWhenAuthorIdIsInvalid() = runBlocking {
        val response = flibustaTools.getBooksByAuthorId(authorId = 0)

        assertEquals(listOf("Error: Author ID must be greater than 0"), response.errors)
        verify(httpHelper, times(0)).fetchMultiplePages(any<List<String>>(), any<Int>())
    }

    @Test
    fun popularBooks_keepsFastPathWithoutGenreOptions() = runBlocking {
        val popularBooksHtml = """
            <html><body>
            <h1 class='title'>Популярные книги</h1>
            <ul><li><a href='/a/1'>Author</a> - <a href='/b/101'>Book</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>()))
            .thenReturn(listOf(popularBooksHtml) to emptyList())

        val response = flibustaTools.getPopularBooksList()

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(101, response.payload!!.popularBooks!!.single().book!!.id)
        assertEquals(1, response.payload!!.totalPages)
        verify(httpHelper, times(1)).fetchMultiplePages(any<List<String>>(), any<Int>())
        verify(httpHelper, times(0)).queryGet(any<String>(), any<Int>())
    }

    @Test
    fun popularBooks_defaultsEndPageToPageAfterStartPage() = runBlocking {
        val popularBooksHtml = """
            <html><body>
            <h1 class='title'>Popular books</h1>
            <ul><li><a href='/a/1'>Author</a> - <a href='/b/101'>Book</a></li></ul>
            </body></html>
        """.trimIndent()
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            requestedUrls += invocation.getArgument<List<String>>(0)
            listOf(popularBooksHtml) to emptyList<String>()
        }

        val response = flibustaTools.getPopularBooksList(startPage = 3)

        assertEquals(emptyList<String>(), response.errors)
        assertEquals(1, requestedUrls.size)
        assertEquals(true, "page=3" in requestedUrls.single())
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
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { _ ->
            listOf(rawHtml, rawHtml, rawHtml) to emptyList<String>()
        }
        val response = flibustaTools.getRecommendedAuthors(0, 3)
        assertEquals(emptyList<String>(), response.errors)
        assertEquals(5, response.payload!!.totalPages)
        verify(httpHelper, times(0)).queryGet(any<String>(), any<Int>())
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
        fun recommendationHtml(books: List<Pair<Int, String>>): String {
            val rows = books.joinToString("\n") { (bookId, title) ->
                """
                <tr>
                  <td><a href='/a/123'>Author Name</a></td>
                  <td><a href='/b/$bookId'>$title</a></td>
                  <td><a href='/g/12'>Genre Name</a></td>
                  <td>12</td>
                </tr>
                """.trimIndent()
            }
            return """
            <html><body>
            <form name='formrecs'>
              <table>
                <tr><th>Author</th><th>Book</th><th>Genre</th><th>Recs</th></tr>
                $rows
              </table>
            </form>
            <ul class='pager'><li class='pager-last'><a href='/rec?view=books&page=5'>5</a></li></ul>
            </body></html>
            """.trimIndent()
        }
        val firstPageHtml = recommendationHtml(
            listOf(
                456 to "First Book",
                789 to "Second Book",
                999 to "Third Book",
            ),
        )
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul>
              <li><a href='/g/sf'>Genre Name</a></li>
              <li><a href='/g/detective'>Detective</a></li>
            </ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(any<String>(), any<Int>())).thenReturn(Result.success(firstPageHtml))
        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))
        val requestedUrls = mutableListOf<String>()
        whenever(httpHelper.fetchMultiplePages(any<List<String>>(), any<Int>())).thenAnswer { invocation ->
            val urls = invocation.getArgument<List<String>>(0)
            requestedUrls += urls
            urls.map { firstPageHtml } to emptyList<String>()
        }
        val response = flibustaTools.getRecommendedBooks(
            startPage = 0,
            endPage = 3,
            authorName = "Example Author",
            genreSlugs = listOf("sf", "detective"),
            limit = 2,
        )
        assertEquals(emptyList<String>(), response.errors)
        assertEquals(listOf(456, 789), response.payload!!.bookRecommendations!!.map { it.book.id })
        assertEquals(12, response.payload!!.bookRecommendations!!.first().genres.single().id)
        assertEquals("sf", response.payload!!.bookRecommendations!!.first().genres.single().slug)
        assertEquals(1, requestedUrls.size)
        assertEquals(true, "page=0" in requestedUrls.single())
        assertEquals(true, "author=Example+Author" in requestedUrls.single())
        assertEquals(true, "srcgenre=sf%2Cdetective" in requestedUrls.single())
    }

    @Test
    fun recommendationsByBook_returnsErrorForUnknownGenreSlug() = runBlocking {
        val genresHtml = """
            <html><body>
            <h1 class='title'>Список жанров</h1>
            <ul><li><a href='/g/sf'>Научная фантастика</a></li></ul>
            </body></html>
        """.trimIndent()
        whenever(httpHelper.queryGet(eq("https://flibusta.is/g"), any<Int>()))
            .thenReturn(Result.success(genresHtml))

        val response = flibustaTools.getRecommendedBooks(genreSlugs = listOf("not-a-genre"))

        assertEquals(
            listOf(
                "Error: Unknown genre slugs: not-a-genre. " +
                    "Use flibustaGetGenresList to discover valid genre slugs.",
            ),
            response.errors,
        )
        verify(httpHelper, times(0)).fetchMultiplePages(any<List<String>>(), any<Int>())
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

    private fun opdsBooksFeed(bookIds: Iterable<Int>, nextHref: String? = null): String {
        val entries = bookIds.joinToString("\n") { bookId ->
            """
            <entry>
              <title>Example Book $bookId</title>
              <author><name>Example Author</name><uri>/a/201</uri></author>
              <content type="text/html">&lt;p&gt;Description $bookId&lt;/p&gt;</content>
              <link href="/b/$bookId/fb2" rel="http://opds-spec.org/acquisition/open-access"
                    type="application/fb2+zip" />
              <link href="/b/$bookId" rel="alternate" type="text/html" />
              <id>tag:book:example-$bookId</id>
            </entry>
            """.trimIndent()
        }
        return """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              ${nextHref?.let { "<link href=\"$it\" rel=\"next\" type=\"application/atom+xml\" />" }.orEmpty()}
              $entries
            </feed>
        """.trimIndent()
    }
}
