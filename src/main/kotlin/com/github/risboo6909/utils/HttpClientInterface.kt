package com.github.risboo6909.utils

interface HttpClientInterface {

    suspend fun queryGet(url: String): String

}