package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookDetails
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenreRef
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.PopularBooksExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsResponse
import com.github.risboo6909.mcp.flibusta.extractors.SearchBookRef
import com.github.risboo6909.mcp.flibusta.extractors.SearchBooksByName
import com.github.risboo6909.utils.HttpClientInterface
import com.github.risboo6909.utils.joinListParams
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val FETCH_TIMEOUT_MILLIS: Long = 60 * 1000 // Flibusta can be slow sometimes
const val MAX_PAGES_PER_REQUEST = 5

@Service
class FlibustaTools(private val httpHelper: HttpClientInterface) {

    private val recExtractor = RecommendationsExtractor(httpHelper)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper)
    private val genresExtractor = GenresListExtractor(httpHelper)
    private val searchBookByName = SearchBooksByName(httpHelper)
    private val popularBooksExtractor = PopularBooksExtractor(httpHelper)

    @McpTool(
        name = "flibustaGetGenresList",
        description = "[Flibusta] Get all available genres list",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getGenresList(): McpResponse<List<GenreRef>> = executeWithTimeout {
        genresExtractor.getAllGenres()
    }

    @McpTool(
        name = "flibustaSearchBooksByName",
        description = "[Flibusta] Search books by name and returns their names and IDs",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun searchBooksByName(
        @McpToolParam(
            description = "Book name to search for on Flibusta (required)",
        )
        bookName: String,
    ): McpResponse<List<SearchBookRef>> = executeWithTimeout {
        searchBookByName.searchBooksByName(
            URLEncoder.encode(bookName, StandardCharsets.UTF_8.toString()),
        )
    }

    @McpTool(
        name = "flibustaGetBookInfoByIds",
        description = "[Flibusta] Get book info by book ID",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getBookInfoByIds(
        @McpToolParam(
            description = "List of Flibusta book IDs to fetch (required)",
        )
        bookIds: List<Int>,
    ): McpResponse<List<BookDetails>> = executeWithTimeout {
        bookInfoExtractor.getBookInfoByIds(bookIds)
    }

    @McpTool(
        name = "flibustaGetPopularBooksList",
        description = "[Flibusta] Get top rated books list",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getPopularBooksList(
        @McpToolParam(
            description = "Period (day/week/all) to get popular books for. Default: all.",
            required = false,
        )
        bookIds: List<Int>,
    ): McpResponse<List<BookDetails>> = executeWithTimeout {
        // TODO: implement popular books extractor
        bookInfoExtractor.getBookInfoByIds(bookIds)
    }

    @McpTool(
        name = "flibustaGetRecommendedBooks",
        description = "[Flibusta] Get recommended books paginated (50 items per page, " +
            "max $MAX_PAGES_PER_REQUEST pages per request)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
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

        validateRecommendationsRequest<RecommendationsResponse>(startPageValue, endPageValue)
            ?.let { return it }

        return executeWithTimeout {
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
        description = "[Flibusta] Get recommended authors paginated (50 items per page, " +
            "max $MAX_PAGES_PER_REQUEST pages per request)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
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

        validateRecommendationsRequest<RecommendationsResponse>(startPageValue, endPageValue)
            ?.let { return it }

        return executeWithTimeout {
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

    private fun <T> validateRecommendationsRequest(startPage: Int, endPage: Int): McpResponse<T>? {
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

    private fun <T> executeWithTimeout(block: suspend () -> McpResponse<T>): McpResponse<T> {
        return try {
            val response = runBlocking {
                withTimeout(FETCH_TIMEOUT_MILLIS) { block() }
            }
            response
        } catch (e: TimeoutCancellationException) {
            McpResponse(errors = listOf("Error: timeout after ${FETCH_TIMEOUT_MILLIS}ms"))
        } catch (e: Exception) {
            McpResponse(errors = listOf("Error: ${e.message ?: e::class.simpleName}"))
        }
    }
}
