package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookDetails
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenreInfo
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBook
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksPeriod
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksResponse
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsResponse
import com.github.risboo6909.mcp.flibusta.extractors.SearchBookInfo
import com.github.risboo6909.mcp.flibusta.extractors.SearchBooksByName
import com.github.risboo6909.mcp.flibusta.extractors.TopBooksByGenreResponse
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.executeWithTimeout
import com.github.risboo6909.utils.joinListParams
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val DEFAULT_TOOL_TIMEOUT_MILLIS: Long = 120 * 1000 // Flibusta can be slow sometimes
const val MAX_PAGES_PER_REQUEST = 10 // To reduce the time spent waiting for multiple pages
const val DEFAULT_TOP_BOOKS_LIMIT = 10
const val MAX_TOP_BOOKS_LIMIT = 10
const val DEFAULT_TOP_BOOKS_SCAN_PAGES = 5
const val MAX_TOP_BOOKS_SCAN_PAGES = 10

@Service
class FlibustaTools(
    httpHelper: HttpClientInterface,
    @Value("\${lit-mcp.tool-timeout-millis:120000}")
    private val toolTimeoutMillis: Long,
) {

    private val genresExtractor = GenresListExtractor(httpHelper)
    private val recExtractor = RecommendationsExtractor(httpHelper, genresExtractor)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper, genresExtractor)
    private val searchBookByName = SearchBooksByName(httpHelper)
    private val popularBooksExtractor = PopularBooksExtractor(httpHelper, bookInfoExtractor)

    @McpTool(
        name = "flibustaGetGenresList",
        title = "Flibusta Get Genres List",
        description = "[Flibusta] Get all available genres list",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getGenresList(): McpResponse<List<GenreInfo>> = executeWithTimeout(toolTimeoutMillis) {
        genresExtractor.getAllGenres()
    }

    @McpTool(
        name = "flibustaSearchBooksByName",
        title = "Flibusta Search Books By Name",
        description = "[Flibusta] Search books by name and returns their names and IDs",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun searchBooksByName(
        @McpToolParam(
            description = "Book name to search for on Flibusta (required)",
        )
        bookName: String,
    ): McpResponse<List<SearchBookInfo>> = executeWithTimeout(toolTimeoutMillis) {
        searchBookByName.searchBooksByName(
            URLEncoder.encode(bookName, StandardCharsets.UTF_8.toString()),
        )
    }

    @McpTool(
        name = "flibustaGetBookInfoByIds",
        title = "Flibusta Get Book Info By IDs",
        description = "[Flibusta] Get book info by book ID. Returns detailed info for each book ID such as " +
            "title, authors, genres, description, download links, user rating, user reviews, etc.",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getBookInfoByIds(
        @McpToolParam(
            description = "List of Flibusta book IDs to fetch (required)",
        )
        bookIds: List<Int>,
    ): McpResponse<List<BookDetails>> = executeWithTimeout(toolTimeoutMillis) {
        bookInfoExtractor.getBookInfoByIds(bookIds)
    }

    @McpTool(
        name = "flibustaGetPopularBooksList",
        title = "Flibusta Get Popular Books List",
        description = "[Flibusta] Get popular books list (100 items per source page, " +
            "max $MAX_PAGES_PER_REQUEST pages per request)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getPopularBooksList(
        @McpToolParam(
            description = "Start page index (0-based). Default: 0",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "End page index (0-based, exclusive). Default: 1",
            required = false,
        )
        endPage: Int? = null,
        @McpToolParam(
            description = "Period (TODAY/WEEK/ALL_TIME) to get popular books for. Default: ALL_TIME.",
            required = false,
        )
        period: PopularBooksPeriod? = null,
    ): McpResponse<PopularBooksResponse> = executeWithTimeout(toolTimeoutMillis) {
        val startPageValue = startPage ?: 0
        val endPageValue = endPage ?: 1
        val periodValue = period ?: PopularBooksPeriod.ALL_TIME

        validatePageRange<PopularBooksResponse>(startPageValue, endPageValue)
            ?.let { return@executeWithTimeout it }

        getPopularBooksPage(
            period = periodValue,
            startPage = startPageValue,
            endPage = endPageValue,
            genreNames = emptySet(),
        )
    }

    @McpTool(
        name = "flibustaGetTopBooksByGenre",
        title = "Flibusta Get Top Books By Genre",
        description = "[Flibusta] Get up to $MAX_TOP_BOOKS_LIMIT globally popular books matching the requested " +
            "genres. Scans popular-book pages in ranking order (default $DEFAULT_TOP_BOOKS_SCAN_PAGES, " +
            "max $MAX_TOP_BOOKS_SCAN_PAGES pages per request).",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getTopBooksByGenre(
        @McpToolParam(
            description = "Genre slugs to match (required). A book matches when it has any requested genre.",
        )
        genreSlugs: List<String>,
        @McpToolParam(
            description = "Number of books to return (1-$MAX_TOP_BOOKS_LIMIT). Default: $DEFAULT_TOP_BOOKS_LIMIT.",
            required = false,
        )
        limit: Int? = null,
        @McpToolParam(
            description = "Maximum ranking pages to scan (1-$MAX_TOP_BOOKS_SCAN_PAGES). " +
                "Default: $DEFAULT_TOP_BOOKS_SCAN_PAGES.",
            required = false,
        )
        maxScanPages: Int? = null,
        @McpToolParam(
            description = "Popularity period (TODAY/WEEK/ALL_TIME). Default: ALL_TIME.",
            required = false,
        )
        period: PopularBooksPeriod? = null,
    ): McpResponse<TopBooksByGenreResponse> = executeWithTimeout(toolTimeoutMillis) {
        val limitValue = limit ?: DEFAULT_TOP_BOOKS_LIMIT
        if (limitValue !in 1..MAX_TOP_BOOKS_LIMIT) {
            return@executeWithTimeout McpResponse(
                errors = listOf("Error: Limit must be between 1 and $MAX_TOP_BOOKS_LIMIT"),
            )
        }

        val maxScanPagesValue = maxScanPages ?: DEFAULT_TOP_BOOKS_SCAN_PAGES
        if (maxScanPagesValue !in 1..MAX_TOP_BOOKS_SCAN_PAGES) {
            return@executeWithTimeout McpResponse(
                errors = listOf("Error: Max scan pages must be between 1 and $MAX_TOP_BOOKS_SCAN_PAGES"),
            )
        }

        val requestedGenreSlugs = normalizeGenreSlugs(genreSlugs)
        if (requestedGenreSlugs.isEmpty()) {
            return@executeWithTimeout McpResponse(
                errors = listOf("Error: At least one genre slug is required"),
            )
        }

        val genreNamesResponse = resolveGenreNames(requestedGenreSlugs)
        if (genreNamesResponse.errors.isNotEmpty()) {
            return@executeWithTimeout McpResponse(errors = genreNamesResponse.errors)
        }

        val popularBooks = mutableListOf<PopularBook>()
        val seenBookIds = mutableSetOf<Int>()
        val errors = mutableListOf<String>()
        var scannedPages = 0
        var totalPages: Int? = null

        while (
            scannedPages < maxScanPagesValue &&
            popularBooks.size < limitValue &&
            (totalPages == null || scannedPages < totalPages)
        ) {
            val pageResponse = getPopularBooksPage(
                period = period ?: PopularBooksPeriod.ALL_TIME,
                startPage = scannedPages,
                endPage = scannedPages + 1,
                genreNames = genreNamesResponse.payload.orEmpty(),
                includeTotalPages = scannedPages == 0,
            )
            scannedPages++
            errors += pageResponse.errors

            val pagePayload = pageResponse.payload ?: break
            if (totalPages == null) {
                totalPages = pagePayload.totalPages
            }

            for (popularBook in pagePayload.popularBooks.orEmpty()) {
                val bookId = popularBook.book?.id
                if (bookId == null || seenBookIds.add(bookId)) {
                    popularBooks += popularBook
                }
                if (popularBooks.size == limitValue) break
            }
        }

        McpResponse(
            payload = TopBooksByGenreResponse(
                popularBooks = popularBooks,
                requestedLimit = limitValue,
                maxScanPages = maxScanPagesValue,
                scannedPages = scannedPages,
                limitReached = popularBooks.size == limitValue,
                scanLimitReached = popularBooks.size < limitValue &&
                    scannedPages == maxScanPagesValue &&
                    (totalPages == null || scannedPages < totalPages),
                totalPages = totalPages,
            ),
            errors = errors.distinct(),
        )
    }

    @McpTool(
        name = "flibustaGetRecommendedBooks",
        title = "Flibusta Get Recommended Books",
        description = "[Flibusta] Get recommended books paginated (50 items per page, " +
            "max $MAX_PAGES_PER_REQUEST pages per request)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedBooks(
        @McpToolParam(
            description = "Start page index (0-based). Default: 0",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "End page index (0-based, exclusive). Default: 1",
            required = false,
        )
        endPage: Int? = null,
        @McpToolParam(
            description = "Author name filter (optional). Default: none",
            required = false,
        )
        authorName: String? = null,
        @McpToolParam(
            description = "Genre slugs to filter by (optional). Default: none",
            required = false,
        )
        genreSlugs: List<String>? = null,
    ): McpResponse<RecommendationsResponse> {
        val startPageValue = startPage ?: 0
        val endPageValue = endPage ?: 1
        val authorNameValue = URLEncoder.encode(
            authorName ?: "",
            StandardCharsets.UTF_8.toString(),
        )

        val genreSlugsValue = joinListParams(genreSlugs, ",")

        validatePageRange<RecommendationsResponse>(startPageValue, endPageValue)
            ?.let { return it }

        return executeWithTimeout(toolTimeoutMillis) {
            recExtractor.getRecommendedBooks(
                mapOf(
                    "view" to "books",
                    "srcgenre" to genreSlugsValue,
                    "adata" to "name",
                    "author" to authorNameValue,
                ),
                startPageValue,
                endPageValue,
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendedAuthors",
        title = "Flibusta Get Recommended Authors",
        description = "[Flibusta] Get recommended authors paginated (50 items per page, " +
            "max $MAX_PAGES_PER_REQUEST pages per request)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            openWorldHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedAuthors(
        @McpToolParam(
            description = "Page index (0-based). Default: 0",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "End page index (0-based, exclusive). Default: 1",
            required = false,
        )
        endPage: Int? = null,
        @McpToolParam(
            description = "Genre slugs to filter by (optional). Default: null",
            required = false,
        )
        genreSlugs: List<String>? = null,
    ): McpResponse<RecommendationsResponse> {
        val startPageValue = startPage ?: 0
        val endPageValue = endPage ?: 1

        val genreSlugsValue = joinListParams(genreSlugs, ",")

        validatePageRange<RecommendationsResponse>(startPageValue, endPageValue)
            ?.let { return it }

        return executeWithTimeout(toolTimeoutMillis) {
            recExtractor.getRecommendedAuthors(
                mapOf(
                    "view" to "authors",
                    "srcgenre" to genreSlugsValue,
                ),
                startPageValue,
                endPageValue,
            )
        }
    }

    private fun normalizeGenreSlugs(genreSlugs: List<String>?): Set<String> = genreSlugs.orEmpty()
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    private suspend fun resolveGenreNames(requestedGenreSlugs: Set<String>): McpResponse<Set<String>> {
        if (requestedGenreSlugs.isEmpty()) {
            return McpResponse(payload = emptySet())
        }

        val genresResponse = genresExtractor.getAllGenres()
        if (genresResponse.errors.isNotEmpty()) {
            return McpResponse(errors = genresResponse.errors)
        }

        val genresBySlug = genresResponse.payload.orEmpty()
            .mapNotNull { genre -> genre.slug?.lowercase()?.let { it to genre.name } }
            .toMap()
        val unknownGenreSlugs = requestedGenreSlugs - genresBySlug.keys
        if (unknownGenreSlugs.isNotEmpty()) {
            return McpResponse(
                errors = listOf("Unknown genre slugs: ${unknownGenreSlugs.sorted().joinToString(", ")}"),
            )
        }

        return McpResponse(
            payload = requestedGenreSlugs.mapTo(mutableSetOf()) { genresBySlug.getValue(it) },
        )
    }

    private suspend fun getPopularBooksPage(
        period: PopularBooksPeriod,
        startPage: Int,
        endPage: Int,
        genreNames: Set<String>,
        includeTotalPages: Boolean = true,
    ): McpResponse<PopularBooksResponse> = popularBooksExtractor.getPopularBooks(
        period = period,
        startPage = startPage,
        endPage = endPage,
        genreNames = genreNames,
        includeTotalPages = includeTotalPages,
    )

    private fun <T> validatePageRange(startPage: Int, endPage: Int): McpResponse<T>? {
        if (startPage < 0) {
            return McpResponse(
                errors = listOf("Error: Start page must be 0 or greater"),
            )
        }
        if (endPage < 0) {
            return McpResponse(
                errors = listOf("Error: End page must be 0 or greater"),
            )
        }
        if (endPage <= startPage) {
            return McpResponse(
                errors = listOf("Error: End page must be greater than start page"),
            )
        }
        if (endPage - startPage > MAX_PAGES_PER_REQUEST) {
            return McpResponse(
                errors = listOf(
                    "Error: Requested page range exceeds max limit of " +
                        "$MAX_PAGES_PER_REQUEST pages per request",
                ),
            )
        }
        return null
    }
}
