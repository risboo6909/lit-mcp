package com.github.risboo6909.mcp.flibusta

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

fun parseSearchResults(rawHtml: String): List<BookInfo> {
    val html = Jsoup.parse(rawHtml)
    return html.select("div.book-row").map {
        val id = it.select("a").first().attr("href").split("/").last().toInt()
        val title = it.select("a").first().attr("title")
        BookInfo(id, title, null, null)
    }
}

fun parseRecommendationsByAuthor(rawHtml: String): List<AuthorRecommendation> {
    val doc: Document = Jsoup.parse(rawHtml)

    val dataTable: Element = doc.select("form[name=formrecs] table").last() ?: return emptyList()

    val allRows = dataTable.select("> tbody > tr, > tr")
    if (allRows.isEmpty()) return emptyList()

    val rows = allRows.drop(1)

    fun extractInt(text: String): Int =
        Regex("""\d+""").find(text)?.value?.toInt() ?: 0

    return rows.mapNotNull { tr ->
        val tds = tr.select("> td")
        if (tds.size != 4) return@mapNotNull null

        val authorCell = tds[0]
        val booksCell  = tds[1]
        val usersCell  = tds[2]
        val recsCell   = tds[3]

        val authorLink = authorCell.selectFirst("a") ?: return@mapNotNull null
        val authorName = authorLink.text().trim()
        val authorUrl  = authorLink.absUrl("href").ifBlank { authorLink.attr("href") }
        val authorId   = authorLink.attr("href")
            .substringAfter("/a/", "")
            .takeWhile { it.isDigit() }
            .toIntOrNull()

        AuthorRecommendation(
            AuthorRef(id = authorId, name = authorName, url = authorUrl),
            booksCount = extractInt(booksCell.text()),
            usersCount = extractInt(usersCell.text()),
            recsCount  = extractInt(recsCell.text())
        )
    }
}

fun parseRecommendationsByBook(html: String, baseUrl: String = "https://flibusta.is"): List<BookRecommendation> {
    val doc: Document = Jsoup.parse(html, baseUrl)

    val dataTable: Element = doc.select("form[name=formrecs] table").last() ?: return emptyList()

    val allRows = dataTable.select("> tbody > tr, > tr")
    if (allRows.isEmpty()) return emptyList()
    val rows = allRows.drop(1) // пропускаем шапку

    return rows.mapNotNull { tr ->
        val tds = tr.select("> td")
        if (tds.size != 4) return@mapNotNull null

        val authorCell = tds[0]
        val bookCell   = tds[1]
        val genreCell  = tds[2]
        val recsCell   = tds[3]

        val authors: List<AuthorRef> = authorCell.select("a[href^=/a/]").map { a ->
            val name = a.text().trim()
            val url  = a.absUrl("href").ifBlank { a.attr("href") }
            val id   = extractIdFromHref(a.attr("href"), "/a")
            AuthorRef(id = id, name = name, url = url)
        }.ifEmpty {
            val fallback = authorCell.text().trim()
            if (fallback.isNotEmpty()) listOf(AuthorRef(null, fallback, "")) else emptyList()
        }

        val bookLink = bookCell.select("a[href^=/b/]").last() ?: return@mapNotNull null
        val bookTitle = bookLink.text().trim()
        val bookUrl   = bookLink.absUrl("href").ifBlank { bookLink.attr("href") }
        val bookId    = extractIdFromHref(bookLink.attr("href"), "/b")
        val book = BookRef(id = bookId, title = bookTitle, url = bookUrl)

        val genres: List<GenreRef> = genreCell.select("a[href^=/g/]").map { a ->
            val name = a.text().trim()
            val url  = a.absUrl("href").ifBlank { a.attr("href") }
            val id   = extractIdFromHref(a.attr("href"), "/g")
            GenreRef(id = id, name = name, url = url)
        }

        val recsCount = extractFirstInt(recsCell.text())

        BookRecommendation(
            authors = authors,
            book = book,
            genres = genres,
            recommendationsCount = recsCount
        )
    }
}

private fun extractFirstInt(s: String): Int =
    Regex("""\d+""").find(s)?.value?.toInt() ?: 0

private fun extractIdFromHref(href: String, prefix: String): Int? {
    val part = href.substringAfter("$prefix/", "")
    val digits = part.takeWhile { it.isDigit() }
    return digits.toIntOrNull()
}