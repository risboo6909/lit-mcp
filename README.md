# lit-mcp

**DISCLAIMER: This project is not affiliated with or endorsed by any book websites. 
Use it responsibly and respect copyright laws.**

**lit-mcp** allows LLMs to work with popular books websites (currently only **Flibusta**). Initially I started this project to
learn some basic SpringAI and MCP concepts, but it turned out to be quite useful.

I started with **Flibusta** because it is one of the largest free book repositories. The project uses Flibusta's OPDS catalog
where it provides the required data and parses website pages for features that are not exposed through OPDS.

Also, I chose SpringAI and Kotlin due to these technologies being new to me, so this project served as well as a learning experience.

I was trying to use AI to generate some parts of the code especially for the parts that involve web scraping and parsing HTML.
As I see it as of now, AI is quite helpful in generating boilerplate code and providing suggestions, 
but it still requires human supervision to ensure correctness and quality. 

Although my initial goal was to check if I would be able to vibe code the whole project using AI, it turned out that
this is not yet feasible for non-trivial projects and lots of human input is still required.

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
MCP server through the selected client's CLI. Choose the client you want to configure.

#### Codex

macOS or Linux:

```bash
curl -fsSL https://github.com/risboo6909/lit-mcp/releases/latest/download/install.sh | sh -s -- codex
```

Windows PowerShell:

```powershell
& ([scriptblock]::Create((irm https://github.com/risboo6909/lit-mcp/releases/latest/download/install.ps1))) codex
```

#### Claude Code

macOS or Linux:

```bash
curl -fsSL https://github.com/risboo6909/lit-mcp/releases/latest/download/install.sh | sh -s -- claude
```

Windows PowerShell:

```powershell
& ([scriptblock]::Create((irm https://github.com/risboo6909/lit-mcp/releases/latest/download/install.ps1))) claude
```

#### Codex and Claude Code

Use the `all` target to configure both clients at once.

macOS or Linux:

```bash
curl -fsSL https://github.com/risboo6909/lit-mcp/releases/latest/download/install.sh | sh -s -- all
```

Windows PowerShell:

```powershell
& ([scriptblock]::Create((irm https://github.com/risboo6909/lit-mcp/releases/latest/download/install.ps1))) all
```

The PowerShell form runs the downloaded installer in the current session and does not require changing the execution
policy.

### Manual installation

Download `lit-mcp.jar` from the [latest GitHub release](https://github.com/risboo6909/lit-mcp/releases/latest), or use:

```bash
curl -L https://github.com/risboo6909/lit-mcp/releases/latest/download/lit-mcp.jar -o lit-mcp.jar
```

Run the downloaded JAR in `stdio` mode with:

```bash
java -jar lit-mcp.jar --transport=stdio
```

Flibusta can occasionally respond slowly. The defaults are 300 seconds for a complete MCP tool call and 15 seconds for
an individual HTTP request. They can be overridden when starting the server:

```bash
LIT_MCP_TOOL_TIMEOUT_MILLIS=600000 \
LIT_MCP_HTTP_REQUEST_TIMEOUT_MILLIS=30000 \
java -jar lit-mcp.jar --transport=stdio
```

### Building from source

To build the project, you can use the following command:

```bash
make build
```

This will compile the source code and create a runnable JAR file in the `build/libs` directory.

To run the MCP server in `stdio` mode, you can use the following command:

```bash
make run_stdio
```

To run the MCP server in `HTTP` mode, you can use the following command:

```bash
make run_http
```

On Windows systems, please use `gradlew.bat` to build and run the project.

### Client configuration

Use these instructions when you downloaded the JAR manually or want to manage the MCP configuration yourself.

#### Codex

To connect `lit-mcp` to Codex, add the following to `~/.codex/config.toml`:

```toml
[mcp_servers.lit]
command = "java"
args = ["-jar", "/absolute/path/to/lit-mcp.jar", "--transport=stdio"]
```

Replace `/absolute/path/to/lit-mcp.jar` with the location of the downloaded JAR, then restart Codex.

#### Claude Code

Register the downloaded JAR for your user account, then restart Claude Code:

```bash
claude mcp add --transport stdio --scope user lit -- \
  java -jar /absolute/path/to/lit-mcp.jar --transport=stdio
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
