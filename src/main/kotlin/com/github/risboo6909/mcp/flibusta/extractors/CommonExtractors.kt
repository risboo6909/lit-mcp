package com.github.risboo6909.mcp.flibusta.extractors

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

fun extractGenreInfo(e: Element): GenreInfo {
    return GenreInfo(
        id = extractIdFromHref(e.attr("href"), "/g"),
        name = e.text().trim(),
        url = e.absUrl("href").ifBlank { e.attr("href") },
        slug = e.attr("href")
            .substringAfterLast('/')
            // slug should not contain digits
            .takeIf { it.all { ch -> !ch.isDigit() } },
    )
}

fun extractAuthorInfo(e: Element, isTranslator: Boolean): AuthorInfo {
    return AuthorInfo(
        id = extractIdFromHref(e.attr("href"), "/a"),
        name = e.text().trim(),
        url = e.absUrl("href").ifBlank { e.attr("href") },
        isTranslator = isTranslator,
    )
}

fun extractBookInfo(e: Element): BookInfo {
    return BookInfo(
        id = extractIdFromHref(e.attr("href"), "/b"),
        title = e.text().trim(),
        url = e.absUrl("href").ifBlank { e.attr("href") },
    )
}

fun extractIdFromHref(href: String, prefix: String): Int? {
    val part = href.substringAfter("$prefix/", "")
    val digits = part.takeWhile { it.isDigit() }
    return digits.toIntOrNull()
}

fun extractLastPageNumber(doc: Document): Pair<Int?, String?> {
    val a = doc.selectFirst("li.pager-last a, li.pager-item.last a")
        ?: return 1 to null  // if there's no last page link, assume there's only one page

    val fromHref = Regex("""page=(\d+)""")
        .find(a.attr("href"))
        ?.groupValues?.getOrNull(1)
        ?.toIntOrNull()

    if (fromHref != null) return fromHref to null

    val fromText = a.text().toIntOrNull()
    if (fromText != null) return fromText to null

    return null to "Failed to extract page number from either href or text"
}
