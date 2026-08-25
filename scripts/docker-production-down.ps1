param(
    [switch]$Edge,
    [switch]$RemoveVolumes,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-down.ps1."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Get-DockerCommandPath {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) {
        return $dockerCommand.Source
    }
    return $null
}

$repoRoot = Get-RepoRoot
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$edgeComposeFile = Join-Path $repoRoot "docker-compose.production-edge.yml"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if ($Edge -and -not (Test-Path -LiteralPath $edgeComposeFile)) {
    throw "Edge compose file not found: $edgeComposeFile"
}

if ($ValidateOnly) {
    Write-Host "[INFO] Validation succeeded."
    Write-Host "[INFO] Compose file: $composeFile"
    if ($Edge) {
        Write-Host "[INFO] Edge compose file: $edgeComposeFile"
    }
    Write-Host "[INFO] Edge enabled: $Edge"
    Write-Host "[INFO] Remove volumes: $RemoveVolumes"
    exit 0
}

$dockerCommand = Get-DockerCommandPath
if (-not $dockerCommand) {
    throw "Docker is not installed or not available in PATH."
}

$arguments = @("compose", "-f", $composeFile)
if ($Edge) {
    $arguments += @("-f", $edgeComposeFile)
}
$arguments += "down"
if ($RemoveVolumes) {
    $arguments += "-v"
}

Write-Host "[INFO] Stopping Iguana docker production contour"
Write-Host "[INFO] Edge enabled: $Edge"

& $dockerCommand @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed with exit code $LASTEXITCODE."
}

Write-Host "[INFO] Iguana docker production contour stopped."
