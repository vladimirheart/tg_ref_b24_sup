param(
    [string]$DestinationRoot = "C:\Intel",
    [string]$PackageName = "iguana",
    [switch]$CleanTarget
)

$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir ".."))

if (-not (Test-Path -LiteralPath $DestinationRoot)) {
    New-Item -ItemType Directory -Path $DestinationRoot -Force | Out-Null
}

$destinationRootFull = [System.IO.Path]::GetFullPath((Resolve-Path -LiteralPath $DestinationRoot).Path)
$targetPath = Join-Path $destinationRootFull $PackageName
$targetPathFull = [System.IO.Path]::GetFullPath($targetPath)

if (-not $targetPathFull.StartsWith($destinationRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw ("Target path '{0}' escaped destination root '{1}'." -f $targetPathFull, $destinationRootFull)
}

if ($CleanTarget -and (Test-Path -LiteralPath $targetPathFull)) {
    Remove-Item -LiteralPath $targetPathFull -Recurse -Force
}

New-Item -ItemType Directory -Path $targetPathFull -Force | Out-Null

$excludedDirectories = @(
    (Join-Path $repoRoot ".git"),
    (Join-Path $repoRoot ".venv"),
    (Join-Path $repoRoot ".vscode"),
    (Join-Path $repoRoot ".agents"),
    (Join-Path $repoRoot "node_modules"),
    (Join-Path $repoRoot "logs"),
    (Join-Path $repoRoot "run"),
    (Join-Path $repoRoot "temp-recovery"),
    (Join-Path $repoRoot "spring-panel\target"),
    (Join-Path $repoRoot "java-bot\bot-core\target"),
    (Join-Path $repoRoot "java-bot\bot-telegram\target"),
    (Join-Path $repoRoot "java-bot\bot-vk\target"),
    (Join-Path $repoRoot "java-bot\bot-max\target")
)

$excludedFiles = @(
    "*.log",
    "*.tmp",
    "*.pid",
    "*.lck",
    "*.db-wal",
    "*.db-shm"
)

$robocopyArgs = @(
    $repoRoot,
    $targetPathFull,
    "/E",
    "/R:1",
    "/W:1",
    "/NP",
    "/NFL",
    "/NDL",
    "/NJH",
    "/NJS",
    "/XD"
) + $excludedDirectories + @(
    "/XF"
) + $excludedFiles

Write-Host "Iguana export"
Write-Host ("Source: {0}" -f $repoRoot)
Write-Host ("Destination: {0}" -f $targetPathFull)

& robocopy @robocopyArgs | Out-Null
$robocopyExitCode = $LASTEXITCODE

if ($robocopyExitCode -gt 7) {
    throw ("Robocopy finished with exit code {0}." -f $robocopyExitCode)
}

$importantPaths = @(
    "README.md",
    "docs\IGUANA_PROJECT_GUIDE.md",
    "docs\IGUANA_TRANSFER_WINDOWS.md",
    "spring-panel\run-windows.bat",
    "config\shared\settings.json"
)

foreach ($relativePath in $importantPaths) {
    $fullPath = Join-Path $targetPathFull $relativePath
    if (-not (Test-Path -LiteralPath $fullPath)) {
        throw ("Required file missing after export: {0}" -f $relativePath)
    }
}

Write-Host ""
Write-Host ("Iguana package is ready: {0}" -f $targetPathFull)
Write-Host ("Robocopy code: {0}" -f $robocopyExitCode)
