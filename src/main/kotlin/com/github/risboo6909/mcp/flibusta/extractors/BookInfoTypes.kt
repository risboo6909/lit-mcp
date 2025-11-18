package com.github.risboo6909.mcp.flibusta.extractors

data class DownloadLink(val format: String, val url: String)

data class BookDetails(
    val title: String?,
    val authors: List<AuthorRef>?,
    val genres: List<GenreRef>?,
    val publishYear: Int?,
    val pagesNum: Int?,
    val downloads: List<DownloadLink>?,
    val annotation: String?,
    val coverUrl: String?,
    val totalRecommendations: Int?,
    val avgRating: Double?,
    val discussions: List<String>?,
)
