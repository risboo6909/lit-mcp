package com.github.risboo6909.utils

import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.HttpHeaders
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
import java.net.URI
import kotlin.math.pow
import kotlin.time.Duration.Companion.milliseconds
import io.ktor.client.HttpClient as KtorHttpClient

const val MAX_CONCURRENT_REQUESTS = 10
const val MAX_REQUEST_ATTEMPTS = 10
const val DEFAULT_HTTP_REQUEST_TIMEOUT_MILLIS = 10_000L

@Component
class HttpClient internal constructor(
    private val ktorClient: KtorHttpClient,
    private val mirrorManager: MirrorManager,
) : HttpClientInterface, MirrorAccess by mirrorManager {

    private val mirrorClient = ktorClient.config {
        followRedirects = false
        expectSuccess = false
    }

    @Autowired
    constructor(
        @Value("\${lit-mcp.http-request-timeout-millis:10000}")
        requestTimeoutMillis: Long,
        mirrorManager: MirrorManager,
    ) : this(createKtorClient(requestTimeoutMillis), mirrorManager)

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
    fun close() {
        mirrorClient.close()
        ktorClient.close()
    }

    override suspend fun queryGet(url: String, retries: Int): Result<String> = queryPage(url, retries).map { it.body }

    override suspend fun queryPage(url: String, retries: Int): Result<FetchedPage> {
        if (mirrorManager.handles(url)) {
            return mirrorManager.fetchWithFailover(url, retries, ::requestMirror)
        }
        return queryOther(url, retries)
    }

    private suspend fun requestMirror(url: String): MirrorResponse {
        var target = url
        var redirects = 0
        val redirectOrigins = mutableSetOf(originOf(URI(target)))
        var response = mirrorClient.get(target)
        while (response.status.value in 300..399) {
            val location = response.headers[HttpHeaders.Location]
                ?: throw MirrorRequestRejectedException("Redirect without Location from $target")
            val previousTarget = URI(target)
            target = previousTarget.resolve(location).toString()
            if (!mirrorManager.isAllowedMirrorUrl(target)) {
                throw MirrorRequestRejectedException("Untrusted redirect to $target")
            }
            val destination = originOf(URI(target))
            val previousOrigin = originOf(previousTarget)
            if (destination != previousOrigin && !redirectOrigins.add(destination)) {
                throw IllegalStateException("$destination: redirect to already attempted mirror")
            }
            if (++redirects > 5) throw MirrorRequestRejectedException("Too many redirects")
            response = mirrorClient.get(target)
        }
        return MirrorResponse(response.status.value, response.bodyAsText(), response.request.url.toString())
    }

    private fun originOf(uri: URI): String = "${uri.scheme}://${uri.host}"

    private suspend fun queryOther(url: String, retries: Int): Result<FetchedPage> {
        var attempt = 0
        var lastError: Throwable? = null
        val initialDelayMs = 200L

        val attempts = retries.coerceIn(1, MAX_REQUEST_ATTEMPTS)

        while (attempt < attempts) {
            attempt++
            try {
                val response = ktorClient.get(url)
                return Result.success(FetchedPage(response.bodyAsText(), response.request.url.toString()))
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
            delay(delayMs.milliseconds)
        }
        return Result.failure(lastError ?: RuntimeException("Failed to fetch $url"))
    }

    override suspend fun fetchMultiplePages(urls: List<String>, retries: Int): Pair<List<String>, List<String>> {
        val result = fetchPages(urls, retries)
        return result.pages.map { it?.body.orEmpty() } to result.errors
    }

    override suspend fun fetchPages(urls: List<String>, retries: Int): PageBatch {
        val semaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
        val pairs = coroutineScope {
            urls.map { url ->
                async {
                    semaphore.acquire()
                    try {
                        val res = queryPage(url, retries)
                        if (res.isSuccess) {
                            res.getOrNull() to null
                        } else {
                            val err = res.exceptionOrNull()
                            LOG.error("Error fetching $url", err)
                            null to "Error fetching $url, reason: ${err?.message}"
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
        val results = pairs.map { it.first }
        val errors = pairs.mapNotNull { it.second }
        return PageBatch(results, errors)
    }
}
