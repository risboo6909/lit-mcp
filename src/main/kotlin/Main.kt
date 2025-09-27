package com.github.risboo6909

import org.springframework.boot.autoconfigure.SpringBootApplication
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.int
import org.springframework.boot.runApplication

@SpringBootApplication
class Application: CliktCommand(name = "lit-mcp") {
    private val transport by option("--transport", help = "stdio | http")
        .choice("stdio", "http").default("stdio")

    private val port by option("--port", help = "HTTP port").int().default(8080)
    private val profile by option("--profile", help = "Spring profile (e.g. http|stdio)")

    override fun run() {
        val springArgs = mutableListOf<String>()

        val useStdio = (transport == "stdio")
        springArgs += "--spring.ai.mcp.server.stdio=$useStdio"
        springArgs += "--spring.ai.mcp.server.protocol=" + if (useStdio) "NONE" else "SSE"

        springArgs += "--server.port=$port"

        profile?.let { springArgs += "--spring.profiles.active=$it" }

        runApplication<Application>(*springArgs.toTypedArray())
    }
}

fun main(args: Array<String>) = Application().main(args)
