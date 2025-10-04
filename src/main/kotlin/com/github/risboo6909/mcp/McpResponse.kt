package com.github.risboo6909.mcp

data class McpResponse(
    val success: Boolean,
    val message: String? = null,
    val payload: Any? = null
)