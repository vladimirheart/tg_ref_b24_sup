param(
    [string]$SourceDirectory = "",
    [string]$StagingDirectory = "",
    [switch]$Replace
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RepoRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Resolve-PathFromRepo {
    param([string]$Value, [string]$RepoRoot)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $RepoRoot
    }
    if ([System.IO.Path]::IsPathRooted($Value)) {
        return [System.IO.Path]::GetFullPath($Value)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $Value))
}

$repoRoot = Resolve-RepoRoot
$sourceRoot = Resolve-PathFromRepo -Value $SourceDirectory -RepoRoot $repoRoot
$stageRoot = if ([string]::IsNullOrWhiteSpace($StagingDirectory)) {
    Join-Path $repoRoot ".tmp\legacy-sqlite-import"
} else {
    Resolve-PathFromRepo -Value $StagingDirectory -RepoRoot $repoRoot
}

if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container)) {
    throw "Legacy SQLite source directory does not exist: $sourceRoot"
}

$safeStagePrefix = [System.IO.Path]::GetFullPath((Join-Path $repoRoot ".tmp"))
if (-not $stageRoot.StartsWith($safeStagePrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "StagingDirectory must stay under $safeStagePrefix"
}

if (Test-Path -LiteralPath $stageRoot) {
    $existing = @(Get-ChildItem -LiteralPath $stageRoot -Force -ErrorAction Stop)
    if ($existing.Count -gt 0 -and -not $Replace) {
        throw "Staging directory is not empty: $stageRoot. Verify its manifest and pass -Replace to create a fresh copy."
    }
    if ($existing.Count -gt 0 -and $Replace) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}

New-Item -ItemType Directory -Path $stageRoot -Force | Out-Null

$rootNames = @(
    "panel_runtime.db",
    "panel_identity.db",
    "monitoring.db",
    "bot_runtime.db",
    "clients.db",
    "knowledge_base.db",
    "objects.db",
    "settings.db"
)
$sources = @()
foreach ($name in $rootNames) {
    $source = Join-Path $sourceRoot $name
    if (Test-Path -LiteralPath $source -PathType Leaf) {
        $sources += [pscustomobject]@{ Source = $source; Relative = $name }
    }
}

$botDirectory = Join-Path $sourceRoot "bot_databases"
if (Test-Path -LiteralPath $botDirectory -PathType Container) {
    Get-ChildItem -LiteralPath $botDirectory -File -Filter "bot-*.db" | ForEach-Object {
        $sources += [pscustomobject]@{ Source = $_.FullName; Relative = (Join-Path "bot_databases" $_.Name) }
    }
}

if ($sources.Count -eq 0) {
    throw "No legacy SQLite files were found under $sourceRoot"
}

$manifestEntries = @()
foreach ($item in $sources) {
    $destination = Join-Path $stageRoot $item.Relative
    $destinationDirectory = Split-Path -Parent $destination
    New-Item -ItemType Directory -Path $destinationDirectory -Force | Out-Null

    $sourceHash = (Get-FileHash -LiteralPath $item.Source -Algorithm SHA256).Hash.ToLowerInvariant()
    Copy-Item -LiteralPath $item.Source -Destination $destination -Force
    $stagedHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($sourceHash -cne $stagedHash) {
        throw "Hash mismatch while staging $($item.Source). The source may have changed during copy."
    }
    $manifestEntries += [ordered]@{
        relative_path = $item.Relative.Replace("\", "/")
        source_path = [System.IO.Path]::GetFullPath($item.Source)
        size_bytes = (Get-Item -LiteralPath $item.Source).Length
        sha256 = $sourceHash
    }
}

$manifest = [ordered]@{
    schema_version = 1
    generated_at = (Get-Date).ToUniversalTime().ToString("o")
    source_directory = $sourceRoot
    entries = $manifestEntries
}
$manifestPath = Join-Path $stageRoot "manifest.json"
$manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $manifestPath -Encoding UTF8

Write-Host "[GREEN] Legacy SQLite staging completed without modifying source files."
Write-Host "[RESULT] staging_directory=$stageRoot"
Write-Host "[RESULT] manifest=$manifestPath"
Write-Host "[RESULT] staged_files=$($manifestEntries.Count)"
