package com.github.risboo6909.mcp.flibusta.extractors

import com.github.risboo6909.mcp.McpResponse
import com.github.risboo6909.utils.HttpClientInterface
import org.jsoup.Jsoup

class PopularBooksExtractor(
    private val httpHelper: HttpClientInterface,
    private val bookInfoExtractor: BookInfoExtractor,
) {

    suspend fun getPopularBooks(
        period: PopularBooksPeriod,
        startPage: Int,
        endPage: Int,
        genreNames: Set<String> = emptySet(),
        includeGenreBreakdown: Boolean = false,
    ): McpResponse<PopularBooksResponse> {
        val url = "$POPULAR_BOOKS_URL/${period.suffix}"
        val (totalPages, pagerError) = getTotalPages(
            url,
            mapOf(),
            httpHelper,
        )
        val (popularBooks, errors) = getWithPaginationParallel(
            url,
            httpHelper,
            ::parse,
            mapOf(),
            startPage,
            endPage,
        )

        if (genreNames.isEmpty() && !includeGenreBreakdown) {
            return McpResponse(
                PopularBooksResponse(
                    popularBooks,
                    totalPages,
                ),
                errors = errors + pagerError,
            )
        }

        val processedBooksResponse = enrichAndFilterByGenres(popularBooks, genreNames)
        val filteredBooks = processedBooksResponse.payload.orEmpty()

        return McpResponse(
            PopularBooksResponse(
                filteredBooks,
                totalPages,
                genreBreakdown = if (includeGenreBreakdown) buildGenreBreakdown(filteredBooks) else null,
            ),
            errors = errors + pagerError + processedBooksResponse.errors,
        )
    }

    private suspend fun enrichAndFilterByGenres(
        popularBooks: List<PopularBook>,
        genreNames: Set<String>,
    ): McpResponse<List<PopularBook>> {
        val bookIds = popularBooks.mapNotNull { it.book?.id }.distinct()
        val genresResponse = bookInfoExtractor.getGenresByBookIds(bookIds)
        val genresByBookId = genresResponse.payload.orEmpty()
        val enrichedBooks = popularBooks.map { popularBook ->
            popularBook.copy(
                genres = popularBook.book?.id?.let { genresByBookId[it] }.orEmpty(),
            )
        }
        val normalizedGenreNames = genreNames.mapTo(mutableSetOf()) {
            normalizeGenreName(it)
        }
        val filteredBooks = if (normalizedGenreNames.isEmpty()) {
            enrichedBooks
        } else {
            enrichedBooks.filter { popularBook ->
                popularBook.genres.orEmpty().any { genre ->
                    normalizeGenreName(genre.name) in normalizedGenreNames
                }
            }
        }

        return McpResponse(
            payload = filteredBooks,
            errors = genresResponse.errors,
        )
    }

    private fun buildGenreBreakdown(popularBooks: List<PopularBook>): List<PopularBooksGenreCount> = popularBooks
        .flatMap { it.genres.orEmpty() }
        .groupBy { normalizeGenreName(it.name) }
        .values
        .map { genres ->
            PopularBooksGenreCount(
                genre = genres.first(),
                booksCount = genres.size,
            )
        }
        .sortedWith(compareByDescending<PopularBooksGenreCount> { it.booksCount }.thenBy { it.genre.name })

    private fun parse(rawHtml: String, baseUrl: String = FLIBUSTA_BASE_URL): List<PopularBook> {
        val doc = Jsoup.parse(rawHtml, baseUrl)

        val listItems =
            doc.select("h1.title + ul > li").ifEmpty {
                doc.select("h1.title ~ ul > li")
            }

        return listItems.mapNotNull { li ->
            val bookLink = li.selectFirst("a[href^=/b/]") ?: return@mapNotNull null
            val book = extractBookInfo(bookLink)

            val authors = li.select("a[href^=/a/]").map { a ->
                extractAuthorInfo(a, false)
            }

            PopularBook(
                book = book,
                authors = authors,
            )
        }
    }
}
