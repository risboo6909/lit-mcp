# lit-mcp

**DISCLAIMER: This project is not affiliated with or endorsed by any book websites. 
Use it responsibly and respect copyright laws.**

**lit-mcp** allows LLMs to work with popular books websites (currently only **Flibusta**). Initially I started this project to
learn some basic SpringAI and MCP concepts, but it turned out to be quite useful.

I started with **Flibusta** because it is one of the largest free book repositories. The project uses Flibusta's OPDS catalog
where it provides the required data and parses website pages for features that are not exposed through OPDS.

Also, I chose SpringAI and Kotlin due to these technologies being new to me, so this project served as well as a learning experience.

In 2025, I started this project partly to see whether AI could build a non-trivial application. At that point it was
useful for boilerplate and suggestions, but substantial implementation and supervision were still required. By 2026,
coding agents had become capable of implementing, testing, and maintaining changes across the project end to end, so
the original experiment had effectively succeeded, with product and architecture decisions still guided by a human.

## Token efficiency

`lit-mcp` filters and normalizes Flibusta responses before they enter the model context. A benchmark run on
September 22, 2026 compared each complete Flibusta HTTP response with the exact MCP result for the same request.
Token counts use `tiktoken` 0.14.0 with the `o200k_base` encoding.

| Scenario | Direct Flibusta response | MCP result | Reduction |
| --- | ---: | ---: | ---: |
| Search for `Ложная слепота`, up to 20 books, descriptions disabled | 3,188 | 807 | 74.7% |
| First 20 books for author ID `33386`, descriptions disabled | 22,052 | 6,269 | 71.6% |
| Details for book ID `315739`, discussions disabled | 40,809 | 389 | 99.0% |
| **Combined** | **66,049** | **7,465** | **88.7%** |

For these cases, the MCP payload was **8.85 times smaller**. This benchmark measures response payloads only; total
agent token usage also depends on prompts, tool schemas, reasoning, and whether a browser preprocesses or truncates a
page before returning it to the model.

## Features and limitations

`lit-mcp` supports both `stdio` and `HTTP` modes. `stdio` mode is useful to run the MCP server locally and connect
it to clients that support `stdio` MCPs (such as Codex, Claude Code, or local LLMs running under LM Studio).

Only books in Russian language are supported at the moment, since **Flibusta** mostly contains russian books.

Project currently supports the following set of tools:

- `flibustaGetGenresList`: Get all available genres list
- `flibustaSearchBooksByName`: Search up to 50 books across Flibusta OPDS result pages and return catalog metadata and download links; descriptions are opt-in
- `flibustaGetNewBooks`: Get books added during the current weekly Flibusta catalog window; descriptions are opt-in
- `flibustaSearchAuthorsByName`: Search the Flibusta OPDS catalog for authors
- `flibustaGetBooksByAuthorId`: Get an author's books in alphabetical order by Flibusta author ID; descriptions are opt-in
- `flibustaGetBookInfoByIds`: Get detailed book info for up to 50 book IDs; user discussions are opt-in and limited to 5 per book by default, with a configurable maximum of 20
- `flibustaGetPopularBooksList`: Get pages from the overall popular-books ranking
- `flibustaGetRecommendedBooks`: Get books ranked by user recommendations, optionally filtered by author or genre, with an optional result limit of up to 50 books
- `flibustaRecommendedAuthors`: Get recommended authors paginated (50 items per page)

For more information, please check the source code and the tool definitions.

## Usage

Please make sure you have Java 21 or newer installed on your system. Kotlin and Gradle are not required to run the
prebuilt application.

### Automatic installation

The installer downloads the latest JAR, verifies its checksum, stores it in the user data directory, and registers the
MCP server through the selected client's CLI.

On macOS or Linux:

```bash
curl -fsSL https://github.com/risboo6909/lit-mcp/releases/latest/download/install.sh | sh -s -- TARGET
```

Windows PowerShell:

```powershell
& ([scriptblock]::Create((irm https://github.com/risboo6909/lit-mcp/releases/latest/download/install.ps1))) TARGET
```

Replace `TARGET` with one of these values:

- `codex` — configure Codex CLI, IDE extension, and app
- `claude` — configure Claude Code
- `all` — configure both Codex and Claude Code

The PowerShell form runs the downloaded installer in the current session and does not require changing the execution
policy.

To override the runtime timeouts, set one or both variables on the installer process. The installer validates and saves
them in the selected client configuration:

```bash
curl -fsSL https://github.com/risboo6909/lit-mcp/releases/latest/download/install.sh |
  LIT_MCP_TOOL_TIMEOUT_MILLIS=600000 \
  LIT_MCP_HTTP_REQUEST_TIMEOUT_MILLIS=30000 \
  sh -s -- TARGET
```

```powershell
$env:LIT_MCP_TOOL_TIMEOUT_MILLIS = "600000"
$env:LIT_MCP_HTTP_REQUEST_TIMEOUT_MILLIS = "30000"
& ([scriptblock]::Create((irm https://github.com/risboo6909/lit-mcp/releases/latest/download/install.ps1))) TARGET
```

### Get the JAR manually

Download `lit-mcp.jar` from the [latest GitHub release](https://github.com/risboo6909/lit-mcp/releases/latest), or run:

```bash
curl -L https://github.com/risboo6909/lit-mcp/releases/latest/download/lit-mcp.jar -o lit-mcp.jar
```

Alternatively, build the JAR from source:

```bash
make build
```

The built JAR is written to `build/libs`. On Windows, use `gradlew.bat bootJar` instead of `make build`.

### Configure a client

Use these instructions after downloading or building the JAR, or when you want to manage the MCP configuration
yourself.

#### Codex

Register the JAR, then restart Codex:

```bash
codex mcp add lit -- java -jar /absolute/path/to/lit-mcp.jar --transport=stdio
```

Replace `/absolute/path/to/lit-mcp.jar` with the JAR location.

#### Claude Code

Register the JAR for your user account, then restart Claude Code:

```bash
claude mcp add --transport stdio --scope user lit -- \
  java -jar /absolute/path/to/lit-mcp.jar --transport=stdio
```

### Run standalone

Run a downloaded or built JAR in `stdio` mode:

```bash
java -jar lit-mcp.jar --transport=stdio
```

Or run it in HTTP mode:

```bash
java -jar lit-mcp.jar --transport=http
```

Flibusta can occasionally respond slowly. The defaults are 300 seconds for a complete MCP tool call and 15 seconds for
an individual HTTP request. Override them with environment variables when starting the server:

```bash
LIT_MCP_TOOL_TIMEOUT_MILLIS=600000 \
LIT_MCP_HTTP_REQUEST_TIMEOUT_MILLIS=30000 \
java -jar lit-mcp.jar --transport=stdio
```

## Contributing

Contributions are welcome! If you find any issues or have suggestions for improvements, please feel free to open an issue 
or submit a pull request.

## Future plans

- Make this server available via public HTTP endpoint so that it can be used without running locally
- Add support for more book websites (including English language ones)
- Improve existing tools and add more tools
- Add more examples and documentation
- etc.
