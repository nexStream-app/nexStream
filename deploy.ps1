<#
.SYNOPSIS
    Deploy backend/ to nexstream.uk via SFTP (WinSCP).

.DESCRIPTION
    Reads credentials from sftp.env in the project root.
    Without arguments, syncs the entire backend/ directory.
    With a path argument, uploads a single file or subdirectory.

.EXAMPLE
    .\deploy.ps1                          # full sync
    .\deploy.ps1 api/profiles.php         # single file
    .\deploy.ps1 api/                     # subdirectory only

.NOTES
    Requires WinSCP to be installed.
    On first run, WinSCP will ask to accept the server host key — accept it once
    and it will be cached. To skip the prompt add the server fingerprint as
    SFTP_HOSTKEY in sftp.env (copy from WinSCP after first connect).
#>

param(
    [string]$Path = ""   # relative to backend/; empty = full sync
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Load .env
# ---------------------------------------------------------------------------
$envFile = Join-Path $PSScriptRoot "sftp.env"
if (-not (Test-Path $envFile)) {
    Write-Error "sftp.env not found at $envFile"
    exit 1
}

function Get-Env([string]$name) {
    $line = Get-Content $envFile |
        Where-Object { $_ -match "^\s*$name\s*=" } |
        Select-Object -First 1
    if (-not $line) { return $null }
    return $line.Split("=", 2)[1].Trim().Trim('"').Trim("'")
}

$sftpUser    = Get-Env "SFTP_USER"    ?? "su457920"
$sftpPass    = Get-Env "SFTP_PASS"
$sftpHost    = Get-Env "SFTP_HOST"    ?? "access-5019910084.webspace-host.com"
$sftpPort    = Get-Env "SFTP_PORT"    ?? "22"
$sftpRemote  = Get-Env "SFTP_REMOTE"  ?? "/htdocs"
$sftpHostKey = Get-Env "SFTP_HOSTKEY"   # optional — suppresses host key prompt

if (-not $sftpPass) {
    Write-Error "SFTP_PASS is missing from .env"
    exit 1
}

# ---------------------------------------------------------------------------
# Locate WinSCP.com
# ---------------------------------------------------------------------------
$winscpCandidates = @(
    "C:\Program Files (x86)\WinSCP\WinSCP.com",
    "C:\Program Files\WinSCP\WinSCP.com"
)
$winscp = $winscpCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $winscp) {
    Write-Error "WinSCP.com not found. Install WinSCP (https://winscp.net) or set the path manually in deploy.ps1."
    exit 1
}

# ---------------------------------------------------------------------------
# Build open command (with optional host key fingerprint)
# ---------------------------------------------------------------------------
$openUrl = "sftp://${sftpUser}:${sftpPass}@${sftpHost}:${sftpPort}"
$openCmd = if ($sftpHostKey) {
    "open $openUrl -hostkey=`"$sftpHostKey`""
} else {
    "open $openUrl"
}

$localBase  = Join-Path $PSScriptRoot "backend"
$remoteBase = $sftpRemote.TrimEnd("/")

# ---------------------------------------------------------------------------
# Run WinSCP
# ---------------------------------------------------------------------------
if ($Path) {
    $rel        = $Path.TrimStart("/\").Replace('\', '/')
    $localPath  = Join-Path $localBase ($rel.Replace('/', '\'))
    $remotePath = "$remoteBase/$rel"

    if (-not (Test-Path $localPath)) {
        Write-Error "Local path not found: $localPath"
        exit 1
    }

    $remoteDir = $remotePath.Substring(0, $remotePath.LastIndexOf('/'))
    Write-Host "Uploading $localPath  ->  $remotePath"

    & $winscp /command `
        $openCmd `
        "option batch continue" `
        "mkdir `"$remoteDir`"" `
        "option batch abort" `
        "put `"$localPath`" `"$remotePath`"" `
        "exit"
} else {
    Write-Host "Syncing backend/  ->  $remoteBase"

    & $winscp /command `
        $openCmd `
        "synchronize remote `"$localBase`" `"$remoteBase`"" `
        "exit"
}

if ($LASTEXITCODE -ne 0) {
    Write-Error "WinSCP exited with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Done."
