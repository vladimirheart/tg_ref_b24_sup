[CmdletBinding()]
param(
    [string]$EvidenceRoot = "",
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # Best effort only.
}

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Test-PathInsideOrEqual {
    param(
        [string]$CandidatePath,
        [string]$RootPath
    )

    $candidate = [System.IO.Path]::GetFullPath($CandidatePath)
    $root = [System.IO.Path]::GetFullPath($RootPath)
    if ($candidate.Equals($root, [System.StringComparison]::OrdinalIgnoreCase)) {
        return $true
    }
    $separator = [string][System.IO.Path]::DirectorySeparatorChar
    $prefix = $root
    if (-not $prefix.EndsWith($separator)) {
        $prefix += $separator
    }
    return $candidate.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)
}

$repoRoot = Get-RepoRoot
$repoParent = Split-Path -Parent $repoRoot
if ([string]::IsNullOrWhiteSpace($EvidenceRoot)) {
    $EvidenceRoot = Join-Path $repoParent "iguana-legacy-storage-inventory"
}
$evidenceRootFull = [System.IO.Path]::GetFullPath($EvidenceRoot)

if (Test-PathInsideOrEqual -CandidatePath $evidenceRootFull -RootPath $repoRoot) {
    throw "EvidenceRoot must be outside the repository: $evidenceRootFull"
}

$roots = @(
    [pscustomobject]@{ Name = "attachments"; Path = (Join-Path $repoRoot "attachments") },
    [pscustomobject]@{ Name = "java-bot-attachments"; Path = (Join-Path $repoRoot "java-bot/attachments") }
) | Where-Object { Test-Path -LiteralPath $_.Path -PathType Container }

if (@($roots).Count -eq 0) {
    throw "No legacy attachment roots were found."
}

if ($ValidateOnly) {
    Write-Host "[GREEN] Legacy storage inventory script parsed successfully."
    Write-Host "[RESULT] repo_root=$repoRoot"
    Write-Host "[RESULT] evidence_root=$evidenceRootFull"
    Write-Host "[RESULT] discovered_legacy_roots=$(@($roots).Count)"
    Write-Host "[RESULT] validation is read-only and did not create evidence files."
    return
}

$gitStatus = @(& git -C $repoRoot status --short 2>&1)
if ($LASTEXITCODE -ne 0) {
    throw "git status failed."
}
if ($gitStatus.Count -gt 0) {
    $gitStatus | ForEach-Object { Write-Host $_ }
    throw "Production working tree is not clean."
}
$gitCommit = ((@(& git -C $repoRoot rev-parse HEAD 2>&1) | ForEach-Object { [string]$_ }) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($gitCommit)) {
    throw "git rev-parse HEAD failed."
}

$inventory = @()
foreach ($root in $roots) {
    $resolvedRoot = (Resolve-Path -LiteralPath $root.Path).Path
    Write-Host "[INFO] inventory_root=$($root.Name) path=$resolvedRoot"
    foreach ($file in @(Get-ChildItem -LiteralPath $resolvedRoot -File -Recurse -Force -ErrorAction Stop)) {
        $relative = $file.FullName.Substring($resolvedRoot.Length).TrimStart("\")
        $normalized = $relative.Replace("\", "/")
        $firstPart = ($normalized -split "/", 2)[0].ToLowerInvariant()
        $classification = switch ($firstPart) {
            "knowledge_base" { "excluded:knowledge_base"; break }
            "passport_photos" { "excluded:passport_photos"; break }
            "forms" { "excluded:forms"; break }
            "avatars" { "separate-audit:avatars"; break }
            default { "dialog-or-orphan-review" }
        }
        $inventory += [pscustomobject]@{
            RootName = [string]$root.Name
            RootPath = $resolvedRoot
            FullName = $file.FullName
            RelativePath = $normalized
            Classification = $classification
            Length = [long]$file.Length
            LastWriteTimeUtc = $file.LastWriteTimeUtc.ToString("o")
        }
    }
}
$inventory = @($inventory | Sort-Object RootName, RelativePath)
$totalBytes = [long](($inventory | Measure-Object -Property Length -Sum).Sum)
$duplicates = @($inventory | Group-Object RelativePath | Where-Object { $_.Count -gt 1 })

$rootSummary = @($inventory | Group-Object RootName | Sort-Object Name | ForEach-Object {
    $bytes = [long](($_.Group | Measure-Object -Property Length -Sum).Sum)
    [pscustomobject]@{ Root = $_.Name; Files = $_.Count; Bytes = $bytes; MiB = [Math]::Round($bytes / 1MB, 2) }
})
$classSummary = @($inventory | Group-Object Classification | Sort-Object Name | ForEach-Object {
    $bytes = [long](($_.Group | Measure-Object -Property Length -Sum).Sum)
    [pscustomobject]@{ Classification = $_.Name; Files = $_.Count; Bytes = $bytes; MiB = [Math]::Round($bytes / 1MB, 2) }
})

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$evidenceDirectory = Join-Path $evidenceRootFull $stamp
[void](New-Item -ItemType Directory -Path $evidenceDirectory -Force)
$inventoryCsv = Join-Path $evidenceDirectory "storage-local-inventory.csv"
$inventoryJson = Join-Path $evidenceDirectory "storage-local-inventory.json"
$summaryJson = Join-Path $evidenceDirectory "storage-local-inventory-summary.json"

$inventory | Export-Csv -LiteralPath $inventoryCsv -NoTypeInformation -Encoding UTF8
$inventory | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $inventoryJson -Encoding UTF8
[ordered]@{
    schema_version = 1
    generated_at = (Get-Date).ToUniversalTime().ToString("o")
    git_commit = $gitCommit
    inventory_files = $inventory.Count
    inventory_bytes = $totalBytes
    inventory_mib = [Math]::Round($totalBytes / 1MB, 2)
    duplicate_relative_paths = $duplicates.Count
    roots = $rootSummary
    classifications = $classSummary
} | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $summaryJson -Encoding UTF8

Write-Host "[RESULT] STORAGE LEGACY LOCAL INVENTORY"
Write-Host "[RESULT] git_commit=$gitCommit"
Write-Host "[RESULT] inventory_roots=$(@($roots).Count)"
Write-Host "[RESULT] inventory_files=$($inventory.Count)"
Write-Host "[RESULT] inventory_bytes=$totalBytes"
Write-Host "[RESULT] duplicate_relative_paths=$($duplicates.Count)"
foreach ($item in $classSummary) {
    Write-Host "[RESULT] classification=$($item.Classification) files=$($item.Files) bytes=$($item.Bytes)"
}
Write-Host "[RESULT] evidence_directory=$evidenceDirectory"
Write-Host "[RESULT] inventory_csv=$inventoryCsv"
Write-Host "[RESULT] summary_json=$summaryJson"
Write-Host "[RESULT] no repository files, database rows, MinIO objects, or legacy source files were modified."
Write-Host "[GREEN] STORAGE LEGACY LOCAL INVENTORY COMPLETED"
