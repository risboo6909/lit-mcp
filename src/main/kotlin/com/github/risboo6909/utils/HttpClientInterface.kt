package com.github.risboo6909.utils

interface HttpClientInterface {

    suspend fun queryGet(url: String, retries: Int = 1): String

    suspend fun fetchMultiplePages(urls: List<String>): List<String>

}