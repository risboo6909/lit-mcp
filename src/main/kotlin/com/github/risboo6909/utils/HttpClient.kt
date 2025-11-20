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
            requestTimeoutMillis = 10_000
        }
    }

    override suspend fun queryGet(url: String, retries: Int): Result<String> {
        var attempt = 0
        var lastError: Throwable? = null
        val initialDelayMs = 200L

        val retries = min(retries, MAX_RETRIES)

        while (attempt < retries) {
            attempt++
            try {
                return Result.success(ktorClient.get(url).bodyAsText())
            } catch (e: Throwable) {
                lastError = e
            }

            if (attempt >= retries) break

            val delayMs = initialDelayMs * 2.0.pow(attempt - 1)
                .toLong()
                .coerceAtMost(8000)

            LOG.info("Retrying $url in ${delayMs}ms (attempt ${attempt + 1}/$retries)")

            delay(delayMs)
        }

        return Result.failure(lastError ?: RuntimeException("Failed to fetch $url"))
    }

    override suspend fun fetchMultiplePages(urls: List<String>): Pair<List<String>, List<String>> {
        val results = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        coroutineScope {
            val futures = urls.map { url ->
                async {
                    semaphore.acquire()
                    val res = queryGet(url)
                    semaphore.release()
                    res.getOrElse {
                        errors.add("Error fetching $url, reason: ${res.exceptionOrNull()}")
                        // return empty string on error
                        ""
                    }
                }
            }
            results.addAll(futures.awaitAll())
        }
        return Pair(results, errors)
    }
}
