package com.github.risboo6909.mcp.flibusta.extractors

data class SeriesRef(val id: Int?, val name: String, val index: String?)

data class DownloadLink(val format: String, val url: String)

data class BookDetails(
    val title: String?,
    val authors: List<AuthorRef>?,
    val genres: List<GenreRef>?,
    val series: SeriesRef?,
    val year: Int?,
    val downloads: List<DownloadLink>?,
    val annotation: String?,
    val coverUrl: String?,
)
