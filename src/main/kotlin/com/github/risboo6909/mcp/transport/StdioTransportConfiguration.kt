package com.github.risboo6909.mcp.transport

import com.fasterxml.jackson.databind.ObjectMapper
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider
import io.modelcontextprotocol.spec.McpServerSession
import io.modelcontextprotocol.spec.McpServerTransportProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    prefix = "spring.ai.mcp.server",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class StdioTransportConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = ["stdio"], havingValue = "true")
    fun serializedStdioTransport(
        @Qualifier("mcpServerObjectMapper") mapper: ObjectMapper,
    ): McpServerTransportProvider = object : StdioServerTransportProvider(JacksonMcpJsonMapper(mapper)) {
        override fun setSessionFactory(sessionFactory: McpServerSession.Factory) {
            super.setSessionFactory { transport -> sessionFactory.create(SerializingMcpTransport(transport)) }
        }
    }
}
