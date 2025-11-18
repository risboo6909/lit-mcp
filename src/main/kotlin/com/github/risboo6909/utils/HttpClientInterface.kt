package com.github.risboo6909.utils

const val DEFAULT_NUMBER_OF_RETRIES = 3

interface HttpClientInterface {

    suspend fun queryGet(url: String, retries: Int = DEFAULT_NUMBER_OF_RETRIES): String

    suspend fun fetchMultiplePages(urls: List<String>): List<String>
}
