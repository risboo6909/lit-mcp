[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("codex", "claude", "all")]
    [string]$Target,

    [switch]$Help
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version 2.0

$Repository = "risboo6909/lit-mcp"
$ServerName = "lit"
$ReleaseBaseUrl = if ($env:LIT_MCP_RELEASE_BASE_URL) {
    $env:LIT_MCP_RELEASE_BASE_URL.TrimEnd("/")
} else {
    "https://github.com/$Repository/releases/latest/download"
}

function Show-Usage {
    @"
Usage: install.ps1 <codex|claude|all>

Downloads the latest lit-mcp release and configures it for:
  codex   Codex CLI, IDE extension, and app
  claude  Claude Code
  all     Both Codex and Claude Code

Environment variables:
  LIT_MCP_INSTALL_DIR       Override the installation directory
  LIT_MCP_RELEASE_BASE_URL  Override the release download URL
"@
}

function Fail([string]$Message) {
    throw $Message
}

function Get-RequiredCommand([string]$Name, [string]$ErrorMessage) {
    $Command = Get-Command $Name -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $Command) {
        Fail $ErrorMessage
    }
    return $Command.Source
}

function Test-Client([string]$Client) {
    if ($Client -eq "codex" -or $Client -eq "all") {
        $script:CodexCommand = Get-RequiredCommand "codex" "Codex is not installed or is not available on PATH."
    }
    if ($Client -eq "claude" -or $Client -eq "all") {
        $script:ClaudeCommand = Get-RequiredCommand "claude" "Claude Code is not installed or is not available on PATH."
    }
}

function Install-CodexConfig {
    $PreviousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $script:CodexCommand mcp get $ServerName *> $null
    $GetExitCode = $LASTEXITCODE
    $ErrorActionPreference = $PreviousErrorActionPreference
    if ($GetExitCode -eq 0) {
        Write-Host "Updating existing Codex MCP configuration..."
        & $script:CodexCommand mcp remove $ServerName | Out-Null
        if ($LASTEXITCODE -ne 0) { Fail "Unable to remove the existing Codex MCP configuration." }
    }

    & $script:CodexCommand mcp add $ServerName -- $script:JavaCommand -jar $script:JarPath --transport=stdio
    if ($LASTEXITCODE -ne 0) { Fail "Unable to configure Codex." }
}

function Install-ClaudeConfig {
    $PreviousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & $script:ClaudeCommand mcp remove --scope user $ServerName *> $null
    $RemoveExitCode = $LASTEXITCODE
    $ErrorActionPreference = $PreviousErrorActionPreference
    if ($RemoveExitCode -eq 0) {
        Write-Host "Updating existing Claude Code MCP configuration..."
    }

    & $script:ClaudeCommand mcp add --transport stdio --scope user $ServerName -- $script:JavaCommand -jar $script:JarPath --transport=stdio
    if ($LASTEXITCODE -ne 0) { Fail "Unable to configure Claude Code." }
}

if ($Help) {
    Show-Usage
    exit 0
}
if (-not $Target) {
    Show-Usage
    exit 2
}

$TempDir = $null
try {
    Test-Client $Target
    $script:JavaCommand = Get-RequiredCommand "java" "Java 21 or newer is required."

    $PreviousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $JavaVersionOutput = (& $script:JavaCommand -version 2>&1 | Out-String)
    $JavaExitCode = $LASTEXITCODE
    $ErrorActionPreference = $PreviousErrorActionPreference
    if ($JavaExitCode -ne 0) { Fail "Unable to run Java." }
    if ($JavaVersionOutput -notmatch 'version\s+"(?:1\.)?(\d+)') {
        Fail "Unable to determine the installed Java version."
    }
    $JavaMajor = [int]$Matches[1]
    if ($JavaMajor -lt 21) {
        Fail "Java 21 or newer is required; found Java $JavaMajor."
    }

    $DefaultInstallDir = Join-Path ([Environment]::GetFolderPath("LocalApplicationData")) "lit-mcp"
    $InstallDir = if ($env:LIT_MCP_INSTALL_DIR) { $env:LIT_MCP_INSTALL_DIR } else { $DefaultInstallDir }
    $script:JarPath = Join-Path $InstallDir "lit-mcp.jar"
    $TempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("lit-mcp-install-" + [Guid]::NewGuid().ToString("N"))
    New-Item -ItemType Directory -Path $TempDir | Out-Null

    $TempJar = Join-Path $TempDir "lit-mcp.jar"
    $TempChecksum = Join-Path $TempDir "lit-mcp.jar.sha256"
    Write-Host "Downloading lit-mcp..."
    Invoke-WebRequest -UseBasicParsing -Uri "$ReleaseBaseUrl/lit-mcp.jar" -OutFile $TempJar
    Invoke-WebRequest -UseBasicParsing -Uri "$ReleaseBaseUrl/lit-mcp.jar.sha256" -OutFile $TempChecksum

    Write-Host "Verifying checksum..."
    $ChecksumText = (Get-Content -Raw $TempChecksum).Trim()
    if ($ChecksumText -notmatch '^([0-9a-fA-F]{64})(?:\s|$)') {
        Fail "The downloaded checksum file is invalid."
    }
    $ExpectedHash = $Matches[1]
    $ActualHash = (Get-FileHash -Algorithm SHA256 $TempJar).Hash
    if ($ActualHash -ine $ExpectedHash) {
        Fail "Checksum verification failed."
    }

    New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
    $NewJarPath = "$($script:JarPath).new"
    Copy-Item -Force $TempJar $NewJarPath
    Move-Item -Force $NewJarPath $script:JarPath

    switch ($Target) {
        "codex" { Install-CodexConfig }
        "claude" { Install-ClaudeConfig }
        "all" {
            Install-CodexConfig
            Install-ClaudeConfig
        }
    }

    Write-Host ""
    Write-Host "lit-mcp was installed at: $($script:JarPath)"
    Write-Host "Restart the configured client before using lit-mcp."
} catch {
    Write-Error "Error: $($_.Exception.Message)"
    exit 1
} finally {
    if ($TempDir -and (Test-Path -LiteralPath $TempDir)) {
        Remove-Item -LiteralPath $TempDir -Recurse -Force
    }
}
