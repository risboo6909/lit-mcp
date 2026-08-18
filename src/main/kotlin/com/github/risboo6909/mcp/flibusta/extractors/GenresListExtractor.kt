package com.github.risboo6909.mcp.flibusta.extractors

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.time.Duration

class GenresListExtractor(private val httpHelper: HttpClientInterface) {

    companion object {
        private const val ALL_GENRES_CACHE_KEY = "all-genres"
    }

    private val cache: Cache<String, List<GenreInfo>> = Caffeine.newBuilder()
        .maximumSize(1)
        .expireAfterWrite(Duration.ofHours(24))
        .build()
    private val cacheMutex = Mutex()

    suspend fun getAllGenres(): McpResponse<List<GenreInfo>> {
        cache.getIfPresent(ALL_GENRES_CACHE_KEY)?.let { genres ->
            return McpResponse(payload = genres)
        }

        return cacheMutex.withLock {
            cache.getIfPresent(ALL_GENRES_CACHE_KEY)?.let { genres ->
                return@withLock McpResponse(payload = genres)
            }

            val response = fetchAllGenres()
            if (response.errors.isEmpty() && !response.payload.isNullOrEmpty()) {
                cache.put(ALL_GENRES_CACHE_KEY, response.payload.toList())
            }
            response
        }
    }

    suspend fun getGenreCatalog(): McpResponse<Map<String, GenreInfo>> {
        val response = getAllGenres()
        return McpResponse(
            payload = response.payload.orEmpty().associateBy { normalizeGenreName(it.name) },
            errors = response.errors,
        )
    }

    private suspend fun fetchAllGenres(): McpResponse<List<GenreInfo>> {
        val result = httpHelper.queryGet(GENRES_LIST_URL)
        return McpResponse(
            payload = parse(result.getOrDefault("")),
            errors = result.exceptionOrNull()?.let {
                listOf(it.toString())
            } ?: listOf(),
        )
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<GenreInfo> {
        val doc = Jsoup.parse(rawHtml, baseUrl)

        return doc.select("h1.title:matchesOwn(Список жанров) ~ ul > li")
            .mapNotNull { li ->
                val a = li.selectFirst("a[href^=/g/]") ?: return@mapNotNull null
                extractGenreInfo(a)
            }
    }
}
