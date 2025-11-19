package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.mcp.flibusta.extractors.SearchBooksByName
import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

const val MAX_RECOMMENDATIONS = 500
const val DEFAULT_RECOMMENDATIONS = 10
const val FETCH_TIMEOUT_MILLIS: Long = 15 * 1000 // Flibusta can be slow sometimes

@Service
class FlibustaTools(private val httpHelper: HttpClientInterface) {

    private val recExtractor = RecommendationsExtractor(httpHelper)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper)
    private val genresExtractor = GenresListExtractor(httpHelper)
    private val searchBookByName = SearchBooksByName(httpHelper)

    @McpTool(
        name = "flibustaGenresList",
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
        name = "flibustaRecommendedBooks",
        description = "[Flibusta] Get recommended books",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedBooks(
        @McpToolParam(
            description = "Max recommendations (max $MAX_RECOMMENDATIONS). Default: $DEFAULT_RECOMMENDATIONS.",
            required = false,
        )
        recommendationsRequired: Int? = null,
        @McpToolParam(
            description = "Page index (0-based). Default: 0.",
            required = false,
        )
        startPage: Int? = null,
        @McpToolParam(
            description = "Author name filter (optional). Default: none.",
            required = false,
        )
        authorName: String? = null,
        @McpToolParam(
            description = "Genre slugs to filter by (optional). Default: none.",
            required = false,
        )
        genreSlugs: List<String>? = null,
    ): McpResponse {
        val recommendationsRequiredValue = recommendationsRequired ?: DEFAULT_RECOMMENDATIONS
        val startPageValue = startPage ?: 0
        val authorNameValue = URLEncoder.encode(
            authorName ?: "",
            StandardCharsets.UTF_8.toString(),
        )
        val genreSlugsValue = (genreSlugs ?: emptyList()).joinToString(",")

        validateRecommendationsRequest(recommendationsRequiredValue, startPageValue)?.let { return it }
        return executeWithTimeout {
            recExtractor.getRecommendedBooks(
                mapOf(
                    "view" to "books",
                    "srcgenre" to genreSlugsValue,
                    "adata" to "name",
                    "author" to authorNameValue,
                ),
                recommendationsRequiredValue,
                startPageValue,
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendedAuthors",
        description = "[Flibusta] Get recommended authors",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedAuthors(
        @McpToolParam(
            description = "Max recommendations (max $MAX_RECOMMENDATIONS). Default: $DEFAULT_RECOMMENDATIONS.",
            required = false,
        )
        recommendationsRequired: Int? = null,
        @McpToolParam(
            description = "Page index (0-based). Default: 0.",
            required = false,
        )
        startPage: Int? = null,
    ): McpResponse {
        val recommendationsRequiredValue = recommendationsRequired ?: DEFAULT_RECOMMENDATIONS
        val startPageValue = startPage ?: 0
        // TODO: Add genre selection
        validateRecommendationsRequest(recommendationsRequiredValue, startPageValue)?.let { return it }
        return executeWithTimeout {
            recExtractor.getRecommendedAuthors(
                emptyMap(),
                recommendationsRequiredValue,
                startPageValue,
            )
        }
    }

    private fun validateRecommendationsRequest(recommendationsRequired: Int, startPage: Int): McpResponse? {
        if (recommendationsRequired > MAX_RECOMMENDATIONS) {
            return McpResponse(
                errors = listOf("Error: Maximum number of recommendations is $MAX_RECOMMENDATIONS"),
            )
        }
        if (recommendationsRequired <= 0) {
            return McpResponse(
                errors = listOf("Error: Number of recommendations must be greater than 0"),
            )
        }
        if (startPage < 0) {
            return McpResponse(
                errors = listOf("Error: Start page must be 0 or greater"),
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
