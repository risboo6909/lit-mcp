package com.github.risboo6909.utils

import java.net.URI

const val DEFAULT_NUMBER_OF_RETRIES = 3

data class FetchedPage(
    val body: String,
    val sourceUrl: String,
    val mirrorOrigins: Set<String> = originOf(sourceUrl)?.let(::setOf) ?: emptySet(),
)
data class PageBatch(val pages: List<FetchedPage?>, val errors: List<String>)

private fun originOf(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val scheme = uri.scheme ?: return null
    val host = uri.host ?: return null
    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "$scheme://$host$port"
}

interface MirrorAccess {
    fun preferredMirrorOrigin(): String = allowedMirrorOrigins().firstOrNull().orEmpty()

    fun allowedMirrorOrigins(): Set<String> = emptySet()

    fun isAllowedMirrorUrl(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull()
        return uri != null && uri.scheme == "https" && uri.port == -1 && uri.userInfo == null &&
            originOf(url) in allowedMirrorOrigins()
    }
}

interface HttpClientInterface : MirrorAccess {

    suspend fun queryGet(url: String, retries: Int = DEFAULT_NUMBER_OF_RETRIES): Result<String>

    suspend fun fetchMultiplePages(
        urls: List<String>,
        retries: Int = DEFAULT_NUMBER_OF_RETRIES,
    ): Pair<List<String>, List<String>>

    suspend fun queryPage(url: String, retries: Int = DEFAULT_NUMBER_OF_RETRIES): Result<FetchedPage> =
        queryGet(url, retries).map { FetchedPage(it, url) }

    suspend fun fetchPages(urls: List<String>, retries: Int = DEFAULT_NUMBER_OF_RETRIES): PageBatch {
        val (bodies, errors) = fetchMultiplePages(urls, retries)
        return PageBatch(
            urls.mapIndexed { index, url ->
                bodies.getOrNull(index)?.takeIf { it.isNotBlank() }?.let { FetchedPage(it, url) }
            },
            errors,
        )
    }
}
