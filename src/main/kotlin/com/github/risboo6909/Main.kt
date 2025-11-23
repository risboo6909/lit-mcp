package com.github.risboo6909

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.stereotype.Component

@SpringBootApplication
class Application {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val command = object : CliktCommand(name = "lit-mcp") {
                private val transport by option("--transport", help = "stdio | http")
                    .choice("stdio", "http").default("stdio")

                private val port by option("--port", help = "HTTP port").int().default(8080)

                override fun run() {
                    val args = mutableListOf<String>()
                    val useStdio = transport == "stdio"

                    if (useStdio) {
                        System.err.println("[lit-mcp] Starting in STDIO mode")
                        // STDIO MCP server: no HTTP server, just stdin/stdout
                        args += "--spring.main.web-application-type=none"
                        args += "--spring.ai.mcp.server.stdio=true"
                    } else {
                        System.err.println("[lit-mcp] Starting in HTTP mode on port $port")
                        // HTTP/SSE MCP server: normal web application on the given port
                        args += "--spring.ai.mcp.server.stdio=false"
                        args += "--server.port=$port"
                        args += "--spring.ai.mcp.server.protocol=STREAMABLE"
                    }

                    runApplication<Application>(*args.toTypedArray())
                }
            }
            command.main(args)
        }
    }
}

@Component
class McpStdioRunner : CommandLineRunner {

    @Value("\${spring.ai.mcp.server.stdio:false}")
    private val stdio: Boolean = false

    override fun run(args: Array<String>) {
        if (stdio) {
            while (true) {
                Thread.sleep(1000)
            }
        }
    }
}
