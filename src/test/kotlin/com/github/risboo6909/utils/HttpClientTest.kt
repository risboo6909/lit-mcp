package com.github.risboo6909.utils

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.expectSuccess
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import io.ktor.client.HttpClient as KtorHttpClient

class HttpClientTest {
    private val defaultMirrors = listOf("https://flibusta.is", "https://flibusta.site", "https://flisland.net")

    private fun testClient(
        ktor: KtorHttpClient,
        mirrors: List<String> = defaultMirrors,
        clock: () -> Long = System::nanoTime,
    ) = HttpClient(ktor, MirrorManager(mirrors, setOf("https://flibusta.is"), clock))

    @Test
    fun mirrorMethodsHaveGenericDefaultsAndCanBeConfigured() {
        val basic = object : HttpClientInterface {
            override suspend fun queryGet(url: String, retries: Int): Result<String> = Result.success("")
            override suspend fun fetchMultiplePages(
                urls: List<String>,
                retries: Int,
            ): Pair<List<String>, List<String>> = emptyList<String>() to emptyList()
        }
        assertEquals("", basic.preferredMirrorOrigin())
        assertEquals(emptySet<String>(), basic.allowedMirrorOrigins())
        assertTrue(!basic.isAllowedMirrorUrl("https://mirror.test/b/1"))
        assertTrue(!basic.isAllowedMirrorUrl("https://evil.test/b/1"))

        val custom = object : HttpClientInterface {
            override suspend fun queryGet(url: String, retries: Int): Result<String> = Result.success("")
            override suspend fun fetchMultiplePages(
                urls: List<String>,
                retries: Int,
            ): Pair<List<String>, List<String>> = emptyList<String>() to emptyList()
            override fun preferredMirrorOrigin(): String = "https://mirror.test"
            override fun allowedMirrorOrigins(): Set<String> = setOf("https://mirror.test")
        }
        assertEquals("https://mirror.test", custom.preferredMirrorOrigin())
        assertEquals(setOf("https://mirror.test"), custom.allowedMirrorOrigins())
        assertTrue(custom.isAllowedMirrorUrl("https://mirror.test/b/1"))
        assertTrue(!custom.isAllowedMirrorUrl("https://evil.test/b/1"))
        assertEquals(setOf("https://source.test"), FetchedPage("", "https://source.test/page").mirrorOrigins)
    }

    @Test
    fun legacyBatchPadsMissingResultsToPreserveAlignment() = runBlocking {
        val client = object : HttpClientInterface {
            override suspend fun queryGet(url: String, retries: Int): Result<String> = Result.success("")
            override suspend fun fetchMultiplePages(
                urls: List<String>,
                retries: Int,
            ): Pair<List<String>, List<String>> = listOf("first") to listOf("missing second")
        }
        val result = client.fetchPages(listOf("https://flibusta.is/b/1", "https://flibusta.is/b/2"))
        assertEquals(2, result.pages.size)
        assertEquals(null, result.pages[1])
    }

    @Test
    fun changingBackupsDoesNotPostponePrimaryProbe() = runBlocking {
        val hosts = mutableListOf<String>()
        val time = AtomicLong(0)
        val slowStarted = CompletableDeferred<Unit>()
        val finishSlow = CompletableDeferred<Unit>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is" ||
                    (request.url.host == "flibusta.site" && request.url.encodedPath == "/b/fast")
                ) {
                    respond("down", HttpStatusCode.ServiceUnavailable)
                } else {
                    if (request.url.encodedPath == "/b/slow") {
                        slowStarted.complete(Unit)
                        finishSlow.await()
                    }
                    respond("ok", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor, clock = { time.get() })
        client.queryGet("https://flibusta.is/g", 5)
        time.set(1_000_000_000L)
        val slow = async { client.queryGet("https://flibusta.is/b/slow", 5) }
        slowStarted.await()
        client.queryGet("https://flibusta.is/b/fast", 5)
        time.set(200_000_000_000L)
        finishSlow.complete(Unit)
        slow.await()
        time.set(302_000_000_000L)
        client.queryGet("https://flibusta.is/g", 5)
        assertEquals(listOf("flibusta.is", "flibusta.site"), hosts.takeLast(2))
        client.close()
    }

