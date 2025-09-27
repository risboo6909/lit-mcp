package com.github.risboo6909.mcp.litres

import org.springframework.ai.tool.annotation.Tool

class LitResMCP {

    @Tool(name = "search", description = "Search LitRes books by query")
    fun hello(): String = "Привет из Spring AI MCP!"

}