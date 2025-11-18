package com.github.risboo6909.mcp.flibusta.extractors

data class AuthorRef(
    val id: Int?,
    val name: String,
    val url: String,
    val isTranslator: Boolean,
)

data class BookRef(
    val id: Int?,
    val title: String,
    val url: String,
)

data class GenreRef(
    // Some pages return genres as IDs (for example recommendations),
    // other pages return slugs (for example genres list page),
    // so let's just support both
    val id: Int?,
    val slug: String?,
    val name: String,
    val url: String,
)
