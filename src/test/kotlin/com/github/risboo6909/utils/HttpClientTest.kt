package com.github.risboo6909.utils

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.expectSuccess
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import io.ktor.client.HttpClient as KtorHttpClient

class HttpClientTest {

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
        val client = HttpClient(ktorClient)

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
        val client = HttpClient(ktorClient)

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
