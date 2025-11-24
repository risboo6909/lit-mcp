package com.github.risboo6909.utils

const val DEFAULT_NUMBER_OF_RETRIES = 5

interface HttpClientInterface {

    suspend fun queryGet(url: String, retries: Int = DEFAULT_NUMBER_OF_RETRIES): Result<String>

    suspend fun fetchMultiplePages(
        urls: List<String>,
        retries: Int = DEFAULT_NUMBER_OF_RETRIES,
    ): Pair<List<String>, List<String>>
}
