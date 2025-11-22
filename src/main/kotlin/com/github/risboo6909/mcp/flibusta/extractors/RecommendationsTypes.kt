package com.github.risboo6909.mcp.flibusta.extractors

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendationsResponse(
    val authorRecommendations: List<AuthorRecommendation>? = null,
    val bookRecommendations: List<BookRecommendation>? = null,
    val totalPages: Int? = null,
)

data class AuthorRecommendation(
    val author: AuthorInfo,
    val booksCount: Int,
    val usersCount: Int,
    val recsCount: Int,
)

data class BookRecommendation(
    val authors: List<AuthorInfo>,
    val book: BookInfo,
    val genres: List<GenreInfo>,
    val recommendationsCount: Int,
)
