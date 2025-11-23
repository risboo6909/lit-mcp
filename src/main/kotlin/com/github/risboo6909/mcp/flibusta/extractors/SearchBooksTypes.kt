package com.github.risboo6909.mcp.flibusta.extractors

data class SearchBookInfo(
    val id: Int?,
    val title: String,
    val authors: List<AuthorInfo>?,
    val url: String,
)
