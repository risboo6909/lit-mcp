package com.github.risboo6909.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.spec.McpServerTransportProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class StdioTransportConfigurationTest {
    private val runner = ApplicationContextRunner().withUserConfiguration(StdioTransportConfiguration::class.java)

    @Test
    fun disabledServerDoesNotRequireMcpMapper() {
        runner.withPropertyValues("spring.ai.mcp.server.enabled=false", "spring.ai.mcp.server.stdio=true")
            .run { context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(McpServerTransportProvider::class.java)
            }
    }

    @Test
    fun httpModeDoesNotRegisterStdioTransport() {
        runner.withPropertyValues("spring.ai.mcp.server.stdio=false")
            .run { context ->
                assertThat(context).hasNotFailed().doesNotHaveBean(McpServerTransportProvider::class.java)
            }
    }

    @Test
    fun stdioModeRegistersTransportWhenEnabledByDefault() {
        runner.withPropertyValues("spring.ai.mcp.server.stdio=true")
            .withBean("mcpServerObjectMapper", ObjectMapper::class.java, { ObjectMapper() })
            .run { context ->
                assertThat(context).hasNotFailed().hasSingleBean(McpServerTransportProvider::class.java)
            }
    }
}
