package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
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

const val FETCH_TIMEOUT_MILLIS: Long = 15 * 1000 // Flibusta can be slow sometimes

@Service
class FlibustaTools(private val httpHelper: HttpClientInterface) {

    private val recExtractor = RecommendationsExtractor(httpHelper)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper)
    private val genresExtractor = GenresListExtractor(httpHelper)
    private val searchBookByName = SearchBooksByName(httpHelper)

    @McpTool(
        name = "flibustaGetGenresList",
        description = "[Flibusta] Get all available genres list",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getGenresList(): McpResponse = executeWithTimeout {
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
    ): McpResponse = executeWithTimeout {
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
    ): McpResponse = executeWithTimeout {
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
    ): McpResponse = executeWithTimeout {
        // TODO
        bookInfoExtractor.getBookInfoByIds(bookIds)
    }

    @McpTool(
        name = "flibustaGetRecommendedBooks",
        description = "[Flibusta] Get recommended books paginated (50 items per page)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedBooks(
        @McpToolParam(
            description = "Start page index (0-based). Default: 0.",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "End page index (0-based, exclusive). Default: no limit.",
            required = false,
        )
        endPage: Int? = null,
        @McpToolParam(
            description = "Author name filter (optional). Default: 1.",
            required = false,
        )
        authorName: String? = null,
        @McpToolParam(
            description = "Genre slugs to filter by (optional). Default: none.",
            required = false,
        )
        genreSlugs: List<String>? = null,
    ): McpResponse {
        val startPageValue = startPage ?: 0
        val endPageValue = endPage ?: 1
        val authorNameValue = URLEncoder.encode(
            authorName ?: "",
            StandardCharsets.UTF_8.toString(),
        )
        val genreSlugsValue = joinListParams(genreSlugs, ",")

        validateRecommendationsRequest(startPageValue, endPageValue)?.let { return it }
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
        description = "[Flibusta] Get recommended authors paginated (50 items per page)",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedAuthors(
        @McpToolParam(
            description = "Page index (0-based). Default: 0.",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "End page index (0-based, exclusive). Default: 1.",
            required = false,
        )
        endPage: Int? = null,
        @McpToolParam(
            description = "Genre slugs to filter by (optional). Default: none.",
            required = false,
        )
        genreSlugs: List<String>? = null,
    ): McpResponse {
        val startPageValue = startPage ?: 0
        val endPageValue = endPage ?: 1
        val genreSlugsValue = joinListParams(genreSlugs, ",")

        validateRecommendationsRequest(startPageValue, endPageValue)?.let { return it }
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

    private fun validateRecommendationsRequest(startPage: Int, endPage: Int): McpResponse? {
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
        return null
    }

    private fun <T> executeWithTimeout(block: suspend () -> T): McpResponse {
        return try {
            val payload = runBlocking {
                withTimeout(FETCH_TIMEOUT_MILLIS) { block() }
            }
            McpResponse(payload = payload)
        } catch (e: TimeoutCancellationException) {
            McpResponse(errors = listOf("Error: timeout after ${FETCH_TIMEOUT_MILLIS}ms"))
        } catch (e: Exception) {
            McpResponse(errors = listOf("Error: ${e.message ?: e::class.simpleName}"))
        }
    }
}
