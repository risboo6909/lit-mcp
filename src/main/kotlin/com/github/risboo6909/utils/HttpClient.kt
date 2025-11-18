package com.github.risboo6909.utils

import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.HttpClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlin.math.min
import kotlin.math.pow

const val MAX_CONCURRENT_REQUESTS = 10

@Component
class HttpClient: HttpClientInterface {

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(RecommendationsExtractor::class.java.name)
    }

    override suspend fun queryGet(url: String, retries: Int): String {
        var attempt = 0
        var lastError: Throwable? = null
        val initialDelayMs = 200L

        val retries = min(retries, 10)

        while (attempt < retries) {
            attempt++
            val client = HttpClient(CIO)
            try {
                val response = client.get(url)
                val text = response.bodyAsText()
                return text
            } catch (e: Throwable) {
                lastError = e
            } finally {
                try {
                    client.close()
                } catch (_: Throwable) {
                }
            }

            if (attempt >= retries) break

            val delayMs = initialDelayMs * 2.0.pow(attempt - 1).toLong()
            delay(delayMs)
        }

        throw (lastError ?: RuntimeException("Failed to fetch $url"))
    }

    override suspend fun fetchMultiplePages(urls: List<String>): List<String> {
        val results = mutableListOf<String>()
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        coroutineScope {
            val futures = urls.map { url ->
                async {
                    semaphore.acquire()
                    try {
                        queryGet(url)
                    } catch (e: Exception) {
                        LOG.error("Error fetching $url, reason: $e")
                        ""
                    } finally {
                        semaphore.release()
                    }
                }
            }
            results.addAll(futures.awaitAll())
        }

        return results
    }

}