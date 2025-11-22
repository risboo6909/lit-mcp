package com.github.risboo6909.mcp.flibusta.extractors

enum class PopularBooksPeriod(url: String) {
    TODAY("/stat/24"),
    WEEK("/stat/w"),
    ALL_TIME("/stat/b"),
}

data class PopularBook(
    val book: BookInfo?,
    val authors: List<AuthorInfo>?,
)
