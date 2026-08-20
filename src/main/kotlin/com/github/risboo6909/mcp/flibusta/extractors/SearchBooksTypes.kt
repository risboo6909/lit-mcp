package com.github.risboo6909.mcp.flibusta.extractors

data class SearchBookInfo(
    val id: Int?,
    val title: String,
    val authors: List<AuthorInfo>?,
    val url: String,
    val genres: List<String>?,
    val description: String?,
    val language: String?,
    val publishYear: Int?,
    val coverUrl: String?,
    val downloads: List<DownloadLink>?,
    val downloadsCount: Int?,
)

data class AuthorSearchInfo(
    val id: Int,
    val name: String,
    val url: String,
    val booksCount: Int?,
    val imageUrl: String?,
)
