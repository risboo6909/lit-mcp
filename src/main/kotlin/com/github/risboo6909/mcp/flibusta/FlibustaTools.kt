package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.GenresListExtractor
import com.github.risboo6909.mcp.flibusta.extractors.NO_PAGE_LIMIT
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
    )
    fun getGenresList(): McpResponse = executeWithTimeout {
        genresExtractor.getAllGenres()
    }

    @McpTool(
        name = "flibustaSearchBooksByName",
        description = "[Flibusta] Search books by name and returns their names and IDs",
    )
    fun searchBooksByName(
        @McpToolParam(
            description = "Book name to search for on Flibusta",
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
            description = "Book ID from Flibusta",
        )
        bookIds: List<Int>,
    ): McpResponse = executeWithTimeout {
        bookInfoExtractor.getBookInfoByIds(bookIds)
    }

    @McpTool(
        name = "flibustaGetRecommendedBooksByAuthor",
        description = "[Flibusta] Get recommended books by author",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun getRecommendedBooksByAuthor(
        @McpToolParam(
            description = "Author ID from Flibusta",
        )
        authorId: Int,
    ): McpResponse {
        if (authorId <= 0) {
            return McpResponse(errors = listOf("Error: Author ID must be greater than 0"))
        }
        return executeWithTimeout {
            recExtractor.getRecommendedBooks(
                mapOf(
                    "view" to "books",
                    "adata" to "id",
                    "author" to authorId.toString(),
                ),
                NO_PAGE_LIMIT,
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendationBooks",
        description = "[Flibusta] Get recommended books",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun recommendationsByBook(
        @McpToolParam(
            description = "Maximum number of recommendations to return (maximum $MAX_RECOMMENDATIONS), default is 10",
        )
        recommendationsRequired: Int,
        @McpToolParam(
            description = "Start from specific page, default is 0 (first page)",
        )
        startPage: Int,
    ): McpResponse {
        validateRecommendationsRequest(recommendationsRequired, startPage)?.let { return it }
        return executeWithTimeout {
            recExtractor.getRecommendedBooks(
                mapOf(
                    "view" to "books",
                ),
                recommendationsRequired,
                startPage,
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendationsAuthor",
        description = "[Flibusta] Get recommended authors",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true,
        ),
    )
    fun recommendationsByAuthor(
        @McpToolParam(
            description = "Maximum number of recommendations to return (maximum $MAX_RECOMMENDATIONS), default is 10",
        )
        recommendationsRequired: Int,
        @McpToolParam(
            description = "Start from specific page, default is 0 (first page)",
        )
        startPage: Int,
    ): McpResponse {
        validateRecommendationsRequest(recommendationsRequired, startPage)?.let { return it }
        return executeWithTimeout {
            recExtractor.getRecommendedAuthors(
                emptyMap(),
                recommendationsRequired,
                startPage,
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
