package com.github.risboo6909.utils

interface HttpHelperInterface {

    suspend fun queryGet(url: String): String

}