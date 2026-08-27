param(
    [string]$SecretsDir = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))

if ([string]::IsNullOrWhiteSpace($SecretsDir)) {
    $SecretsDir = [Environment]::GetEnvironmentVariable("IGUANA_SECRETS_DIR", "Process")
}
if ([string]::IsNullOrWhiteSpace($SecretsDir)) {
    $SecretsDir = Join-Path $repoRoot "config\secrets"
}

if (-not [System.IO.Path]::IsPathRooted($SecretsDir)) {
    $SecretsDir = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $SecretsDir))
} else {
    $SecretsDir = [System.IO.Path]::GetFullPath($SecretsDir)
}

New-Item -ItemType Directory -Force -Path $SecretsDir | Out-Null
$tokenPath = Join-Path $SecretsDir "alertmanager-ingestion.token"

if (Test-Path -LiteralPath $tokenPath -PathType Leaf) {
    $existing = [System.IO.File]::ReadAllText($tokenPath).Trim()
    if ($existing.Length -lt 32) {
        throw "Existing Alertmanager ingestion token is too short/invalid: $tokenPath"
    }
    Write-Host "[GREEN] Alertmanager ingestion token already exists."
    Write-Host "[INFO] Path: $tokenPath"
    exit 0
}

$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $rng.GetBytes($bytes)
} finally {
    $rng.Dispose()
}

$token = ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
$tmp = "$tokenPath.tmp.$PID"
$enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
[System.IO.File]::WriteAllText($tmp, $token + "`n", $enc)
Move-Item -LiteralPath $tmp -Destination $tokenPath

Write-Host "[GREEN] Alertmanager ingestion token created."
Write-Host "[INFO] Path: $tokenPath"
Write-Host "[INFO] Secret value was not printed."
