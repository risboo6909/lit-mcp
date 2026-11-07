package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.OpdsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksPeriod
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.resolveDownloadUrl
import com.github.risboo6909.mcp.flibusta.extractors.resolveMirrorUrl
import com.github.risboo6909.utils.HttpClient
import com.github.risboo6909.utils.MirrorManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.expectSuccess
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import io.ktor.client.HttpClient as KtorHttpClient

class MirrorLinksTest {
    @Test
    fun absoluteCatalogDownloadLinksUseFullPageMirrorEvenWhenCatalogMirrorIsNotConfigured() {
        for (scheme in listOf("http", "https")) {
            assertEquals(
                "https://flibusta.site/b/397210/epub",
                resolveDownloadUrl(
                    "$scheme://flisland.net/b/397210/epub",
                    "https://flibusta.site/b/397210",
                    setOf("https://flibusta.site"),
                ),
            )
        }
    }

    @Test
    fun catalogDownloadTargetsFollowConfiguredFullMirrorOrder() {
        val origins = linkedSetOf("https://flisland.net", "https://books.example", "https://flibusta.is")
        assertEquals(
            "https://books.example/b/397210/fb2?download=1#book",
            resolveDownloadUrl(
                "https://flibusta.is/b/397210/fb2?download=1#book",
                "https://flisland.net/b/397210",
                origins,
            ),
        )
        assertEquals(
            "https://static.example/book.epub",
            resolveDownloadUrl("https://static.example/book.epub", "https://flisland.net/b/397210", origins),
        )
    }

    @Test
    fun catalogOnlyConfigurationFallsBackToCanonicalDownloadOrigin() {
        assertEquals(
            "https://flibusta.is/b/397210/epub",
            resolveDownloadUrl(
                "https://flisland.net/b/397210/epub",
                "https://flisland.net/b/397210",
                setOf("https://flisland.net"),
            ),
        )
    }

