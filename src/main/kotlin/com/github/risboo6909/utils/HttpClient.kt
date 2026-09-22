package com.github.risboo6909.utils

import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import kotlin.math.pow
import io.ktor.client.HttpClient as KtorHttpClient

const val MAX_CONCURRENT_REQUESTS = 10
const val MAX_RETRIES = 10
const val DEFAULT_HTTP_REQUEST_TIMEOUT_MILLIS = 15_000L

@Component
class HttpClient internal constructor(private val ktorClient: KtorHttpClient) : HttpClientInterface {

    @Autowired
    constructor(
        @Value("\${lit-mcp.http-request-timeout-millis:15000}")
        requestTimeoutMillis: Long,
    ) : this(createKtorClient(requestTimeoutMillis))

    companion object {
        val LOG: Logger = LoggerFactory.getLogger(HttpClient::class.java.name)

        private fun createKtorClient(requestTimeoutMillis: Long): KtorHttpClient = KtorHttpClient(CIO) {
            expectSuccess = true
            install(HttpTimeout) {
                this.requestTimeoutMillis = requestTimeoutMillis
            }
        }
    }

    @PreDestroy
    fun close() = ktorClient.close()

    override suspend fun queryGet(url: String, retries: Int): Result<String> {
        var attempt = 0
        var lastError: Throwable? = null
        val initialDelayMs = 200L

        val attempts = retries.coerceIn(1, MAX_RETRIES)

        while (attempt < attempts) {
            attempt++
            try {
                return Result.success(ktorClient.get(url).bodyAsText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
                        val res = queryGet(url, retries)
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
