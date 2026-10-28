package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.MirrorManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FlibustaMirrorConfiguration {
    @Bean
    fun flibustaMirrorManager(@Value("\${lit-mcp.flibusta-mirrors}") mirrorConfig: String): MirrorManager =
        MirrorManager(
            mirrors = mirrorConfig.split(',').map { it.trim() },
            sourceOrigins = setOf("https://flibusta.is"),
        )
}