    @Test
    fun catalogBookDetailsKeepDiscussionsAndUseFullMirrorForFiles() = runBlocking {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.toString()
            when {
                request.url.host != "flisland.net" -> respond("down", HttpStatusCode.ServiceUnavailable)
                request.url.encodedPath == "/g" -> respond(
                    """<h1 class="title">Список жанров</h1><ul><li><a href="/g/sf">Sci-fi</a></li></ul>""",
                    HttpStatusCode.OK,
                )
                else -> respond(
                    """<h1 class="title">Book</h1><a href="/a/5803">Author</a><div class="g-book">
                        <a href="/b/397210/epub">epub</a><a href="/b/397210/fb2">fb2</a>
                        <img alt="Cover image" src="/cover.jpg"/>
                        <span class="container_1">Reader review</span></div>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        try {
            val book = BookInfoExtractor(client, GenresListExtractor(client))
                .getBookInfoByIds(listOf(397210), true, 5).payload!!.single()
            assertEquals("https://flisland.net/b/397210", book.url)
            assertEquals("https://flisland.net/cover.jpg", book.coverUrl)
            assertEquals(listOf("Reader review"), book.discussions)
            assertEquals(
                listOf("https://flibusta.is/b/397210/fb2", "https://flibusta.is/b/397210/epub"),
                book.downloads!!.map { it.url },
            )
            assertEquals(true, requests.all { java.net.URI(it).path in listOf("/b/397210", "/g") })
        } finally {
            client.close()
        }
    }

    @Test
    fun catalogOnlyMirrorKeepsSearchMetadataButUsesFullMirrorForAcquisitions() = runBlocking {
        val requests = mutableListOf<String>()
        val engine = MockEngine { request ->
            requests += request.url.toString()
            if (request.url.host != "flisland.net") {
                respond("down", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(
                    """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Book</title>
                        <link href="/b/397210" rel="alternate"/>
                        <author><name>Author</name><uri>/a/5803</uri></author>
                        <link href="/b/397210/epub/opds?x=1" rel="http://opds-spec.org/acquisition/open-access"/>
                        <link href="/b/397210/fb2/opds" rel="http://opds-spec.org/acquisition/open-access"/>
                        </entry></feed>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        try {
            val book = OpdsExtractor(client).searchBooksByName("Book", 1, false).payload!!.single()
            assertEquals("https://flisland.net/b/397210", book.url)
            assertEquals("https://flisland.net/a/5803", book.authors!!.single().url)
            assertEquals(
                listOf("https://flibusta.is/b/397210/epub/opds?x=1", "https://flibusta.is/b/397210/fb2/opds"),
                book.downloads!!.map { it.url },
            )
            assertEquals(3, requests.size, "Download availability must not be probed during catalog search")
        } finally {
            client.close()
        }
    }

    private fun httpClient(engine: MockEngine) = HttpClient(
        KtorHttpClient(engine) { expectSuccess = true },
        MirrorManager(
            listOf("https://flibusta.is", "https://flibusta.site", "https://flisland.net"),
            setOf("https://flibusta.is"),
        ),
    )

    @Test
    fun popularAndRecommendedListsUseTheirPageMirror() = runBlocking {
        val engine = MockEngine { request ->
            if (request.url.host == "flibusta.is") {
                respond("down", HttpStatusCode.ServiceUnavailable)
            } else {
                val html = when {
                    request.url.encodedPath.startsWith("/stat") ->
                        """<h1 class="title">Popular</h1><ul><li><a href="/a/1">Author</a>
                            <a href="/b/123">Book</a></li></ul>"""
                    request.url.encodedPath == "/g" ->
                        """<h1 class="title">Список жанров</h1><ul><li><a href="/g/sf">Sci-fi</a></li></ul>"""
                    request.url.parameters["view"] == "authors" ->
                        """<form name="formrecs"><table><tr><th>Header</th></tr>
                            <tr><td><a href="/a/1">Author</a></td><td>1</td><td>2</td><td>3</td></tr>
                            </table></form>"""
                    else ->
                        """<form name="formrecs"><table><tr><th>Header</th></tr><tr>
                            <td><a href="/a/1">Author</a></td><td><a href="/b/123">Book</a></td>
                            <td><a href="/g/7">Sci-fi</a></td><td>3</td></tr></table></form>"""
                }
                respond(html, HttpStatusCode.OK)
            }
        }
        val client = httpClient(engine)
        val genres = GenresListExtractor(client)
        val popular = PopularBooksExtractor(client).getPopularBooks(PopularBooksPeriod.ALL_TIME, 0, 1)
        assertEquals("https://flibusta.site/b/123", popular.payload!!.popularBooks!!.single().book!!.url)
        val recommendations = RecommendationsExtractor(client, genres)
        val books = recommendations.getRecommendedBooks(mapOf("view" to "books"), 0, 1)
        assertEquals("https://flibusta.site/b/123", books.payload!!.bookRecommendations!!.single().book.url)
        val authors = recommendations.getRecommendedAuthors(mapOf("view" to "authors"), 0, 1)
        assertEquals("https://flibusta.site/a/1", authors.payload!!.authorRecommendations!!.single().author.url)
        client.close()
    }

    @Test
    fun placeholderRejectsUntrustedSource() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveMirrorUrl("https://flibusta-mirror.invalid/b/1", "https://evil.example/page")
        }
    }

