package com.github.risboo6909.utils

import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
const val MAX_RETRIES = 10

@Component
class HttpClient : HttpClientInterface {

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(RecommendationsExtractor::class.java.name)
    }

    private val ktorClient = HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 5000
        }
    }

    override suspend fun queryGet(url: String, retries: Int): Result<String> {
        var attempt = 0
        var lastError: Throwable? = null
        val initialDelayMs = 200L

        val attempts = min(retries, MAX_RETRIES)

        while (attempt < attempts) {
            attempt++
            try {
                return Result.success(ktorClient.get(url).bodyAsText())
            } catch (e: Throwable) {
                lastError = e
            }

            if (attempt >= attempts) break

            val delayMs = initialDelayMs * 2.0.pow(attempt - 1)
                .toLong()
                .coerceAtMost(8000)

            LOG.info("Retrying $url in ${delayMs}ms (attempt ${attempt + 1}/$attempts)")
            delay(delayMs)
        }
        return Result.failure(lastError ?: RuntimeException("Failed to fetch $url"))
    }

    override suspend fun fetchMultiplePages(urls: List<String>, retries: Int): Pair<List<String>, List<String>> {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val pairs = coroutineScope {
            urls.map { url ->
                async {
                    semaphore.acquire()
                    try {
                        val res = queryGet(url)
                        if (res.isSuccess) {
                            res.getOrNull().orEmpty() to null
                        } else {
                            val err = res.exceptionOrNull()
                            LOG.error("Error fetching $url", err)
                            "" to "Error fetching $url, reason: ${err?.message}"
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
        val results = pairs.map { it.first }
        val errors = pairs.mapNotNull { it.second }
        return results to errors
    }
}
