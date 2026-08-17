#!/bin/sh

set -eu

REPOSITORY="risboo6909/lit-mcp"
SERVER_NAME="lit"
RELEASE_BASE_URL="${LIT_MCP_RELEASE_BASE_URL:-https://github.com/$REPOSITORY/releases/latest/download}"

say() {
    printf '%s\n' "$*"
}

fail() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: install.sh <codex|claude|all>

Downloads the latest lit-mcp release and configures it for:
  codex   Codex CLI, IDE extension, and app
  claude  Claude Code
  all     Both Codex and Claude Code

Environment variables:
  LIT_MCP_INSTALL_DIR       Override the installation directory
  LIT_MCP_RELEASE_BASE_URL  Override the release download URL
EOF
}

command_exists() {
    command -v "$1" >/dev/null 2>&1
}

check_client() {
    case "$1" in
        codex)
            command_exists codex || fail "Codex is not installed or is not available on PATH."
            ;;
        claude)
            command_exists claude || fail "Claude Code is not installed or is not available on PATH."
            ;;
        all)
            check_client codex
            check_client claude
            ;;
        *)
            usage >&2
            exit 2
            ;;
    esac
}

check_java() {
    command_exists java || fail "Java 21 or newer is required."

    JAVA_COMMAND=$(command -v java)
    JAVA_VERSION_OUTPUT=$("$JAVA_COMMAND" -version 2>&1) || fail "Unable to run Java."
    JAVA_MAJOR=$(printf '%s\n' "$JAVA_VERSION_OUTPUT" | awk -F '"' '
        /version/ {
            split($2, parts, ".")
            if (parts[1] == "1") print parts[2]
            else print parts[1]
            exit
        }
    ')

    case "$JAVA_MAJOR" in
        ''|*[!0-9]*) fail "Unable to determine the installed Java version." ;;
    esac

    [ "$JAVA_MAJOR" -ge 21 ] || fail "Java 21 or newer is required; found Java $JAVA_MAJOR."
}

configure_codex() {
    if codex mcp get "$SERVER_NAME" >/dev/null 2>&1; then
        say "Updating existing Codex MCP configuration..."
        codex mcp remove "$SERVER_NAME" >/dev/null
    fi

    codex mcp add "$SERVER_NAME" -- "$JAVA_COMMAND" -jar "$JAR_PATH" --transport=stdio
}

configure_claude() {
    if claude mcp remove --scope user "$SERVER_NAME" >/dev/null 2>&1; then
        say "Updating existing Claude Code MCP configuration..."
    fi

    claude mcp add --transport stdio --scope user "$SERVER_NAME" -- \
        "$JAVA_COMMAND" -jar "$JAR_PATH" --transport=stdio
}

TARGET="${1:-}"

case "$TARGET" in
    -h|--help)
        usage
        exit 0
        ;;
esac

check_client "$TARGET"
check_java
command_exists curl || fail "curl is required to download lit-mcp."
[ -n "${HOME:-}" ] || fail "HOME is not set."

case "$(uname -s)" in
    Darwin)
        DEFAULT_INSTALL_DIR="$HOME/Library/Application Support/lit-mcp"
        ;;
    Linux)
        DEFAULT_INSTALL_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/lit-mcp"
        ;;
    *)
        fail "Only macOS and Linux are supported."
        ;;
esac

INSTALL_DIR="${LIT_MCP_INSTALL_DIR:-$DEFAULT_INSTALL_DIR}"
JAR_PATH="$INSTALL_DIR/lit-mcp.jar"
TEMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/lit-mcp-install.XXXXXX")

cleanup() {
    rm -rf "$TEMP_DIR"
}

trap cleanup EXIT
trap 'exit 1' HUP INT TERM

say "Downloading lit-mcp..."
curl --fail --location --silent --show-error \
    "$RELEASE_BASE_URL/lit-mcp.jar" \
    --output "$TEMP_DIR/lit-mcp.jar"
curl --fail --location --silent --show-error \
    "$RELEASE_BASE_URL/lit-mcp.jar.sha256" \
    --output "$TEMP_DIR/lit-mcp.jar.sha256"

say "Verifying checksum..."
if command_exists sha256sum; then
    (cd "$TEMP_DIR" && sha256sum --check lit-mcp.jar.sha256)
elif command_exists shasum; then
    (cd "$TEMP_DIR" && shasum --algorithm 256 --check lit-mcp.jar.sha256)
else
    fail "Neither sha256sum nor shasum is available to verify the download."
fi

mkdir -p "$INSTALL_DIR"
cp "$TEMP_DIR/lit-mcp.jar" "$JAR_PATH.new"
chmod 0644 "$JAR_PATH.new"
mv "$JAR_PATH.new" "$JAR_PATH"

case "$TARGET" in
    codex)
        configure_codex
        ;;
    claude)
        configure_claude
        ;;
    all)
        configure_codex
        configure_claude
        ;;
esac

say ""
say "lit-mcp was installed at: $JAR_PATH"
say "Restart the configured client before using lit-mcp."
