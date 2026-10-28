package com.github.risboo6909.mcp.flibusta.extractors

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jsoup.Jsoup
import java.net.URI
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
            return McpResponse(payload = resolveCachedGenres(genres))
        }

        return cacheMutex.withLock {
            cache.getIfPresent(ALL_GENRES_CACHE_KEY)?.let { genres ->
                return@withLock McpResponse(payload = resolveCachedGenres(genres))
            }

            val response = fetchAllGenres()
            if (response.errors.isEmpty() && !response.payload.isNullOrEmpty()) {
                cache.put(
                    ALL_GENRES_CACHE_KEY,
                    response.payload.map { genre ->
                        if (httpHelper.isAllowedMirrorUrl(genre.url)) {
                            val uri = URI(genre.url)
                            genre.copy(
                                url = MIRROR_PLACEHOLDER_ORIGIN + uri.rawPath.orEmpty() +
                                    (uri.rawQuery?.let { "?$it" } ?: ""),
                            )
                        } else {
                            genre
                        }
                    },
                )
            }
            response
        }
    }

    private fun resolveCachedGenres(genres: List<GenreInfo>): List<GenreInfo> =
        genres.map { it.resolveMirrorLinks(httpHelper.preferredMirrorOrigin(), httpHelper.allowedMirrorOrigins()) }

    suspend fun getGenreCatalog(): McpResponse<Map<String, GenreInfo>> {
        val response = getAllGenres()
        return McpResponse(
            payload = response.payload.orEmpty().associateBy { normalizeGenreName(it.name) },
            errors = response.errors,
        )
    }

    suspend fun validateGenreSlugs(genreSlugs: List<String>?): McpResponse<List<String>> {
        if (genreSlugs.isNullOrEmpty()) {
            return McpResponse(payload = emptyList())
        }

        val requestedSlugs = genreSlugs.map { it.trim().lowercase() }.distinct()
        if (requestedSlugs.any { it.isBlank() }) {
            return McpResponse(errors = listOf("Error: Genre slugs must not be blank"))
        }

        val genresResponse = getAllGenres()
        if (genresResponse.errors.isNotEmpty()) {
            return McpResponse(errors = genresResponse.errors)
        }

        val genresBySlug = genresResponse.payload.orEmpty()
            .mapNotNull { genre -> genre.slug?.let { it.lowercase() to it } }
            .toMap()
        val unknownSlugs = requestedSlugs.filterNot(genresBySlug::containsKey)
        if (unknownSlugs.isNotEmpty()) {
            return McpResponse(
                errors = listOf(
                    "Error: Unknown genre slugs: ${unknownSlugs.joinToString(", ")}. " +
                        "Use flibustaGetGenresList to discover valid genre slugs.",
                ),
            )
        }

        return McpResponse(payload = requestedSlugs.map { genresBySlug.getValue(it) })
    }

    private suspend fun fetchAllGenres(): McpResponse<List<GenreInfo>> {
        val result = httpHelper.queryPage(GENRES_LIST_URL)
        return McpResponse(
            payload = result.getOrNull()?.let { page ->
                parse(
                    page.body,
                    MIRROR_PLACEHOLDER_ORIGIN,
                ).map { it.resolveMirrorLinks(page.sourceUrl, page.mirrorOrigins) }
            }.orEmpty(),
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