    @Test
    fun redirectedMirrorUsesConfiguredRetryBudget() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                when (request.url.host) {
                    "flibusta.is" -> respond(
                        "",
                        HttpStatusCode.Found,
                        headersOf(HttpHeaders.Location, "https://flibusta.site/g"),
                    )
                    "flibusta.site" -> respond("down", HttpStatusCode.ServiceUnavailable)
                    else -> respond("ok", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        assertTrue(client.queryGet("https://flibusta.is/g", 5).isSuccess)
        assertEquals(listOf("flibusta.is", "flibusta.site", "flibusta.site", "flisland.net"), hosts)
        client.close()
    }

    @Test
    fun invalidMirrorConfigurationFailsAtStartup() {
        for (mirrors in listOf(
            "",
            "http://primary.test",
            "https://user@primary.test",
            "https://primary.test/path",
            "https://primary.test,https://primary.test",
        )) {
            assertThrows(Exception::class.java) { MirrorManager(mirrors.split(",").map { it.trim() }) }
        }
    }

    @Test
    fun cancellationDoesNotTryAnotherMirror() = runBlocking {
        val requests = AtomicInteger()
        val ktor = KtorHttpClient(
            MockEngine {
                requests.incrementAndGet()
                throw CancellationException("cancelled")
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        try {
            client.queryGet("https://flibusta.is/g", 5)
            throw AssertionError("Cancellation was not propagated")
        } catch (_: CancellationException) {
            assertEquals(1, requests.get())
        } finally {
            client.close()
        }
    }

    @Test
    fun failedPrimaryProbeIsNotRepeatedUntilNextCooldown() = runBlocking {
        val hosts = mutableListOf<String>()
        val time = AtomicLong(0)
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is") {
                    respond("down", HttpStatusCode.ServiceUnavailable)
                } else {
                    respond("ok", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor, clock = { time.get() })
        client.queryGet("https://flibusta.is/g", 5)
        time.set(301_000_000_000L)
        client.queryGet("https://flibusta.is/g", 5)
        client.queryGet("https://flibusta.is/g", 5)
        assertEquals(listOf("flibusta.is", "flibusta.site", "flibusta.is", "flibusta.site", "flibusta.site"), hosts)
        client.close()
    }

    @Test
    fun timedOutRequestFallsBackToNextMirror() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is") delay(300)
                respond("ok", HttpStatusCode.OK)
            },
        ) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 50 }
        }
        val client = testClient(ktor)
        val result = client.queryGet("https://flibusta.is/g", 5)
        assertTrue(result.isSuccess, result.exceptionOrNull()?.toString())
        assertEquals(listOf("flibusta.is", "flibusta.site"), hosts)
        client.close()
    }

    @Test
    fun remembersWorkingMirrorAndRetriesPrimaryAfterCooldown() = runBlocking {
        val hosts = mutableListOf<String>()
        val time = AtomicLong(0)
        var primaryDown = true
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is" && primaryDown) {
                    respond("busy", HttpStatusCode.TooManyRequests)
                } else {
                    respond("ok", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor, clock = { time.get() })
        assertTrue(client.queryGet("https://flibusta.is/g", 5).isSuccess)
        assertTrue(client.queryGet("https://flibusta.is/g", 5).isSuccess)
        primaryDown = false
        time.set(301_000_000_000L)
        assertTrue(client.queryGet("https://flibusta.is/g", 5).isSuccess)
        assertEquals(listOf("flibusta.is", "flibusta.site", "flibusta.site", "flibusta.is"), hosts)
        client.close()
    }

    @Test
    fun pageBatchPreservesOriginAndFailedPosition() = runBlocking {
        val ktor = KtorHttpClient(
            MockEngine { request ->
                when (request.url.encodedPath) {
                    "/b/1" -> if (request.url.host == "flibusta.is") {
                        respond("down", HttpStatusCode.ServiceUnavailable)
                    } else {
                        respond("one", HttpStatusCode.OK)
                    }
                    "/b/2" -> respond("missing", HttpStatusCode.NotFound)
                    else -> respond("three", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        val batch = client.fetchPages(
            listOf("https://flibusta.is/b/1", "https://flibusta.is/b/2", "https://flibusta.is/b/3"),
            3,
        )
        assertEquals("one", batch.pages[0]?.body)
        assertTrue(batch.pages[0]!!.sourceUrl.startsWith("https://flibusta.site/"))
        assertEquals(null, batch.pages[1])
        assertEquals("three", batch.pages[2]?.body)
        assertEquals(1, batch.errors.size)
        client.close()
    }

    @Test
    fun doesNotFollowRedirectToUntrustedOrigin() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is") {
                    respond("redirect", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://example.test/"))
                } else {
                    respond("wrong", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        assertTrue(client.queryGet("https://flibusta.is/g", 1).isFailure)
        assertTrue("example.test" !in hosts)
        client.close()
    }

    @Test
    fun customMirrorListReplacesCanonicalOrigin() = runBlocking {
        val urls = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                urls += request.url.toString()
                respond("ok", HttpStatusCode.OK)
            },
        ) { expectSuccess = true }
        val client = testClient(ktor, listOf("https://flibusta.site", "https://flisland.net"))
        val result = client.queryGet("https://flibusta.is/opds/search?searchTerm=A%2BB%20C", 1)
        assertTrue(result.isSuccess)
        assertEquals(listOf("https://flibusta.site/opds/search?searchTerm=A%2BB%20C"), urls)
        client.close()
    }

    @Test
    fun notFoundDoesNotTryAnotherMirror() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                respond("missing", HttpStatusCode.NotFound)
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        assertTrue(client.queryGet("https://flibusta.is/b/1", 5).isFailure)
        assertEquals(listOf("flibusta.is"), hosts)
        client.close()
    }

    @Test
    fun defaultRetryCountAppliesWhenOnlyOneMirrorIsConfigured() = runBlocking {
        val requests = AtomicInteger()
        val ktor = KtorHttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond("down", HttpStatusCode.ServiceUnavailable)
            },
        ) { expectSuccess = true }
        val client = testClient(ktor, listOf("https://flibusta.site"))

        assertTrue(client.queryGet("https://flibusta.is/g").isFailure)
        assertEquals(3, requests.get())
        client.close()
    }

    @Test
    fun defaultRetryCountAppliesToNonMirrorUrls() = runBlocking {
        val requests = AtomicInteger()
        val ktor = KtorHttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond("down", HttpStatusCode.ServiceUnavailable)
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)

        assertTrue(client.queryGet("https://example.test/g").isFailure)
        assertEquals(3, requests.get())
        client.close()
    }

    @Test
    fun mirrorRequestsRespectExplicitRetryCount() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktor = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                respond("down", HttpStatusCode.ServiceUnavailable)
            },
        ) { expectSuccess = true }
        val client = testClient(ktor)
        val result = client.queryGet("https://flibusta.is/g", 5)
        assertTrue(result.isFailure)
        assertEquals(listOf("flibusta.is", "flibusta.site", "flisland.net", "flibusta.is", "flibusta.site"), hosts)
        assertTrue(hosts.all { result.exceptionOrNull()!!.message!!.contains(it) })
        client.close()
    }

    @Test
    fun queryGet_usesNextMirrorWhenPrimaryReturns503() = runBlocking {
        val hosts = mutableListOf<String>()
        val ktorClient = KtorHttpClient(
            MockEngine { request ->
                hosts += request.url.host
                if (request.url.host == "flibusta.is") {
                    respond("unavailable", HttpStatusCode.ServiceUnavailable)
                } else {
                    respond("ok", HttpStatusCode.OK)
                }
            },
        ) { expectSuccess = true }
        val client = testClient(ktorClient)
        val result = client.queryGet("https://flibusta.is/opds/search?searchTerm=A%2BB", retries = 3)
        assertTrue(result.isSuccess)
        assertEquals(listOf("flibusta.is", "flibusta.site"), hosts)
        client.close()
    }

    @Test
    fun queryGet_retriesNonSuccessResponses() = runBlocking {
        val requests = AtomicInteger()
        val ktorClient = KtorHttpClient(
            MockEngine {
                if (requests.incrementAndGet() < 3) {
                    respond("temporary failure", HttpStatusCode.InternalServerError)
                } else {
                    respond("success", HttpStatusCode.OK)
                }
            },
        ) {
            expectSuccess = true
        }
        val client = testClient(ktorClient)

        val result = client.queryGet("https://example.test/books", retries = 3)

        assertTrue(result.isSuccess)
        assertEquals("success", result.getOrNull())
        assertEquals(3, requests.get())
        client.close()
    }

    @Test
    fun fetchMultiplePages_passesRetriesToEachRequest() = runBlocking {
        val requests = AtomicInteger()
        val ktorClient = KtorHttpClient(
            MockEngine {
                requests.incrementAndGet()
                respond("temporary failure", HttpStatusCode.ServiceUnavailable)
            },
        ) {
            expectSuccess = true
        }
        val client = testClient(ktorClient)

        val (payloads, errors) = client.fetchMultiplePages(
            listOf("https://example.test/books"),
            retries = 1,
        )

        assertEquals(1, requests.get())
        assertEquals(listOf(""), payloads)
        assertEquals(1, errors.size)
        client.close()
    }
}
