package com.github.risboo6909.mcp.flibusta.extractors

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RecommendationsResponse(
    val authorRecommendations: List<AuthorRecommendation>? = null,
    val bookRecommendations: List<BookRecommendation>? = null,
    val isLastPage: Boolean = false,
)

data class AuthorRecommendation(
    val author: AuthorRef,
    val booksCount: Int,
    val usersCount: Int,
    val recsCount: Int,
)

data class BookRecommendation(
    val authors: List<AuthorRef>,
    val book: BookRef,
    val genres: List<GenreRef>,
    val recommendationsCount: Int,
)
