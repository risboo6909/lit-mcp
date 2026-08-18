# lit-mcp agent guide

## Project overview

`lit-mcp` is a Kotlin/Spring Boot MCP server that exposes structured, read-only tools for discovering and inspecting books on public book websites. It supports both stdio and HTTP transports. The project scrapes HTML because the supported sites do not provide the required public APIs.

The application targets Java 21 and is built with Gradle. Spring AI provides the MCP server integration, Ktor handles outbound HTTP, and Jsoup parses source pages. `README.md` is the source of truth for user-facing installation and usage instructions.

## Architecture

- The application entry point selects the transport and starts Spring.
- MCP tool services define the public tool contracts, validate inputs, apply operation-level timeouts, and coordinate extraction.
- Extractors fetch and parse remote pages into typed response models. Keep site-specific parsing in this layer.
- Shared utilities own HTTP behavior, retries, concurrency limits, parameter handling, logging, and timeout execution.
- Unit tests normally mock the HTTP interface and use small representative HTML fixtures. A Spring context test protects dependency-injection wiring; external integration tests must tolerate the site being unavailable.

Prefer these boundaries over depending on current class names. New sources and tools should follow the same separation.

## Development rules

- Keep MCP tools read-only unless a feature explicitly requires otherwise.
- In stdio mode, stdout is reserved for JSON-RPC. Send application logs and diagnostics to stderr.
- Validate user input before issuing network requests and return failures through the project's MCP response type.
- Treat remote HTML as unreliable: tolerate missing optional elements, preserve partial results where useful, and cover parser changes with fixtures.
- Use the shared HTTP abstraction instead of creating ad hoc clients. Respect existing retry, concurrency, pagination, and timeout limits.
- Preserve inexpensive fast paths. Avoid fetching every book page when the requested response does not need detailed data.
- Prefer server configuration properties or environment variables for operational settings rather than adding infrastructure controls to every tool call.
- Keep changes focused and preserve unrelated work. Do not change MCP registrations, installed JARs, releases, or remote state unless the user explicitly asks.
- When replacing an installed JAR, build and validate the new artifact first, retain a recoverable backup, and verify the installed artifact with an MCP handshake.

## Verification

For normal changes, run:

```bash
./gradlew spotlessCheck test
```

Also run `./gradlew bootJar` and smoke-test the produced JAR after changes to Spring wiring, configuration, startup, transport handling, packaging, or runtime dependencies. Parser changes should include focused fixture-based tests. Do not require live network access for the regular test suite.
