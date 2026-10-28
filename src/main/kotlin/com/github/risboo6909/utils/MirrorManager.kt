package com.github.risboo6909.utils

import kotlinx.coroutines.CancellationException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private val PRIMARY_COOLDOWN_NANOS = TimeUnit.MINUTES.toNanos(5)

data class MirrorResponse(val statusCode: Int, val body: String, val sourceUrl: String)

class MirrorRequestRejectedException(message: String) : IllegalStateException(message)

class MirrorManager(
    mirrors: List<String>,
    sourceOrigins: Set<String> = emptySet(),
    private val clock: () -> Long = System::nanoTime,
) : MirrorAccess {
    private val mirrors = mirrors.also { entries ->
        require(entries.isNotEmpty() && entries.distinct().size == entries.size)
        entries.forEach { entry ->
            val uri = URI(entry)
            require(
                uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null &&
                    uri.port == -1 && uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null &&
                    entry == "https://${uri.host}",
            ) { "Invalid mirror origin: $entry" }
        }
    }
    private val sourceOrigins = sourceOrigins.also { entries -> entries.forEach(::validateOrigin) }

    private data class Preferred(val origin: String, val since: Long)

    private val preferred = AtomicReference(Preferred(mirrors.first(), clock()))

    fun handles(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return (originOf(uri) in mirrors || originOf(uri) in sourceOrigins) && uri.port == -1 && uri.userInfo == null
    }

    fun mirrorAttempts(retries: Int): List<String> {
        val current = preferred.get()
        val now = clock()
        val first = if (current.origin != mirrors.first() && now - current.since >= PRIMARY_COOLDOWN_NANOS &&
            preferred.compareAndSet(current, current.copy(since = now))
        ) {
            mirrors.first()
        } else {
            preferred.get().origin
        }
        val order = listOf(first) + mirrors.filterNot { it == first }
        val attempts = retries.coerceIn(1, MAX_REQUEST_ATTEMPTS)
        return List(attempts) { attempt -> order[attempt % order.size] }
    }

    suspend fun fetchWithFailover(
        url: String,
        retries: Int,
        request: suspend (String) -> MirrorResponse,
    ): Result<FetchedPage> {
        val failures = mutableListOf<String>()
        for (mirror in mirrorAttempts(retries)) {
            onAttempt(mirror)
            val candidate = urlOnMirror(url, mirror)
            try {
                val response = request(candidate)
                if (response.statusCode in 400..499 && response.statusCode != 429) {
                    return Result.failure(
                        IllegalStateException("HTTP ${response.statusCode} from ${response.sourceUrl}"),
                    )
                }
                if (response.statusCode == 429 || response.statusCode >= 500) {
                    val origin = runCatching { originOf(URI(response.sourceUrl)) }.getOrDefault(mirror)
                    failures += "$origin: HTTP ${response.statusCode}"
                    continue
                }
                if (response.statusCode !in 200..299) {
                    return Result.failure(
                        IllegalStateException("HTTP ${response.statusCode} from ${response.sourceUrl}"),
                    )
                }
                if (!isAllowedMirrorUrl(response.sourceUrl)) {
                    return Result.failure(IllegalStateException("Untrusted mirror response from ${response.sourceUrl}"))
                }
                recordSuccess(response.sourceUrl)
                return Result.success(FetchedPage(response.body, response.sourceUrl, allowedMirrorOrigins()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: MirrorRequestRejectedException) {
                return Result.failure(e)
            } catch (e: Exception) {
                failures += "$mirror: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return Result.failure(IllegalStateException("All mirrors failed: ${failures.joinToString("; ")}"))
    }

    fun onAttempt(origin: String) {
        if (origin == mirrors.first()) {
            val attemptTime = clock()
            preferred.updateAndGet { it.copy(since = attemptTime) }
        }
    }

    fun recordSuccess(url: String) {
        val origin = runCatching { originOf(URI(url)) }.getOrNull() ?: return
        if (origin !in mirrors) return
        preferred.updateAndGet { previous ->
            if (previous.origin == origin) previous else Preferred(origin, previous.since)
        }
    }

    fun urlOnMirror(url: String, mirror: String): String {
        val uri = URI(url)
        return mirror + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "")
    }

    override fun preferredMirrorOrigin(): String = preferred.get().origin

    override fun allowedMirrorOrigins(): Set<String> = mirrors.toSet() + sourceOrigins

    override fun isAllowedMirrorUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.userInfo == null && uri.port == -1 && uri.rawFragment == null && originOf(uri) in mirrors
    }

    private fun originOf(uri: URI): String = "${uri.scheme}://${uri.host}"

    private fun validateOrigin(origin: String) {
        val uri = URI(origin)
        require(
            uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.port == -1 &&
                uri.rawPath.isNullOrEmpty() && uri.rawQuery == null && uri.rawFragment == null &&
                origin == "https://${uri.host}",
        ) { "Invalid source origin: $origin" }
    }
}
