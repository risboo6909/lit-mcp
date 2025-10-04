package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import kotlinx.coroutines.runBlocking
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service

const val MAX_RECOMMENDATIONS = 500

@Service
class FlibustaTools(private val httpHelper: HttpClientInterface) {

    @McpTool(name = "flibustaSearch", description = "Search Flibusta books by query")
    fun search(): String = runBlocking {
        val rawHtml = httpHelper.queryGet("https://flibusta.is/s/лавкрафт")
        val result = parseSearchResults(rawHtml)
        result.toString()
    }

    @McpTool(name = "flibustaGenresList", description = "Get Flibusta genres")
    fun genres(): String = runBlocking {
        val rawHtml = httpHelper.queryGet("https://flibusta.is/g")
        //val result = parser.parseGenres(rawHtml)
        //result.toString()
        "Not implemented yet"
    }

    // -----

    @McpTool(
        name = "flibustaRecommendationsByBook",
        description = "Get Flibusta recommendations by book",
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
    ): McpResponse {

        validateRecommendationsRequest(recommendationsRequired)?.let { return it }

        return runBlocking {
            McpResponse(
                success = true,
                "OK",
                getRecommendations(
                    httpHelper,
                    ::parseRecommendationsByBook,
                    "https://flibusta.is/rec?view=books",
                    recommendationsRequired
                ),
            )
        }
    }

    @McpTool(
        name = "flibustaRecommendationsByAuthor",
        description = "Get Flibusta recommendations by author",
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
    ): McpResponse {

        validateRecommendationsRequest(recommendationsRequired)?.let { return it }

        return runBlocking {
            McpResponse(
                success = true,
                "OK",
                getRecommendations(
                    httpHelper,
                    ::parseRecommendationsByAuthor,
                    "https://flibusta.is/rec",
                    recommendationsRequired
                ),
            )
        }
    }

    private fun validateRecommendationsRequest(recommendationsRequired: Int): McpResponse? {
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
        return null
    }

}