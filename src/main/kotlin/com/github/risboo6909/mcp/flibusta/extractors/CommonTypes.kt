package com.github.risboo6909.mcp.flibusta.extractors

data class AuthorInfo(
    val id: Int?,
    val name: String,
    val url: String,
    val isTranslator: Boolean,
)

data class BookInfo(
    val id: Int?,
    val title: String,
    val url: String,
)

data class GenreInfo(
    // Some pages return genres as IDs (for example recommendations),
    // other pages return slugs (for example genres list page),
    // so let's just support both
    val id: Int?,
    val slug: String?,
    val name: String,
    val url: String,
)
