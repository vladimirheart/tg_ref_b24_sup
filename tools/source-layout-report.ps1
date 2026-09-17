param(
    [string]$BaselinePath = '',
    [switch]$WriteBaseline,
    [switch]$CheckBaselineSync,
    [switch]$ShowBaseline
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($BaselinePath)) {
    $BaselinePath = Join-Path $PSScriptRoot 'source-layout-baseline.json'
}

if (-not (Test-Path -LiteralPath (Join-Path $root '.git'))) {
    throw "Run from a repository checkout; .git not found under: $root"
}

function Invoke-GitLines([string[]]$Arguments) {
    $stderrPath = [System.IO.Path]::GetTempFileName()
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        Push-Location $root
        try {
            $output = @(& git @Arguments 2> $stderrPath)
            $code = $LASTEXITCODE
        }
        finally {
            Pop-Location
        }
        if ($code -ne 0) {
            $stderr = [System.IO.File]::ReadAllText($stderrPath)
            throw "git $($Arguments -join ' ') failed with exit $code`: $stderr"
        }
        return @($output | ForEach-Object { [string]$_ })
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
        Remove-Item -LiteralPath $stderrPath -Force -ErrorAction SilentlyContinue
    }
}

function Get-SourceKind([string]$RelativePath) {
    $p = $RelativePath.Replace('\', '/').ToLowerInvariant()
    if ($p -match '(^|/)(target|build|dist|node_modules|vendor|generated|generated-sources|fixtures?|migrations?)(/|$)') {
        return $null
    }
    if ($p -match '^spring-panel/src/main/resources/scss/.+\.scss$') {
        return 'scss'
    }
    if ($p -match '^spring-panel/src/main/resources/static/js/.+\.js$' -and $p -notmatch '\.min\.js$') {
        return 'browser-js'
    }
    if ($p -match '^spring-panel/src/main/resources/templates/.+\.html$') {
        return 'template'
    }
    if ($p -match '/src/main/java/.+\.java$') {
        return 'java'
    }
    if ($p -match '^scripts/.+\.(ps1|sh|cmd|bat|py|js)$') {
        return 'operational-script'
    }
    return $null
}

function Get-SourceMetric([string]$RelativePath, [string]$Kind) {
    $fullPath = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
        return $null
    }
    $text = [System.IO.File]::ReadAllText($fullPath)
    $normalized = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = [System.Text.Encoding]::UTF8.GetByteCount($normalized)
    if ($normalized.Length -eq 0) {
        $lines = 0
    } else {
        $parts = @($normalized -split "`n", -1)
        $lines = $parts.Count
        if ($normalized.EndsWith("`n")) {
            $lines--
        }
    }
    return [pscustomobject][ordered]@{
        path = $RelativePath.Replace('\', '/')
        kind = $Kind
        bytes = [long]$bytes
        lines = [int]$lines
    }
}

function Test-ReviewTrigger($Metric) {
    switch ([string]$Metric.kind) {
        'scss' { return ([long]$Metric.bytes -gt 20KB) }
        'browser-js' { return ([long]$Metric.bytes -gt 40KB) }
        'template' { return ([long]$Metric.bytes -gt 30KB -or [int]$Metric.lines -gt 500) }
        'java' { return ([int]$Metric.lines -gt 500) }
        'operational-script' { return ([long]$Metric.bytes -gt 20KB) }
        default { return $false }
    }
}

function Test-Regression($Current, $Baseline) {
    switch ([string]$Current.kind) {
        'java' { return ([int]$Current.lines -gt [int]$Baseline.lines) }
        'template' {
            return ([long]$Current.bytes -gt [long]$Baseline.bytes -or [int]$Current.lines -gt [int]$Baseline.lines)
        }
        default { return ([long]$Current.bytes -gt [long]$Baseline.bytes) }
    }
}

function Format-Metric($Metric) {
    $kb = [Math]::Round(([long]$Metric.bytes / 1KB), 1)
    return "$kb KB / $([int]$Metric.lines) lines"
}

function Get-ReviewMetrics {
    $items = @()
    foreach ($relativePath in @(Invoke-GitLines @('ls-files'))) {
        if ([string]::IsNullOrWhiteSpace($relativePath)) { continue }
        $kind = Get-SourceKind $relativePath
        if ($null -eq $kind) { continue }
        $metric = Get-SourceMetric $relativePath $kind
        if ($null -ne $metric -and (Test-ReviewTrigger $metric)) {
            $items += $metric
        }
    }
    return @($items | Sort-Object path)
}

function Write-BaselineSnapshot($ReviewMetrics) {
    $headLines = @(Invoke-GitLines @('rev-parse', 'HEAD'))
    $head = ([string]$headLines[0]).Trim()
    $snapshot = [ordered]@{
        schema = 1
        baseline_commit = $head
        policy = 'docs/SOURCE_LAYOUT_POLICY.md'
        normalization = 'UTF-8 bytes after CRLF/CR -> LF; line count ignores the final trailing newline'
        entries = @($ReviewMetrics | ForEach-Object {
            [ordered]@{
                path = [string]$_.path
                kind = [string]$_.kind
                bytes = [long]$_.bytes
                lines = [int]$_.lines
            }
        })
    }
    $json = $snapshot | ConvertTo-Json -Depth 6
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($BaselinePath, $json + "`n", $utf8NoBom)
    Write-Host "Source-layout baseline written: $BaselinePath"
    Write-Host "Review-triggered baseline entries: $($ReviewMetrics.Count)"
}

try {
    $current = @(Get-ReviewMetrics)

    if ($WriteBaseline) {
        Write-BaselineSnapshot $current
        exit 0
    }

    if (-not (Test-Path -LiteralPath $BaselinePath -PathType Leaf)) {
        throw "Source-layout baseline not found: $BaselinePath"
    }

    $baseline = ([System.IO.File]::ReadAllText($BaselinePath) | ConvertFrom-Json)
    if ([int]$baseline.schema -ne 1) {
        throw "Unsupported source-layout baseline schema: $($baseline.schema)"
    }

    $baselineMap = @{}
    foreach ($entry in @($baseline.entries)) {
        $baselineMap[[string]$entry.path] = $entry
    }
    $currentMap = @{}
    foreach ($entry in $current) {
        $currentMap[[string]$entry.path] = $entry
    }

    $signals = @()
    $baselineCarry = @()
    foreach ($entry in $current) {
        $pathKey = [string]$entry.path
        if (-not $baselineMap.ContainsKey($pathKey)) {
            $signals += [pscustomobject]@{ status = 'NEW'; current = $entry; baseline = $null }
            continue
        }
        $before = $baselineMap[$pathKey]
        if (([string]$before.kind -ne [string]$entry.kind) -or (Test-Regression $entry $before)) {
            $signals += [pscustomobject]@{ status = 'REGRESSION'; current = $entry; baseline = $before }
        } else {
            $baselineCarry += $entry
        }
    }

    foreach ($entry in @($baseline.entries)) {
        $pathKey = [string]$entry.path
        if (-not $currentMap.ContainsKey($pathKey)) {
            $signals += [pscustomobject]@{ status = 'IMPROVED'; current = $null; baseline = $entry }
        }
    }

    $newCount = @($signals | Where-Object { $_.status -eq 'NEW' }).Count
    $regressionCount = @($signals | Where-Object { $_.status -eq 'REGRESSION' }).Count
    $improvedCount = @($signals | Where-Object { $_.status -eq 'IMPROVED' }).Count

    Write-Host ''
    Write-Host 'Source layout review report'
    Write-Host '==========================='
    Write-Host 'Policy: docs/SOURCE_LAYOUT_POLICY.md'
    Write-Host "Baseline commit: $($baseline.baseline_commit)"
    Write-Host "Current review-triggered files: $($current.Count)"
    Write-Host "Baseline carry-over: $($baselineCarry.Count)"
    Write-Host "NEW: $newCount | REGRESSION: $regressionCount | IMPROVED: $improvedCount"

    foreach ($signal in @($signals | Sort-Object @{Expression={ switch ($_.status) { 'REGRESSION' {0} 'NEW' {1} default {2} } }}, @{Expression={ if ($null -ne $_.current) { $_.current.path } else { $_.baseline.path } }})) {
        if ($signal.status -eq 'NEW') {
            Write-Host "[REVIEW][NEW] $($signal.current.kind) $($signal.current.path) - $(Format-Metric $signal.current)"
        } elseif ($signal.status -eq 'REGRESSION') {
            Write-Host "[REVIEW][REGRESSION] $($signal.current.kind) $($signal.current.path) - $(Format-Metric $signal.baseline) -> $(Format-Metric $signal.current)"
        } else {
            Write-Host "[INFO][IMPROVED] $($signal.baseline.kind) $($signal.baseline.path) - no longer crosses the review threshold"
        }
    }

    if ($ShowBaseline) {
        foreach ($entry in $baselineCarry) {
            Write-Host "[BASELINE] $($entry.kind) $($entry.path) - $(Format-Metric $entry)"
        }
    }

    if ($newCount -eq 0 -and $regressionCount -eq 0) {
        Write-Host 'No new source-layout review regressions.'
    } else {
        Write-Host 'Review signal only: NEW/REGRESSION findings do not fail this report.'
    }

    if ($CheckBaselineSync -and ($signals.Count -gt 0)) {
        [Console]::Error.WriteLine('Source-layout baseline is not synchronized with the current review-triggered source set.')
        exit 3
    }

    exit 0
}
catch {
    [Console]::Error.WriteLine("Source layout report failed: $($_.Exception.Message)")
    exit 2
}
