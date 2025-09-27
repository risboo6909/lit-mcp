package com.github.risboo6909.mcp.flibusta

import org.springframework.ai.tool.annotation.Tool
import org.springframework.stereotype.Service

@Service
class Flibusta(parser: Parser) {

    @Tool(name = "search", description = "Search Flibusta books by query")
    fun search(): String {

        return "123"
    }

}