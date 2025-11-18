package com.github.risboo6909.mcp

data class McpResponse(
    val payload: Any? = null,
    val errors: List<String> = listOf(),
)