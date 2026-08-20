package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.joinKeyValueParams
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URI

class OpdsExtractor(private val httpHelper: HttpClientInterface) {

    suspend fun searchBooksByName(bookName: String, includeDescription: Boolean): McpResponse<List<SearchBookInfo>> {
        val url = buildSearchUrl("books", bookName)
        val result = httpHelper.queryGet(url)
        return McpResponse(
            payload = parseBookEntries(result.getOrDefault(""), includeDescription),
            errors = result.exceptionOrNull()?.let { listOf(it.toString()) }.orEmpty(),
        )
    }

    suspend fun searchAuthorsByName(authorName: String): McpResponse<List<AuthorSearchInfo>> {
        val url = buildSearchUrl("authors", authorName)
        val result = httpHelper.queryGet(url)
        return McpResponse(
            payload = parseAuthorEntries(result.getOrDefault("")),
            errors = result.exceptionOrNull()?.let { listOf(it.toString()) }.orEmpty(),
        )
    }

    suspend fun getNewBooks(limit: Int, includeDescription: Boolean): McpResponse<List<SearchBookInfo>> {
        val urls = pageIndexes(limit).map { page ->
            if (page == 0) "$OPDS_BASE_URL/new/0/new" else "$OPDS_BASE_URL/new/$page/new/"
        }
        return fetchBooks(urls, limit, includeDescription)
    }

    suspend fun getBooksByAuthorId(
        authorId: Int,
        limit: Int,
        includeDescription: Boolean,
    ): McpResponse<List<SearchBookInfo>> {
        val baseUrl = "$OPDS_BASE_URL/author/$authorId/alphabet"
        val urls = pageIndexes(limit).map { page ->
            if (page == 0) baseUrl else "$baseUrl/$page"
        }
        return fetchBooks(urls, limit, includeDescription)
    }

    private suspend fun fetchBooks(
        urls: List<String>,
        limit: Int,
        includeDescription: Boolean,
    ): McpResponse<List<SearchBookInfo>> {
        val (payloads, errors) = httpHelper.fetchMultiplePages(urls)
        return McpResponse(
            payload = payloads.flatMap { parseBookEntries(it, includeDescription) }.take(limit),
            errors = errors,
        )
    }

    private fun pageIndexes(limit: Int): IntRange = 0 until ((limit + OPDS_BOOKS_PER_PAGE - 1) / OPDS_BOOKS_PER_PAGE)

    private fun buildSearchUrl(searchType: String, searchTerm: String): String = joinKeyValueParams(
        OPDS_SEARCH_URL,
        mapOf(
            "searchType" to searchType,
            "searchTerm" to searchTerm,
        ),
    )

    private fun parseBookEntries(
        rawXml: String,
        includeDescription: Boolean,
        baseUrl: String = FLIBUSTA_BASE_URL,
    ): List<SearchBookInfo> {
        val doc = Jsoup.parse(rawXml, baseUrl, Parser.xmlParser())

        return doc.select("feed > entry").mapNotNull { entry ->
            val bookLinks = entry.select("link[href^=/b/]")
            val bookLink = bookLinks.firstOrNull { it.attr("rel") == "alternate" }
                ?: bookLinks.firstOrNull()
                ?: return@mapNotNull null
            val bookId = extractIdFromHref(bookLink.attr("href"), "/b") ?: return@mapNotNull null
            val title = entry.selectFirst("title")?.text()?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null

            val authors = entry.select("author").mapNotNull { parseBookAuthor(it, baseUrl) }
            val content = entry.selectFirst("content")?.text().orEmpty()
            SearchBookInfo(
                id = bookId,
                title = title,
                authors = authors.takeIf { it.isNotEmpty() },
                url = URI(baseUrl).resolve("/b/$bookId").toString(),
                genres = entry.select("category")
                    .mapNotNull { it.attr("label").ifBlank { it.attr("term") }.trim().takeIf(String::isNotEmpty) }
                    .distinct()
                    .takeIf { it.isNotEmpty() },
                description = content.takeIf { includeDescription }?.let(::extractDescription),
                language = entry.getElementsByTag("dc:language").first()?.text()?.trim()?.takeIf(String::isNotEmpty),
                publishYear = entry.getElementsByTag("dc:issued").first()?.text()?.let(::extractYear),
                coverUrl = entry.select("link")
                    .firstOrNull { it.attr("rel") == OPDS_IMAGE_REL }
                    ?.attr("href")
                    ?.takeIf(String::isNotEmpty)
                    ?.let { URI(baseUrl).resolve(it).toString() },
                downloads = extractDownloadLinks(entry, baseUrl),
                downloadsCount = extractDownloadsCount(content),
            )
        }
    }

