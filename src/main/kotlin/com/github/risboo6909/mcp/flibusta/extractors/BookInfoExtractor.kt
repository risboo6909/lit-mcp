package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.slf4j.LoggerFactory


const val BOOKINFO_URL = "$FLIBUSTA_BASE_URL/b"

class BookInfoExtractor(private val httpHelper: HttpClientInterface) {

    companion object {
        val LOG = LoggerFactory.getLogger(BookInfoExtractor::class.java.name)
    }

    suspend fun getBookInfoById(
        bookId: Int,
    ): BookDetails {
        val rawHtml = httpHelper.queryGet("$BOOKINFO_URL/$bookId")
        return parse(rawHtml)
    }

    suspend fun <T> getForumDiscussions(
        params: Map<String, String>,
    ): List<T> {

        return emptyList()
    }

    fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): BookDetails {
        val doc = Jsoup.parse(rawHtml, baseUrl)
        return BookDetails(
            title = "",                  // заглушка
            authors = emptyList(),       // заглушка
            genres = emptyList(),        // заглушка
            series = null,
            year = null,
            downloads = extractDownloads(doc),
            annotation = extractAnnotation(doc),
            coverUrl = extractCoverImage(doc),
        )
    }

    private fun extractAnnotation(doc: Document): String? {
        val p = doc.selectFirst(
            "h2:containsOwn(Аннотация) + p, " +
                "h3:containsOwn(Аннотация) + p"
        )
        return p?.text()
    }

    private fun extractCoverImage(doc: Document): String? {
        return doc.selectFirst("img[alt='Cover image']")?.attr("abs:src")
    }

    private fun extractDownloads(doc: Document): List<DownloadLink> {
        val result = mutableListOf<DownloadLink>()
        return result
    }
}

