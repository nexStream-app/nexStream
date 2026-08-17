Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = $PSScriptRoot

# 1. Increment build number
$numFile = Join-Path $root "build_number.txt"
$current = 0
if (Test-Path $numFile) { $current = [int](Get-Content $numFile -Raw).Trim() }
$next    = $current + 1
$nextStr = $next.ToString("D4")
Set-Content $numFile $nextStr -NoNewline
Write-Host "Build number: $nextStr"

# 2. Build release APK
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

Write-Host "Building release APK..."
& (Join-Path $root "gradlew.bat") assembleRelease

if ($LASTEXITCODE -ne 0) {
    $rollback = $current.ToString("D4")
    Set-Content $numFile $rollback -NoNewline
    Write-Host "Build failed - rolled back to $rollback"
    exit 1
}

# 3. Rename APK
$apkSrc = Join-Path $root "app\build\outputs\apk\release\app-release.apk"
$apkDst = Join-Path $root "nexStream.apk"
Copy-Item $apkSrc $apkDst -Force
Write-Host "APK copied to nexStream.apk"

# 4. Upload via WinSCP
$envFile = Join-Path $root "sftp.env"
if (-not (Test-Path $envFile)) {
    Write-Host "sftp.env not found"
    exit 1
}

function Get-Env {
    param([string]$name)
    $line = Get-Content $envFile | Where-Object { $_ -match "^\s*$name\s*=" } | Select-Object -First 1
    if (-not $line) { return $null }
    return ($line.Split("=", 2)[1]).Trim().Trim('"').Trim("'")
}

$sftpUser    = Get-Env "SFTP_USER";    if (-not $sftpUser)    { $sftpUser    = "su457920" }
$sftpPass    = Get-Env "SFTP_PASS"
$sftpHost    = Get-Env "SFTP_HOST";    if (-not $sftpHost)    { $sftpHost    = "access-5019910084.webspace-host.com" }
$sftpPort    = Get-Env "SFTP_PORT";    if (-not $sftpPort)    { $sftpPort    = "22" }
$rawRemote   = Get-Env "SFTP_REMOTE";  if (-not $rawRemote)   { $rawRemote   = "/htdocs" }
$sftpRemote  = $rawRemote.TrimEnd("/")
$sftpHostKey = Get-Env "SFTP_HOSTKEY"

if (-not $sftpPass) {
    Write-Host "SFTP_PASS missing"
    exit 1
}

$winscpPath = ""
$candidates = @(
    "C:\Program Files (x86)\WinSCP\WinSCP.com",
    "C:\Program Files\WinSCP\WinSCP.com"
)
foreach ($c in $candidates) {
    if (Test-Path $c) { $winscpPath = $c; break }
}
if (-not $winscpPath) {
    Write-Host "WinSCP.com not found"
    exit 1
}

$openUrl = "sftp://${sftpUser}:${sftpPass}@${sftpHost}:${sftpPort}"
if ($sftpHostKey) {
    $openCmd = "open $openUrl -hostkey=""$sftpHostKey"""
} else {
    $openCmd = "open $openUrl"
}

$apkRemote = "$sftpRemote/nexStream.apk"
$numRemote = "$sftpRemote/build_number.txt"

Write-Host "Uploading to $sftpRemote ..."

$cmds = @(
    $openCmd,
    "option batch continue",
    "put ""$apkDst"" ""$apkRemote""",
    "put ""$numFile"" ""$numRemote""",
    "exit"
)

& $winscpPath /command $cmds

if ($LASTEXITCODE -ne 0) {
    Write-Host "WinSCP upload failed with code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "Done. Build $nextStr deployed."