    private fun extractDescription(content: String): String? {
        val metadataStart = OPDS_METADATA_START.find(content)?.range?.first ?: content.length
        return Jsoup.parseBodyFragment(content.substring(0, metadataStart))
            .text()
            .trim()
            .takeIf(String::isNotEmpty)
    }

    private fun extractYear(value: String): Int? = Regex("""\b\d{4}\b""").find(value)?.value?.toIntOrNull()

    private fun extractDownloadsCount(content: String): Int? = OPDS_DOWNLOADS_COUNT
        .find(content)
        ?.groupValues
        ?.getOrNull(1)
        ?.filter(Char::isDigit)
        ?.toIntOrNull()

    private fun extractDownloadLinks(entry: Element, baseUrl: String): List<DownloadLink>? {
        val declaredFormat = entry.getElementsByTag("dc:format").first()?.text()?.trim()
        return entry.select("link")
            .filter { it.attr("rel") == OPDS_ACQUISITION_REL }
            .mapNotNull { link ->
                val href = link.attr("href").trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                val pathFormat = URI(href).path.substringAfterLast('/').takeUnless { it == "download" }
                val format = pathFormat
                    ?.takeIf(String::isNotEmpty)
                    ?: declaredFormat?.substringBefore('+')?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                DownloadLink(
                    format = format.lowercase(),
                    url = URI(baseUrl).resolve(href).toString(),
                )
            }
            .distinct()
            .takeIf { it.isNotEmpty() }
    }

    private fun parseBookAuthor(author: Element, baseUrl: String): AuthorInfo? {
        val name = author.selectFirst("name")?.text()?.trim().orEmpty()
        val uri = author.selectFirst("uri")?.text()?.trim().orEmpty()
        if (name.isEmpty()) return null

        return AuthorInfo(
            id = uri.takeIf { it.isNotEmpty() }?.let { extractIdFromHref(it, "/a") },
            name = name,
            url = uri.takeIf { it.isNotEmpty() }?.let { URI(baseUrl).resolve(it).toString() }.orEmpty(),
            isTranslator = false,
        )
    }

    private fun parseAuthorEntries(rawXml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<AuthorSearchInfo> {
        val doc = Jsoup.parse(rawXml, baseUrl, Parser.xmlParser())

        return doc.select("feed > entry").mapNotNull { entry ->
            val authorLink = entry.select("link[href^=/opds/author/]").firstOrNull()
                ?: return@mapNotNull null
            val authorId = extractIdFromHref(authorLink.attr("href"), "/opds/author")
                ?: return@mapNotNull null
            val name = entry.selectFirst("title")?.text()?.trim().orEmpty()
            if (name.isEmpty()) return@mapNotNull null

            val imagePath = entry.select("link")
                .firstOrNull { it.attr("rel") == OPDS_IMAGE_REL }
                ?.attr("href")
            AuthorSearchInfo(
                id = authorId,
                name = name,
                url = URI(baseUrl).resolve("/a/$authorId").toString(),
                booksCount = Regex("""\d+""").find(entry.selectFirst("content")?.text().orEmpty())
                    ?.value
                    ?.toIntOrNull(),
                imageUrl = imagePath?.let { URI(baseUrl).resolve(it).toString() },
            )
        }
    }

    private companion object {
        const val OPDS_ACQUISITION_REL = "http://opds-spec.org/acquisition/open-access"
        val OPDS_METADATA_START = Regex(
            """<br\s*/?>\s*(?:Translation|Publication\s+year|Format|Language|Size|Downloads|""" +
                """Перевод|Год\s+издания|Формат|Язык|Размер|Скачиваний)\s*:""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val OPDS_DOWNLOADS_COUNT = Regex(
            """(?:Downloads|Скачиваний)\s*:\s*([\d\s]+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
