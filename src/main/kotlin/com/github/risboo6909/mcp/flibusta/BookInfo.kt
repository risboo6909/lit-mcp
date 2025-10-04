package com.github.risboo6909.mcp.flibusta

data class BookInfo(
    val id: Int,
    val title: String,
    val annotation: String?,
    val userReviews: List<String>?,
)
