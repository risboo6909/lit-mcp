package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.joinKeyValueParams
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URI

class SearchBooksByName(private val httpHelper: HttpClientInterface) {
    suspend fun searchBooksByName(bookName: String): McpResponse<List<SearchBookInfo>> {
        val url = joinKeyValueParams(
            OPDS_SEARCH_URL,
            mapOf(
                "searchType" to "books",
                "searchTerm" to bookName,
            ),
        )
        val result = httpHelper.queryGet(url)
        return McpResponse(
            parse(result.getOrDefault("")),
            result.exceptionOrNull()?.let {
                listOf(it.toString())
            } ?: listOf(),
        )
    }

    private fun parse(rawXml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<SearchBookInfo> {
        val doc = Jsoup.parse(rawXml, baseUrl, Parser.xmlParser())

        return doc.select("feed > entry").mapNotNull { entry ->
            val bookLinks = entry.select("link[href^=/b/]")
            val bookLink = bookLinks.firstOrNull { it.attr("rel") == "alternate" }
                ?: bookLinks.firstOrNull()
                ?: return@mapNotNull null
            val bookHref = bookLink.attr("href")

            val bookId = extractIdFromHref(bookHref, "/b") ?: return@mapNotNull null
            val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            val fullBookUrl = URI(baseUrl).resolve("/b/$bookId").toString()

            val authors = entry.select("author").mapNotNull { author ->
                val name = author.selectFirst("name")?.text()?.trim().orEmpty()
                val uri = author.selectFirst("uri")?.text()?.trim().orEmpty()
                if (name.isEmpty()) return@mapNotNull null
                AuthorInfo(
                    id = uri.takeIf { it.isNotEmpty() }?.let { extractIdFromHref(it, "/a") },
                    name = name,
                    url = uri.takeIf { it.isNotEmpty() }?.let { URI(baseUrl).resolve(it).toString() }.orEmpty(),
                    isTranslator = false,
                )
            }

            SearchBookInfo(
                id = bookId,
                title = title,
                authors = authors.takeIf { it.isNotEmpty() },
                url = fullBookUrl,
            )
        }
    }
}
