package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

class SearchBooksByName(private val httpHelper: HttpClientInterface) {
    suspend fun searchBooksByName(bookName: String): List<SearchBookRef> {
        return parse(httpHelper.queryGet("$BOOK_INFO_URL/$bookName"))
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<SearchBookRef> {
        val doc = Jsoup.parse(rawHtml, baseUrl)

        return doc.select("ol > li").mapNotNull { li ->
            val bookLink = li.selectFirst("a[href^=/b/]") ?: return@mapNotNull null
            val bookHref = bookLink.attr("href")

            val bookId = bookHref.substringAfterLast("/").toIntOrNull()
            val title = bookLink.text().trim()
            val fullBookUrl = bookLink.attr("abs:href")

            val authors = li.select("a[href^=/a/]").map { a ->

                val href = a.attr("href")
                val id = href.substringAfterLast("/").toIntOrNull()
                AuthorRef(
                    id = id,
                    name = a.text().trim(),
                    url = a.attr("abs:href"),
                    isTranslator = false,
                )
            }

            SearchBookRef(
                id = bookId,
                title = title,
                authors = authors.takeIf { it.isNotEmpty() },
                url = fullBookUrl,
            )
        }
    }
}
