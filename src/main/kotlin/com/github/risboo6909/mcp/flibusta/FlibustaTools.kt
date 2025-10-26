package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.mcp.flibusta.extractors.BookInfoExtractor
import com.github.risboo6909.mcp.flibusta.extractors.NO_PAGE_LIMIT
import com.github.risboo6909.mcp.flibusta.extractors.RecommendationsExtractor
import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

const val MAX_RECOMMENDATIONS = 500
const val FETCH_TIMEOUT_MILLIS: Long = 5 * 1000

@Service
class FlibustaTools(private val httpHelper: HttpClientInterface) {
//
//    @McpTool(name = "flibustaSearchByBookName", description = "[Flibusta] Search books by names")
//    fun search(): String = runBlocking {
//        val rawHtml = httpHelper.queryGet("https://flibusta.is/booksearch?ask=name&chb=on")
//        val result = parseSearchResults(rawHtml)
//        result.toString()
//    }
//
//    @McpTool(name = "flibustaGenresList", description = "Get Flibusta genres")
//    fun genres(): String = runBlocking {
//        val rawHtml = httpHelper.queryGet("https://flibusta.is/g")
//        //val result = parser.parseGenres(rawHtml)
//        //result.toString()
//        "Not implemented yet"
//    }

    private val recExtractor = RecommendationsExtractor(httpHelper)
    private val bookInfoExtractor = BookInfoExtractor(httpHelper)

    // -----
    @McpTool(
        name = "flibustaGetBookInfoById",
        description = "[Flibusta] Get book info by book ID",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true
        ))
    fun getBookInfoById(
        @McpToolParam(
            description = "Book ID from Flibusta",
        )
        bookId: Int,
    ): McpResponse {
        return McpResponse(
            true,
            "OK",
            runBlocking {
                withTimeout(FETCH_TIMEOUT_MILLIS) {
                    bookInfoExtractor.getBookInfoById(bookId)
                }
            }
        )
    }

    @McpTool(
        name = "flibustaGetRecommendedBooksByAuthor",
        description = "[Flibusta] Get recommended books by author",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true
        ))
    fun getRecommendedBooksByAuthor(
        @McpToolParam(
            description = "Author ID from Flibusta",
        )
        authorId: Int,
    ): McpResponse {

        if (authorId <= 0) {
            return McpResponse(
                false,
                "Error: Author ID must be greater than 0"
            )
        }

        return runBlocking {
            McpResponse(
                success = true,
                "OK",
                withTimeout(FETCH_TIMEOUT_MILLIS) {
                    recExtractor.getRecommendedBooks(
                        mapOf(
                            "view" to "books",
                            "adata" to "id",
                            "author" to authorId.toString()
                        ),
                        NO_PAGE_LIMIT
                    )
                }
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendationBooks",
        description = "[Flibusta] Get recommended books",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true
        ))
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

        return runBlocking {
            McpResponse(
                success = true,
                "OK",
                withTimeout(FETCH_TIMEOUT_MILLIS) {
                    recExtractor.getRecommendedBooks(
                        mapOf(
                            "view" to "books",
                        ),
                        recommendationsRequired,
                        startPage,
                    )
                }
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendationsAuthor",
        description = "[Flibusta] Get recommended authors",
        annotations = McpTool.McpAnnotations(
            readOnlyHint = true,
            destructiveHint = false,
            idempotentHint = true
        ))
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

        return runBlocking {
            McpResponse(
                success = true,
                "OK",
                withTimeout(FETCH_TIMEOUT_MILLIS) {
                    recExtractor.getRecommendedAuthors(
                        emptyMap(),
                        recommendationsRequired,
                        startPage,
                    )
                }
            )
        }
    }

    private fun validateRecommendationsRequest(recommendationsRequired: Int, startPage: Int): McpResponse? {
        if (recommendationsRequired > MAX_RECOMMENDATIONS) {
            return McpResponse(
                false,
                "Error: Maximum number of recommendations is $MAX_RECOMMENDATIONS"
            )
        }
        if (recommendationsRequired <= 0) {
            return McpResponse(
                false,
                "Error: Number of recommendations must be greater than 0"
            )
        }
        if (startPage < 0) {
            return McpResponse(
                false,
                "Error: Start page must be 0 or greater"
            )
        }
        return null
    }

}