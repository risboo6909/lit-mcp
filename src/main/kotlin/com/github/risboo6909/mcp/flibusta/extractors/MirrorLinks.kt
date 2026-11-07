package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.flibusta.FLIBUSTA_CATALOG_ONLY_ORIGINS
import java.net.URI

internal const val MIRROR_PLACEHOLDER_ORIGIN = "https://flibusta-mirror.invalid"

private val DEFAULT_MIRROR_ORIGINS = setOf("https://flibusta.is", "https://flibusta.site", "https://flisland.net")

internal fun resolveMirrorUrl(
    url: String,
    sourceUrl: String,
    knownOrigins: Set<String> = DEFAULT_MIRROR_ORIGINS,
): String {
    val uri = runCatching { URI(url) }.getOrNull() ?: return url
    val origin = "${uri.scheme}://${uri.authority}"
    if (origin != MIRROR_PLACEHOLDER_ORIGIN && origin !in knownOrigins) {
        return url
    }
    val source = URI(sourceUrl)
    require(source.scheme == "https" && source.host != null && source.userInfo == null && source.port == -1)
    val sourceOrigin = "${source.scheme}://${source.host}"
    require(sourceOrigin in knownOrigins) { "Untrusted source origin: $sourceOrigin" }
    return sourceOrigin + uri.rawPath.orEmpty() + (uri.rawQuery?.let { "?$it" } ?: "") +
        (uri.rawFragment?.let { "#$it" } ?: "")
}

internal fun resolveDownloadUrl(url: String, sourceUrl: String, origins: Set<String>): String {
    val knownOrigins = origins + FLIBUSTA_CATALOG_ONLY_ORIGINS
    val resolved = resolveMirrorUrl(url, sourceUrl, knownOrigins)
    val uri = runCatching { URI(resolved) }.getOrNull() ?: return resolved
    val origin = "${uri.scheme}://${uri.authority}"
    if (origin !in FLIBUSTA_CATALOG_ONLY_ORIGINS) return resolved
    val downloadOrigin = origins.firstOrNull { it !in FLIBUSTA_CATALOG_ONLY_ORIGINS } ?: FLIBUSTA_BASE_URL
    return resolveMirrorUrl(resolved, downloadOrigin, knownOrigins + downloadOrigin)
}

internal fun SearchBookInfo.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): SearchBookInfo = copy(
    url = resolveMirrorUrl(url, sourceUrl, origins),
    authors = authors?.map { it.copy(url = resolveMirrorUrl(it.url, sourceUrl, origins)) },
    coverUrl = coverUrl?.let { resolveMirrorUrl(it, sourceUrl, origins) },
    downloads = downloads?.map { it.copy(url = resolveDownloadUrl(it.url, sourceUrl, origins)) },
)

internal fun AuthorSearchInfo.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): AuthorSearchInfo = copy(
    url = resolveMirrorUrl(url, sourceUrl, origins),
    imageUrl = imageUrl?.let { resolveMirrorUrl(it, sourceUrl, origins) },
)

internal fun AuthorInfo.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): AuthorInfo = copy(
    url = resolveMirrorUrl(url, sourceUrl, origins),
)
internal fun GenreInfo.resolveMirrorLinks(sourceUrl: String, origins: Set<String> = DEFAULT_MIRROR_ORIGINS): GenreInfo =
    copy(url = resolveMirrorUrl(url, sourceUrl, origins))
internal fun BookInfo.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): BookInfo =
    copy(url = resolveMirrorUrl(url, sourceUrl, origins))

internal fun BookDetails.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): BookDetails = copy(
    url = resolveMirrorUrl(url, sourceUrl, origins),
    authors = authors?.map { it.resolveMirrorLinks(sourceUrl, origins) },
    genres = genres?.map { it.resolveMirrorLinks(sourceUrl, origins) },
    downloads = downloads?.map { it.copy(url = resolveDownloadUrl(it.url, sourceUrl, origins)) },
    coverUrl = coverUrl?.let { resolveMirrorUrl(it, sourceUrl, origins) },
)

internal fun PopularBook.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): PopularBook = copy(
    book = book?.resolveMirrorLinks(sourceUrl, origins),
    authors = authors?.map { it.resolveMirrorLinks(sourceUrl, origins) },
)

internal fun AuthorRecommendation.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): AuthorRecommendation =
    copy(
        author = author.resolveMirrorLinks(sourceUrl, origins),
    )

internal fun BookRecommendation.resolveMirrorLinks(sourceUrl: String, origins: Set<String>): BookRecommendation = copy(
    authors = authors.map { it.resolveMirrorLinks(sourceUrl, origins) },
    book = book.resolveMirrorLinks(sourceUrl, origins),
    genres = genres.map { it.resolveMirrorLinks(sourceUrl, origins) },
)
