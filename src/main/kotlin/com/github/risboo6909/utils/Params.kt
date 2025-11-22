package com.github.risboo6909.utils

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

fun joinKeyValueParams(url: String, params: Map<String, Any?>): String {
    return if (params.isEmpty()) {
        url
    } else {
        "$url?" + params.entries.joinToString("&") { (key, value) ->
            "$key=$value"
        }
    }
}

fun joinListParams(xs: List<String>?, separator: String): String {
    return (xs ?: listOf()).joinToString(separator) { item ->
        URLEncoder.encode(item, StandardCharsets.UTF_8.toString())
    }
}

fun addPagination(url: String, page: Int): String {
    return if (url.contains("?")) {
        url + "&page=$page"
    } else {
        url + "?page=$page"
    }
}
