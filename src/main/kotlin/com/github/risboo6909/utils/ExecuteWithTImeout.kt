package com.github.risboo6909.utils

import com.github.risboo6909.mcp.McpResponse
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun <T> executeWithTimeout(timeoutMillis: Long, block: suspend () -> McpResponse<T>): McpResponse<T> {
    return try {
        val response = runBlocking {
            withTimeout(timeoutMillis) { block() }
        }
        response
    } catch (e: TimeoutCancellationException) {
        McpResponse(errors = listOf("Error: timeout after ${timeoutMillis}ms"))
    } catch (e: Exception) {
        McpResponse(errors = listOf("Error: ${e.message ?: e::class.simpleName}"))
    }
}
