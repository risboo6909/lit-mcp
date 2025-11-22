package com.github.risboo6909.mcp.flibusta.extractors

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

fun extractIdFromHref(href: String, prefix: String): Int? {
    val part = href.substringAfter("$prefix/", "")
    val digits = part.takeWhile { it.isDigit() }
    return digits.toIntOrNull()
}
