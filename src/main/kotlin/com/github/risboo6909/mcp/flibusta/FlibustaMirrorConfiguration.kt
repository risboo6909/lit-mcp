package com.github.risboo6909.mcp.flibusta

import com.github.risboo6909.utils.MirrorManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// These origins expose metadata and discussions, but never book files.
internal val FLIBUSTA_CATALOG_ONLY_ORIGINS = setOf("https://flisland.net", "http://flisland.net")

@Configuration
class FlibustaMirrorConfiguration {
    @Bean
    fun flibustaMirrorManager(@Value("\${lit-mcp.flibusta-mirrors}") mirrorConfig: String): MirrorManager =
        MirrorManager(
            mirrors = mirrorConfig.split(',').map { it.trim() },
            sourceOrigins = setOf("https://flibusta.is"),
        )
}
