package com.github.risboo6909.mcp.flibusta

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.github.risboo6909.utils.HttpClientInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.stream.Stream

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "spring.ai.mcp.server.stdio=false",
        "spring.ai.mcp.server.protocol=STREAMABLE",
    ],
)
@Import(FlibustaMcpArgumentsTest.FixtureConfig::class)
class FlibustaMcpArgumentsTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var mapper: ObjectMapper

    private val client = HttpClient.newHttpClient()
    private var session: String? = null

    @BeforeEach
    fun initialize() {
        rpc(
            "initialize",
            mapOf(
                "protocolVersion" to "2024-11-05",
                "capabilities" to emptyMap<String, Any>(),
                "clientInfo" to mapOf("name" to "argument-regression-test", "version" to "1"),
            ),
        )
        rpc("notifications/initialized", emptyMap(), notification = true)
    }

    @ParameterizedTest(name = "{0}: optional flag = {3}")
    @MethodSource("toolArguments")
    fun optionalBooleansWorkThroughMcp(
        tool: String,
        requiredArguments: Map<String, Any>,
        flag: String,
        value: String,
    ) {
        val arguments = requiredArguments.toMutableMap<String, Any?>()
        if (value != "omitted") arguments[flag] = if (value == "null") null else value.toBooleanStrict()

        val response = rpc("tools/call", mapOf("name" to tool, "arguments" to arguments))!!
        assertFalse(response.has("error"), response.toString())
        val result = response.path("result")
        assertFalse(result.path("isError").asBoolean(), result.toString())
        val payload = mapper.readTree(result.path("content")[0].path("text").asText())
        assertEquals(0, payload.path("errors").size(), payload.toString())
        val book = payload.path("payload")[0]
        assertEquals(46382, book.path("id").asInt())
        val optionalField = book.path(if (flag == "includeDiscussions") "discussions" else "description")
        if (value == "true") {
            if (flag == "includeDiscussions") {
                assertEquals("Fixture discussion", optionalField[0].asText())
            } else {
                assertEquals("Fixture description", optionalField.asText())
            }
        } else {
            assertTrue(optionalField.isNull || optionalField.isMissingNode, book.toString())
        }
    }

    private fun rpc(method: String, params: Map<String, Any?>, notification: Boolean = false): JsonNode? {
        val message = mutableMapOf<String, Any>("jsonrpc" to "2.0", "method" to method, "params" to params)
        if (!notification) message["id"] = 1
        val request = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/mcp"))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
        session?.let { request.header("Mcp-Session-Id", it) }
        val response = client.send(
            request.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(message))).build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertTrue(response.statusCode() in 200..299, response.body())
        response.headers().firstValue("Mcp-Session-Id").ifPresent { session = it }
        if (notification) return null
        val body = response.body()
        val data = body.lineSequence().firstOrNull { it.startsWith("data:") }?.removePrefix("data:") ?: body
        return mapper.readTree(data)
    }

    @TestConfiguration
    class FixtureConfig {
        @Bean
        @Primary
        fun fixtureHttpClient(): HttpClientInterface = object : HttpClientInterface {
            override suspend fun queryGet(url: String, retries: Int): Result<String> = Result.success(OPDS)

            override suspend fun fetchMultiplePages(
                urls: List<String>,
                retries: Int,
            ): Pair<List<String>, List<String>> = urls.map { if (it.contains("/opds/")) OPDS else BOOK } to emptyList()
        }
    }

    companion object {
        private const val OPDS = """
            <feed xmlns="http://www.w3.org/2005/Atom"><entry>
            <title>Fixture book</title><link href="/b/46382" rel="alternate"/>
            <content type="text/html">Fixture description</content>
            </entry></feed>
        """
        private const val BOOK = """
            <html><body><h1 class="title">Fixture book</h1>
            <span class="container_1">Fixture discussion</span></body></html>
        """

        @JvmStatic
        fun toolArguments(): Stream<Arguments> = listOf(
            Triple("flibustaGetBookInfoByIds", mapOf("bookIds" to listOf(46382)), "includeDiscussions"),
            Triple("flibustaSearchBooksByName", mapOf("bookName" to "Fixture"), "includeDescription"),
            Triple("flibustaGetNewBooks", emptyMap(), "includeDescription"),
            Triple("flibustaGetBooksByAuthorId", mapOf("authorId" to 10395), "includeDescription"),
        ).flatMap { (tool, arguments, flag) ->
            listOf("omitted", "false", "true", "null").map { Arguments.of(tool, arguments, flag, it) }
        }.stream()
    }
}
