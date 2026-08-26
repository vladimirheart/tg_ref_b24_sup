param(
    [switch]$Edge,
    [switch]$Observability,
    [switch]$Backup,
    [switch]$RemoveVolumes,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

if (-not $PSScriptRoot) {
    throw "Unable to resolve script root for docker-production-down.ps1."
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$edgeComposeFile = Join-Path $repoRoot "docker-compose.production-edge.yml"
$observabilityComposeFile = Join-Path $repoRoot "docker-compose.production-observability.yml"
$backupComposeFile = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"
if ($Backup) {
    $backupConfigLibrary = Join-Path $PSScriptRoot "lib\backup-config.ps1"
    if (-not (Test-Path -LiteralPath $backupConfigLibrary)) {
        throw "Backup config library is missing: $backupConfigLibrary"
    }
    . $backupConfigLibrary
    Import-IguanaBackupSettings -RepoRoot $repoRoot | Out-Null
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if ($Edge -and -not (Test-Path -LiteralPath $edgeComposeFile)) {
    throw "Edge compose file not found: $edgeComposeFile"
}
if ($Observability -and -not (Test-Path -LiteralPath $observabilityComposeFile)) {
    throw "Observability compose file not found: $observabilityComposeFile"
}
if ($Backup -and -not (Test-Path -LiteralPath $backupComposeFile)) {
    throw "Backup compose file not found: $backupComposeFile"
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    if ($ValidateOnly) {
        Write-Host "[INFO] File validation succeeded; Docker is not available, compose config was not executed."
        exit 0
    }
    throw "Docker is not installed or not available in PATH."
}

$baseArguments = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath) {
    $baseArguments += @("--env-file", $dotEnvPath)
}
$baseArguments += @("-f", $composeFile)
if ($Edge) {
    $baseArguments += @("-f", $edgeComposeFile)
}
if ($Observability) {
    $baseArguments += @("-f", $observabilityComposeFile)
}
if ($Backup) {
    $baseArguments += @("-f", $backupComposeFile)
}

if ($ValidateOnly) {
    $configArguments = $baseArguments + @("config", "-q")
    & $dockerCommand.Source @configArguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config validation failed with exit code $LASTEXITCODE."
    }
    Write-Host "[INFO] Validation succeeded."
    exit 0
}

$arguments = $baseArguments + @("down", "--remove-orphans")
if ($RemoveVolumes) {
    $arguments += "-v"
}

Write-Host "[INFO] Stopping Iguana docker production contour (panel-web / ops-worker / db-migrate)"
& $dockerCommand.Source @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed with exit code $LASTEXITCODE."
}

Write-Host "[INFO] Iguana docker production contour stopped."
