package com.github.risboo6909.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun joinKeyValueParams(url: String, params: Map<String, Any?>): String {
    return if (params.isEmpty()) {
        url
    } else {
        val separator = if (url.contains("?")) "&" else "?"
        "$url$separator" + params.entries.joinToString("&") { (key, value) ->
            "${encodeQueryParam(key)}=${encodeQueryParam(value?.toString().orEmpty())}"
        }
    }
}

private fun encodeQueryParam(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())

fun addPagination(url: String, page: Int): String {
    return if (url.contains("?")) {
        "$url&page=$page"
    } else {
        "$url?page=$page"
    }
}
