[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "High")]
param(
    [Parameter(Mandatory = $true)][string]$ManifestPath,
    [string]$QuarantineRoot = "",
    [switch]$Apply,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-Native {
    param([string]$Exe, [string[]]$Arguments, [string]$Message)
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $out = @(& $Exe @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
    if ($code -ne 0) { throw "${Message}: $($out -join ' ')" }
    return @($out | ForEach-Object { [string]$_ })
}

function Invoke-PowerShellScriptStreaming {
    param(
        [string]$Path,
        [string]$Message,
        [string]$Stage
    )

    Write-Host "[INFO] stage=$Stage status=started script=$([IO.Path]::GetFileName($Path))"
    $saved = $ErrorActionPreference
    $code = -1
    try {
        $ErrorActionPreference = "Continue"
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $Path
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
    if ($code -ne 0) { throw "${Message}: exit_code=$code" }
    Write-Host "[INFO] stage=$Stage status=completed"
}

function Lines {
    param([object[]]$Value)
    return @($Value | ForEach-Object { ([string]$_ -split "`r?`n") } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Inside {
    param([string]$Candidate, [string]$Root)
    $c = [IO.Path]::GetFullPath($Candidate)
    $r = [IO.Path]::GetFullPath($Root)
    if ($c.Equals($r, [StringComparison]::OrdinalIgnoreCase)) { return $true }
    $sep = [string][IO.Path]::DirectorySeparatorChar
    if (-not $r.EndsWith($sep)) { $r += $sep }
    return $c.StartsWith($r, [StringComparison]::OrdinalIgnoreCase)
}

function Container-Id {
    param([string]$Docker, [string]$Service)
    $ids = @(Lines (Invoke-Native $Docker @(
        "ps", "-q",
        "--filter", "label=com.docker.compose.project=tg_ref_b24_sup",
        "--filter", "label=com.docker.compose.service=$Service"
    ) "Unable to discover $Service"))
    if ($ids.Count -ne 1) { throw "Expected exactly one running ${Service} container, found $($ids.Count)." }
    return $ids[0]
}

function Inspect {
    param([string]$Docker, [string]$Id)
    return @(((Invoke-Native $Docker @("inspect", $Id) "docker inspect failed") -join "`n") | ConvertFrom-Json)[0]
}

function Env-Value {
    param([object]$Inspect, [string]$Name)
    $prefix = "${Name}="
    foreach ($v in @($Inspect.Config.Env)) {
        $s = [string]$v
        if ($s.StartsWith($prefix, [StringComparison]::Ordinal)) { return $s.Substring($prefix.Length) }
    }
    return ""
}

function Candidate-Hash {
    param([object[]]$Entries)
    $lines = @()
    foreach ($entry in @($Entries | Sort-Object MetadataId, StorageKey)) {
        foreach ($file in @($entry.LocalFiles | Sort-Object RootName, RelativePath, FullName)) {
            $lines += (@(
                [string]$entry.MetadataId,
                [string]$entry.StorageKey,
                [string]$entry.CanonicalObjectBucket,
                [string]$entry.CanonicalObjectKey,
                [string]$file.RootName,
                [string]$file.RelativePath,
                [string]$file.FullName,
                [string]$file.Length,
                ([string]$file.Sha256).ToLowerInvariant()
            ) -join "`t")
        }
    }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($lines -join "`n")))).Replace("-", "").ToLowerInvariant()
    } finally {
        $sha.Dispose()
    }
}

function Get-Sha256ReadOnly {
    param([string]$Path)

    # Windows PowerShell 5.1 can leak the script-level -WhatIf preference into
    # Get-FileHash's provider lookup. Disable it only for this read-only hash,
    # then restore it before any ShouldProcess/Move-Item decision is reached.
    $savedWhatIf = $WhatIfPreference
    try {
        $WhatIfPreference = $false
        $hashResult = Get-FileHash -LiteralPath $Path -Algorithm SHA256
    } finally {
        $WhatIfPreference = $savedWhatIf
    }

    if ($null -eq $hashResult -or [string]::IsNullOrWhiteSpace([string]$hashResult.Hash)) {
        throw "Unable to read SHA-256 for manifest source: $Path"
    }

    return ([string]$hashResult.Hash).ToLowerInvariant()
}

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$repoParent = Split-Path -Parent $repo
$manifestFull = [IO.Path]::GetFullPath($ManifestPath)
$rollback = Join-Path $repo ".env.storage-cutover-20260828-162458.bak"

if (-not (Test-Path -LiteralPath $manifestFull -PathType Leaf)) { throw "Manifest not found: $manifestFull" }
$manifest = Get-Content -LiteralPath $manifestFull -Raw -Encoding UTF8 | ConvertFrom-Json
$entries = @($manifest.exact_mapped_entries)
if ($entries.Count -eq 0) { throw "Manifest contains no exact_mapped_entries." }
if ([bool]$manifest.physical_delete_authorized) { throw "Refusing quarantine manifest with physical_delete_authorized=true." }

$setHash = Candidate-Hash $entries
if ($setHash -cne ([string]$manifest.candidate_set_sha256).Trim().ToLowerInvariant()) { throw "Manifest candidate entries were modified after mapping audit." }
if ($Apply -and [string]::IsNullOrWhiteSpace($QuarantineRoot)) { throw "QuarantineRoot must be explicit when -Apply is used." }
if ([string]::IsNullOrWhiteSpace($QuarantineRoot)) { $QuarantineRoot = Join-Path $repoParent ("iguana-legacy-storage-quarantine\plan-" + $setHash.Substring(0, 12)) }

$quarantine = [IO.Path]::GetFullPath($QuarantineRoot)
if (Inside $quarantine $repo) { throw "QuarantineRoot must be outside the repository." }

$roots = @{
    "attachments" = [IO.Path]::GetFullPath((Join-Path $repo "attachments"))
    "java-bot-attachments" = [IO.Path]::GetFullPath((Join-Path $repo "java-bot/attachments"))
}
foreach ($root in $roots.Values) {
    if (Inside $quarantine $root) { throw "QuarantineRoot must be outside legacy source roots." }
}

if ($ValidateOnly) {
    Write-Host "[GREEN] Storage quarantine script parsed successfully and manifest integrity is valid."
    Write-Host "[RESULT] manifest_entries=$($entries.Count)"
    Write-Host "[RESULT] candidate_set_sha256=$setHash"
    Write-Host "[RESULT] quarantine_authorized=$([bool]$manifest.quarantine_authorized)"
    Write-Host "[RESULT] physical_delete_authorized=$([bool]$manifest.physical_delete_authorized)"
    return
}

$status = @(& git -C $repo status --short 2>&1)
if ($LASTEXITCODE -ne 0 -or $status.Count -gt 0) { throw "Production working tree must be clean." }
$head = ((@(& git -C $repo rev-parse HEAD 2>&1) | ForEach-Object { [string]$_ }) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or $head -cne ([string]$manifest.git_commit).Trim()) { throw "Manifest is stale. manifest=$($manifest.git_commit) current=$head" }
if (-not (Test-Path -LiteralPath $rollback -PathType Leaf)) { throw "Required rollback backup is missing: $rollback" }

$cutoverGate = Join-Path $repo "scripts/docker-production-storage-cutover-gate.ps1"
$avatarGate = Join-Path $repo "scripts/docker-production-client-avatar-cutover-audit.ps1"
Invoke-PowerShellScriptStreaming -Path $cutoverGate -Message "Storage cutover gate failed" -Stage "storage-cutover-gate"
Invoke-PowerShellScriptStreaming -Path $avatarGate -Message "Client avatar audit failed" -Stage "client-avatar-cutover-audit"

Write-Host "[INFO] stage=runtime-health status=started"
$docker = (Get-Command docker -ErrorAction Stop).Source
foreach ($service in @("ops-worker", "panel-web", "panel-direct")) {
    $inspect = Inspect $docker (Container-Id $docker $service)
    if ([string]$inspect.State.Status -ne "running") { throw "Required service is not running: $service" }
    if ($null -ne $inspect.State.Health -and [string]$inspect.State.Health.Status -ne "healthy") { throw "Required service is not healthy: $service" }
    if ($service -eq "panel-web" -and (Env-Value $inspect "APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED").Trim().ToLowerInvariant() -ne "false") { throw "panel-web runtime fallback is not false." }
}
Write-Host "[INFO] stage=runtime-health status=completed"

Write-Host "[INFO] stage=manifest-source-verification status=started files=$([int](($entries | ForEach-Object { @($_.LocalFiles).Count } | Measure-Object -Sum).Sum))"
$qVolume = [IO.Path]::GetPathRoot($quarantine)
$plan = @()
foreach ($entry in $entries) {
    foreach ($file in @($entry.LocalFiles)) {
        $rootName = [string]$file.RootName
        if (-not $roots.ContainsKey($rootName)) { throw "Unsupported source root in manifest: $rootName" }

        $source = [IO.Path]::GetFullPath([string]$file.FullName)
        $root = $roots[$rootName]
        if (-not (Inside $source $root)) { throw "Manifest source escapes reviewed root: $source" }

        $relative = ([string]$file.RelativePath).Replace("/", [string][IO.Path]::DirectorySeparatorChar)
        $expected = [IO.Path]::GetFullPath((Join-Path $root $relative))
        if (-not $source.Equals($expected, [StringComparison]::OrdinalIgnoreCase)) { throw "Manifest FullName does not match RootName + RelativePath: $source" }
        if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Manifest source file is missing: $source" }
        if (-not ([IO.Path]::GetPathRoot($source)).Equals($qVolume, [StringComparison]::OrdinalIgnoreCase)) { throw "QuarantineRoot must be on the same volume as every source file." }

        $current = Get-Item -LiteralPath $source
        if ([long]$current.Length -ne [long]$file.Length) { throw "Manifest source length changed: $source" }

        $hash = Get-Sha256ReadOnly -Path $source
        if ($hash -cne ([string]$file.Sha256).Trim().ToLowerInvariant()) { throw "Manifest source SHA-256 changed: $source" }

        $dest = [IO.Path]::GetFullPath((Join-Path (Join-Path $quarantine $rootName) $relative))
        if (-not (Inside $dest $quarantine) -or (Test-Path -LiteralPath $dest)) { throw "Unsafe or existing quarantine destination: $dest" }

        $plan += [pscustomobject]@{
            Source = $source
            Destination = $dest
            MetadataId = [long]$entry.MetadataId
            Length = [long]$file.Length
        }
    }
}
Write-Host "[INFO] stage=manifest-source-verification status=completed files=$($plan.Count)"

Write-Host "[RESULT] planned_files=$($plan.Count)"
Write-Host "[RESULT] planned_bytes=$([long](($plan | Measure-Object -Property Length -Sum).Sum))"
Write-Host "[RESULT] candidate_set_sha256=$setHash"
Write-Host "[RESULT] quarantine_root=$quarantine"
Write-Host "[RESULT] quarantine_authorized=$([bool]$manifest.quarantine_authorized)"

if (-not $Apply) {
    foreach ($item in $plan) {
        Write-Host "[WHATIF] metadata_id=$($item.MetadataId) source=$($item.Source) destination=$($item.Destination)"
    }
    Write-Host "[GREEN] DRY RUN ONLY: no legacy files were moved."
    return
}

if (-not [bool]$manifest.quarantine_authorized) { throw "Manifest is not authorized for quarantine." }

$invocationWhatIf = [bool]$WhatIfPreference
foreach ($item in $plan) {
    $parent = Split-Path -Parent $item.Destination
    if ($PSCmdlet.ShouldProcess($item.Source, "Move exact manifest file to $($item.Destination)")) {
        if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
            [void](New-Item -ItemType Directory -Path $parent -Force)
        }
        Move-Item -LiteralPath $item.Source -Destination $item.Destination
    }
}

if ($invocationWhatIf) {
    Write-Host "[GREEN] -WhatIf completed: no legacy files were moved."
} else {
    Write-Host "[GREEN] QUARANTINE MOVE COMPLETED. No files were deleted."
}