    @Test
    fun queryOnlyNextLinkKeepsSearchPath() = runBlocking {
        val urls = mutableListOf<String>()
        val engine = MockEngine { request ->
            urls += request.url.toString()
            if (request.url.parameters["page"] == "2") {
                respond(
                    """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Two</title>
                        <link href="/b/2" rel="alternate"/></entry></feed>""",
                    HttpStatusCode.OK,
                )
            } else {
                respond(
                    """<feed xmlns="http://www.w3.org/2005/Atom"><link rel="next" href="?page=2"/>
                        <entry><title>One</title><link href="/b/1" rel="alternate"/></entry></feed>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        assertEquals(2, OpdsExtractor(client).searchBooksByName("Book", 2, false).payload!!.size)
        assertEquals("https://flibusta.is/opds/search?page=2", urls[1])
        client.close()
    }

    @Test
    fun absoluteLinksToOtherFlibustaMirrorsUsePageSource() {
        assertEquals(
            "https://flibusta.site/b/123/epub?x=1",
            resolveMirrorUrl("https://flibusta.is/b/123/epub?x=1", "https://flibusta.site/b/123"),
        )
        assertEquals(
            "https://example.test/path",
            resolveMirrorUrl("https://example.test/path", "https://flibusta.site/b/123"),
        )
    }

    @Test
    fun bookDetailsUseSourceMirrorWithoutChangingDiscussionText() = runBlocking {
        val engine = MockEngine { request ->
            if (request.url.host == "flibusta.is") {
                respond("down", HttpStatusCode.ServiceUnavailable)
            } else if (request.url.encodedPath == "/g") {
                respond(
                    """<h1 class="title">Список жанров</h1><ul><li><a href="/g/sf">Sci-fi</a></li></ul>""",
                    HttpStatusCode.OK,
                )
            } else {
                respond(
                    """<h1 class="title">Book</h1><a href="/a/42">Author</a><div class="g-book">
                    <a class="genre" href="/g/sf">Sci-fi</a><a href="/b/123/epub">epub</a>
                    <img alt="Cover image" src="/cover.jpg"/><h2>Аннотация</h2>
                    <p>https://flibusta-mirror.invalid/text</p>
                    <span class="container_1">https://flibusta-mirror.invalid/comment</span></div>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        val book = BookInfoExtractor(client, GenresListExtractor(client))
            .getBookInfoByIds(listOf(123), true, 5).payload!!.single()
        assertEquals("https://flibusta.site/b/123", book.url)
        assertEquals("https://flibusta.site/a/42", book.authors!!.single().url)
        assertEquals("https://flibusta.site/b/123/epub", book.downloads!!.single().url)
        assertEquals("https://flibusta.site/cover.jpg", book.coverUrl)
        assertEquals("https://flibusta-mirror.invalid/text", book.annotation)
        assertEquals("https://flibusta-mirror.invalid/comment", book.discussions!!.single())
        client.close()
    }

    @Test
    fun cachedGenreLinksFollowNewPreferredMirror() = runBlocking {
        var primaryDown = false
        val engine = MockEngine { request ->
            if (request.url.host == "flibusta.is" && primaryDown) {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(
                    """<h1 class="title">Список жанров</h1><ul><li><a href="/g/sf">Sci-fi</a></li></ul>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        val genres = GenresListExtractor(client)
        assertEquals("https://flibusta.is/g/sf", genres.getAllGenres().payload!!.single().url)
        primaryDown = true
        client.queryGet("https://flibusta.is/b/1", 5)
        assertEquals("https://flibusta.site/g/sf", genres.getAllGenres().payload!!.single().url)
        client.close()
    }

    @Test
    fun searchDoesNotFollowAnExternalNextLink() = runBlocking {
        val hosts = mutableListOf<String>()
        val engine = MockEngine { request ->
            hosts += request.url.host
            respond(
                """<feed xmlns="http://www.w3.org/2005/Atom"><link rel="next" href="https://example.test/steal"/>
                    <entry><title>Book</title><link href="/b/123" rel="alternate"/></entry></feed>""",
                HttpStatusCode.OK,
            )
        }
        val client = httpClient(engine)
        OpdsExtractor(client).searchBooksByName("Book", 2, false)
        assertEquals(listOf("flibusta.is"), hosts)
        client.close()
    }

    @Test
    fun searchReturnsLinksFromTheMirrorThatServedThePage() = runBlocking {
        val engine = MockEngine { request ->
            if (request.url.host == "flibusta.is") {
                respond("unavailable", HttpStatusCode.ServiceUnavailable)
            } else {
                respond(
                    """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Book</title>
                        <link href="/b/123" rel="alternate"/>
                        <link href="/b/123/epub" rel="http://opds-spec.org/acquisition/open-access"/>
                        </entry></feed>""",
                    HttpStatusCode.OK,
                )
            }
        }
        val client = httpClient(engine)
        val book = OpdsExtractor(client).searchBooksByName("Book", 1, false).payload!!.single()
        assertEquals("https://flibusta.site/b/123", book.url)
        assertEquals("https://flibusta.site/b/123/epub", book.downloads!!.single().url)
        client.close()
    }
}
