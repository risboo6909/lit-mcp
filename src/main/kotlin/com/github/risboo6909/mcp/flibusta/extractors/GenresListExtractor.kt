package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

class GenresListExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getAllGenres(): List<GenreRef> {
        return parse(httpHelper.queryGet(GENRES_LIST_URL))
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
