package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.HttpClientInterface


suspend fun<T> getRecommendations(
    httpHelper: HttpClientInterface,
    parser: (String) -> List<T>,
    url: String,
    recommendationsRequired: Int = 10
): List<T> {
    val allRecommendations = mutableListOf<T>()
    var page = 0

    while (allRecommendations.size < recommendationsRequired) {
        val urlWithPage = if (url.contains("?")) {
            url + "&page=${page++}"
        } else {
            url + "?page=${page++}"
        }
        val rawHtml = httpHelper.queryGet(urlWithPage)
        val parsed = parser(rawHtml)
        if (parsed.isEmpty()) break

        allRecommendations.addAll(parsed)
    }

    return allRecommendations
}
