package com.github.risboo6909.mcp.flibusta.extractors

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
    val recommendationsCount: Int
)

data class AuthorRef(
    val id: Int?,
    val name: String,
    val url: String,
    val isTranslator: Boolean
)

data class BookRef(
    val id: Int?,
    val title: String,
    val url: String
)
