package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

class GenresListExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getAllGenres(): McpResponse<List<GenreRef>> {
        val result = httpHelper.queryGet(GENRES_LIST_URL)
        return McpResponse(
            payload = parse(result.getOrDefault("")),
            errors = result.exceptionOrNull()?.let {
                listOf(it.toString())
            } ?: listOf(),
        )
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<GenreRef> {
        val doc = Jsoup.parse(rawHtml, baseUrl)

        return doc.select("h1.title:matchesOwn(Список жанров) ~ ul > li")
            .mapNotNull { li ->
                val a = li.selectFirst("a[href^=/g/]") ?: return@mapNotNull null
                extractGenreInfo(a)
            }
    }
}
