package com.github.risboo6909.mcp.flibusta.extractors

enum class PopularBooksPeriod(val suffix: String) {
    TODAY("24"),
    WEEK("w"),
    ALL_TIME("b"),
}

data class PopularBook(
    val book: BookInfo?,
    val authors: List<AuthorInfo>?,
)

data class PopularBooksResponse(
    val popularBooks: List<PopularBook>? = null,
    val totalPages: Int? = null,
)
