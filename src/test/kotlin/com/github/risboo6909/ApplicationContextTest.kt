package com.github.risboo6909

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.main.web-application-type=none",
        "spring.ai.mcp.server.stdio=true",
    ],
)
class ApplicationContextTest {

    @Test
    fun contextLoads() = Unit
}
