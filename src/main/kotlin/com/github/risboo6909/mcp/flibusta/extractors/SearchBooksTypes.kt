package com.github.risboo6909.mcp.flibusta.extractors

data class SearchBookRef(
    val id: Int?,
    val title: String,
    val authors: List<AuthorInfo>?,
    val url: String,
)
