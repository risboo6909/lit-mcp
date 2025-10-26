package com.github.risboo6909.utils

fun joinParams(url: String, params: Map<String, Any?>): String {
    return if (params.isEmpty()) {
        url
    } else {
        "$url?" + params.entries.joinToString("&") { (key, value) ->
            "$key=$value"
        }
    }
}
