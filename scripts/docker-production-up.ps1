param(
    [switch]$Telegram,
    [switch]$Vk,
    [switch]$Max,
    [switch]$Build,
    [switch]$NoDetach,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-up.ps1."
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

function Ensure-DockerAvailable {
    $dockerCommand = Get-DockerCommandPath
    if (-not $dockerCommand) {
        throw "Docker is not installed or not available in PATH."
    }

    & $dockerCommand compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose is unavailable."
    }

    return $dockerCommand
}

$repoRoot = Get-RepoRoot
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}

$profiles = @()
if ($Telegram) {
    $profiles += "telegram"
}
if ($Vk) {
    $profiles += "vk"
}
if ($Max) {
    $profiles += "max"
}

$requiredDirectories = @(
    (Join-Path $repoRoot "attachments"),
    (Join-Path $repoRoot "attachments\knowledge_base"),
    (Join-Path $repoRoot "attachments\forms"),
    (Join-Path $repoRoot "attachments\avatars"),
    (Join-Path $repoRoot "logs"),
    (Join-Path $repoRoot "bot_databases")
)

foreach ($directory in $requiredDirectories) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

if ($ValidateOnly) {
    Write-Host "[INFO] Validation succeeded."
    Write-Host "[INFO] Compose file: $composeFile"
    if ($profiles.Count -gt 0) {
        Write-Host "[INFO] Profiles: $($profiles -join ', ')"
    } else {
        Write-Host "[INFO] Profiles: none (infra + panel only)"
    }
    exit 0
}

$dockerCommand = Ensure-DockerAvailable

$arguments = @("compose", "-f", $composeFile)
foreach ($profile in $profiles) {
    $arguments += @("--profile", $profile)
}
$arguments += "up"
if ($Build) {
    $arguments += "--build"
}
if (-not $NoDetach) {
    $arguments += "-d"
}

Write-Host "[INFO] Starting Iguana docker production contour"
Write-Host "[INFO] Profiles: $($(if ($profiles.Count -gt 0) { $profiles -join ', ' } else { 'none (infra + panel only)' }))"

& $dockerCommand @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
}

Write-Host "[INFO] Iguana docker production contour started."
