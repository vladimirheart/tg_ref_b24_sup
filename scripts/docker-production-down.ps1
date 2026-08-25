param(
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

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}

if ($ValidateOnly) {
    Write-Host "[INFO] Validation succeeded."
    Write-Host "[INFO] Compose file: $composeFile"
    Write-Host "[INFO] Remove volumes: $RemoveVolumes"
    exit 0
}

$dockerCommand = Get-DockerCommandPath
if (-not $dockerCommand) {
    throw "Docker is not installed or not available in PATH."
}

$arguments = @("compose", "-f", $composeFile, "down")
if ($RemoveVolumes) {
    $arguments += "-v"
}

Write-Host "[INFO] Stopping Iguana docker production contour"

& $dockerCommand @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose down failed with exit code $LASTEXITCODE."
}

Write-Host "[INFO] Iguana docker production contour stopped."
