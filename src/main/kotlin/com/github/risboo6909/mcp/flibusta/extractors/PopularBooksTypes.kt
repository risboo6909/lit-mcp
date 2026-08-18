package com.github.risboo6909.mcp.flibusta.extractors

enum class PopularBooksPeriod(val suffix: String) {
    TODAY("24"),
    WEEK("w"),
    ALL_TIME("b"),
}

data class PopularBook(
    val book: BookInfo?,
    val authors: List<AuthorInfo>?,
    val genres: List<GenreInfo>? = null,
)

data class PopularBooksResponse(
    val popularBooks: List<PopularBook>? = null,
    val totalPages: Int? = null,
)

data class TopBooksByGenreResponse(
    val popularBooks: List<PopularBook>,
    val requestedLimit: Int,
    val maxScanPages: Int,
    val scannedPages: Int,
    val limitReached: Boolean,
    val scanLimitReached: Boolean,
    val totalPages: Int? = null,
)
