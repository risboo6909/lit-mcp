package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode

class BookInfoExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun getBookInfoByIds(bookIds: List<Int>): List<BookDetails> {
        return httpHelper
            .fetchMultiplePages(bookIds.map { "$BOOK_INFO_URL/$it" })
            .filter { !it.isEmpty() }
            .map { parse(it) }
    }

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): BookDetails {
        val doc = Jsoup.parse(rawHtml, baseUrl)
        return BookDetails(
            title = extractTitle(doc),
            authors = extractAuthors(doc),
            genres = extractGenres(doc),
            publishYear = extractPublishYear(doc),
            pagesNum = extractPageCount(doc),
            downloads = extractDownloads(doc),
            annotation = extractAnnotation(doc),
            coverUrl = extractCoverImage(doc),
            totalRecommendations = extractTotalRecommendations(doc),
            avgRating = extractAvgRating(doc),
            discussions = extractDiscussions(doc),
        )
    }

    private fun extractTitle(doc: Document): String? {
        return doc.selectFirst("h1.title")?.text()
    }

    private fun extractDiscussions(doc: Document): List<String> = doc.select("span[class^=container_]")
        .map { it.text().trim() }
        .filter { it.isNotEmpty() }

    private fun extractAvgRating(doc: Document): Double? {
        val text = doc.selectFirst("#newann p")?.text() ?: return null
        val match = Regex("среднее\\s+([0-9.,]+)").find(text) ?: return null
        val raw = match.groupValues[1].replace(',', '.')
        return raw.toDoubleOrNull()
    }

    private fun extractTotalRecommendations(doc: Document): Int? {
        val a = doc.selectFirst("a[href^=/rec?]") ?: return null
        val text = a.text()
        return Regex("(\\d+)").find(text)?.groupValues?.get(1)?.toInt()
    }

    private fun extractPublishYear(doc: Document): Int? {
        val el = doc.selectFirst("*:matchesOwn(издание\\s+\\d{4})")
            ?: return null

        val text = el.ownText()

        val regex = Regex("издание\\s+(\\d{4})")
        return regex.find(text)?.groupValues?.get(1)?.toInt()
    }

    private fun extractPageCount(doc: Document): Int? {
        val span = doc.selectFirst("span[style=size]") ?: return null
        val text = span.text()

        val regex = Regex("(\\d+)\\s*с\\.?")
        return regex.find(text)?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun extractAnnotation(doc: Document): String? {
        val p = doc.selectFirst(
            "h2:containsOwn(Аннотация) + p, " +
                "h3:containsOwn(Аннотация) + p",
        )
        return p?.text()?.trim()
    }

    private fun extractCoverImage(doc: Document): String? {
        return doc
            .selectFirst("img[alt='Cover image']")?.attr("abs:src")
    }

    private fun extractAuthors(doc: Document): List<AuthorRef> {
        val result = mutableListOf<AuthorRef>()
        val title = doc.selectFirst("h1.title") ?: return result

        var node = title.nextSibling()
        var isTranslator = false

        while (node != null) {
            if (node is Element &&
                node.tagName() == "div" &&
                node.classNames().any { it.startsWith("g-") }
            ) {
                break
            }

            when (node) {
                is TextNode -> {
                    if (node.text().contains("перевод", ignoreCase = true)) {
                        isTranslator = true
                    }
                }

                is Element -> {
                    if (node.tagName() == "a") {
                        result += extractAuthorInfo(node, isTranslator)
                    }
                }
            }

            node = node.nextSibling()
        }

        return result
    }

    private fun extractGenres(doc: Document): List<GenreRef> {
        return doc.select("a.genre").map {
            extractGenreInfo(it)
        }
    }

    private fun extractDownloads(doc: Document): List<DownloadLink> {
        return listOf("fb2", "epub", "mobi")
            .map { format -> Pair(format, buildDownloadLink(doc, format)) }
            .filterNot { it.second == null }
            .map { res ->
                DownloadLink(
                    res.first,
                    res.second!!,
                )
            }
    }

    private fun buildDownloadLink(doc: Document, format: String): String? {
        return doc.selectFirst("a[href*=$format]")?.absUrl("href")
    }
}
