package com.github.risboo6909.mcp

data class McpResponse<T>(
    val payload: T? = null,
    val errors: List<String> = listOf(),
)
