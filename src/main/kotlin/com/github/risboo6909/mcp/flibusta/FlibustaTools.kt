package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookDetails
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenreInfo
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksPeriod
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksResponse
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsResponse
import com.github.risboo6909.mcp.flibusta.extractors.SearchBookInfo
import com.github.risboo6909.mcp.flibusta.extractors.SearchBooksByName
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.executeWithTimeout
import com.github.risboo6909.utils.joinListParams
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val DEFAULT_TOOL_TIMEOUT_MILLIS: Long = 300 * 1000 // Flibusta can be slow sometimes
const val MAX_PAGES_PER_REQUEST = 10 // To reduce the time spent waiting for multiple pages
const val MAX_RECOMMENDED_BOOKS_LIMIT = 50

@Service
class FlibustaTools(
    httpHelper: HttpClientInterface,
    @Value("\${lit-mcp.tool-timeout-millis:300000}")
    private val toolTimeoutMillis: Long,
) {

    private val genresExtractor = GenresListExtractor(httpHelper)
    private val recExtractor = RecommendationsExtractor(httpHelper, genresExtractor)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper, genresExtractor)
    private val searchBookByName = SearchBooksByName(httpHelper)
    private val popularBooksExtractor = PopularBooksExtractor(httpHelper)

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

        popularBooksExtractor.getPopularBooks(
            periodValue,
            startPageValue,
            endPageValue,
        )
    }

    @McpTool(
        name = "flibustaGetRecommendedBooks",
        title = "Flibusta Get Recommended Books",
        description = "[Flibusta] Get books ranked by user recommendation count, optionally filtered by author or " +
            "genre (50 items per page, max $MAX_PAGES_PER_REQUEST pages per request). Use " +
            "flibustaGetGenresList to discover valid genre slugs.",
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
        @McpToolParam(
            description = "Maximum number of books to return (1-$MAX_RECOMMENDED_BOOKS_LIMIT). Default: no limit.",
            required = false,
        )
        limit: Int? = null,
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

        if (limit != null && limit !in 1..MAX_RECOMMENDED_BOOKS_LIMIT) {
            return McpResponse(
                errors = listOf("Error: Limit must be between 1 and $MAX_RECOMMENDED_BOOKS_LIMIT"),
            )
        }

        return executeWithTimeout(toolTimeoutMillis) {
            val response = recExtractor.getRecommendedBooks(
                mapOf(
                    "view" to "books",
                    "srcgenre" to genreSlugsValue,
                    "adata" to "name",
                    "author" to authorNameValue,
                ),
                startPageValue,
                endPageValue,
            )
            if (limit == null) {
                response
            } else {
                response.copy(
                    payload = response.payload?.copy(
                        bookRecommendations = response.payload.bookRecommendations?.take(limit),
                    ),
                )
            }
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
