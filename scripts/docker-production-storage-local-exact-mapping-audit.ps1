[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$EvidenceDirectory,
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

function Invoke-Native {
    param([string]$Exe, [string[]]$Args, [string]$Message)
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $out = @(& $Exe @Args 2>&1)
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

function Lines { param([object[]]$Value)
    return @($Value | ForEach-Object { ([string]$_ -split "`r?`n") } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
}

function Normalize-Key { param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $v = $Value.Trim().Replace("\", "/")
    while ($v.StartsWith("/")) { $v = $v.Substring(1) }
    return $v
}

function From-HexUtf8 { param([string]$Hex)
    if ([string]::IsNullOrEmpty($Hex)) { return "" }
    if (($Hex.Length % 2) -ne 0 -or $Hex -notmatch '^[0-9A-Fa-f]+$') { throw "Invalid UTF-8 hex payload." }
    $bytes = New-Object byte[] ([int]($Hex.Length / 2))
    for ($i = 0; $i -lt $Hex.Length; $i += 2) { $bytes[[int]($i / 2)] = [Convert]::ToByte($Hex.Substring($i, 2), 16) }
    return [Text.Encoding]::UTF8.GetString($bytes)
}

function Container-Id { param([string]$Docker, [string]$Service)
    $ids = Lines (Invoke-Native $Docker @(
        "ps", "-q",
        "--filter", "label=com.docker.compose.project=tg_ref_b24_sup",
        "--filter", "label=com.docker.compose.service=$Service"
    ) "Unable to discover $Service")
    if ($ids.Count -ne 1) { throw "Expected exactly one running ${Service} container, found $($ids.Count)." }
    return $ids[0]
}

function Inspect { param([string]$Docker, [string]$Id)
    $raw = (Invoke-Native $Docker @("inspect", $Id) "docker inspect failed") -join "`n"
    return @($raw | ConvertFrom-Json)[0]
}

function Env-Value { param([object]$Inspect, [string]$Name)
    $prefix = "${Name}="
    foreach ($v in @($Inspect.Config.Env)) {
        $s = [string]$v
        if ($s.StartsWith($prefix, [StringComparison]::Ordinal)) { return $s.Substring($prefix.Length) }
    }
    return ""
}

function Candidate-Hash { param([object[]]$Entries)
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
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($lines -join "`n")))).Replace("-", "").ToLowerInvariant() }
    finally { $sha.Dispose() }
}

$repo = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$evidence = [IO.Path]::GetFullPath($EvidenceDirectory)
$inventoryCsv = Join-Path $evidence "storage-local-inventory.csv"
$summaryJson = Join-Path $evidence "storage-local-inventory-summary.json"
$knownPath = Join-Path $repo "ai-context/storage-known-unrecoverable-dialog-attachments.json"
foreach ($p in @($inventoryCsv, $summaryJson, $knownPath)) { if (-not (Test-Path -LiteralPath $p -PathType Leaf)) { throw "Required file missing: $p" } }

$summary = Get-Content -LiteralPath $summaryJson -Raw -Encoding UTF8 | ConvertFrom-Json
$inventory = @(Import-Csv -LiteralPath $inventoryCsv | Where-Object { $_.Classification -eq "dialog-or-orphan-review" })
$known = @((Get-Content -LiteralPath $knownPath -Raw -Encoding UTF8 | ConvertFrom-Json).entries)

if ($ValidateOnly) {
    Write-Host "[GREEN] Exact mapping audit parsed successfully."
    Write-Host "[RESULT] evidence_git_commit=$($summary.git_commit)"
    Write-Host "[RESULT] dialog_or_orphan_inventory_rows=$($inventory.Count)"
    Write-Host "[RESULT] known_unrecoverable_entries=$($known.Count)"
    Write-Host "[RESULT] validation is read-only and no runtime data was accessed."
    return
}

$status = @(& git -C $repo status --short 2>&1)
if ($LASTEXITCODE -ne 0 -or $status.Count -gt 0) { throw "Production working tree must be clean." }
$head = ((@(& git -C $repo rev-parse HEAD 2>&1) | ForEach-Object { [string]$_ }) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or $head -cne ([string]$summary.git_commit).Trim()) { throw "Inventory evidence is stale. inventory=$($summary.git_commit) current=$head" }

$cutoverGate = Join-Path $repo "scripts/docker-production-storage-cutover-gate.ps1"
$avatarGate = Join-Path $repo "scripts/docker-production-client-avatar-cutover-audit.ps1"
Invoke-PowerShellScriptStreaming -Path $cutoverGate -Message "Storage cutover gate failed" -Stage "storage-cutover-gate"
Invoke-PowerShellScriptStreaming -Path $avatarGate -Message "Client avatar audit failed" -Stage "client-avatar-cutover-audit"

Write-Host "[INFO] stage=runtime-storage-contract status=started"
$docker = (Get-Command docker -ErrorAction Stop).Source
$postgres = Container-Id $docker "postgres"
$panel = Inspect $docker (Container-Id $docker "panel-web")
if ((Env-Value $panel "APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED").Trim().ToLowerInvariant() -ne "false") { throw "panel-web runtime fallback is not false." }
$bucket = Env-Value $panel "APP_STORAGE_OBJECT_BUCKET"
$prefix = Normalize-Key (Env-Value $panel "APP_STORAGE_OBJECT_KEY_PREFIX")
$db = Inspect $docker $postgres
$dbUser = Env-Value $db "POSTGRES_USER"
$dbName = Env-Value $db "POSTGRES_DB"
if ([string]::IsNullOrWhiteSpace($bucket) -or [string]::IsNullOrWhiteSpace($prefix) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbName)) { throw "Runtime storage/database environment is incomplete." }
Write-Host "[INFO] stage=runtime-storage-contract status=completed bucket=$bucket prefix=$prefix"

Write-Host "[INFO] stage=attachment-metadata status=started"
$sql = "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') || chr(9) || COALESCE(lower(availability_status), '') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"
$metadata = @()
foreach ($line in (Lines (Invoke-Native $docker @("exec",$postgres,"psql","-U",$dbUser,"-d",$dbName,"-Atc",$sql) "Unable to read attachment metadata"))) {
    $parts = $line -split "`t", 3
    if ($parts.Count -ne 3) { throw "Malformed metadata row: $line" }
    $metadata += [pscustomobject]@{ MetadataId=[long]$parts[0]; StorageKey=(Normalize-Key (From-HexUtf8 $parts[1])); Availability=([string]$parts[2]).Trim().ToLowerInvariant() }
}
Write-Host "[INFO] stage=attachment-metadata status=completed rows=$($metadata.Count)"

Write-Host "[INFO] stage=local-sha256 status=started files=$($inventory.Count)"
$local = @()
foreach ($row in $inventory) {
    if (-not (Test-Path -LiteralPath $row.FullName -PathType Leaf)) { throw "Inventory file disappeared: $($row.FullName)" }
    $file = Get-Item -LiteralPath $row.FullName
    if ([long]$file.Length -ne [long]$row.Length) { throw "Inventory file size changed: $($row.FullName)" }
    $local += [pscustomobject]@{
        RootName=[string]$row.RootName; FullName=[string]$row.FullName; RelativePath=(Normalize-Key ([string]$row.RelativePath)); Length=[long]$file.Length;
        Sha256=(Get-FileHash -LiteralPath $row.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
    }
}
Write-Host "[INFO] stage=local-sha256 status=completed files=$($local.Count)"

Write-Host "[INFO] stage=exact-mapping status=started"
$mapped=@(); $orphans=@(); $ambiguous=@(); $rediscovered=@(); $dupSame=0; $dupDifferent=0
foreach ($key in @($local | Select-Object -ExpandProperty RelativePath | Sort-Object -Unique)) {
    $copies = @($local | Where-Object { $_.RelativePath -ceq $key })
    $matches = @($metadata | Where-Object { $_.StorageKey -ceq $key })
    if ($matches.Count -eq 0) { $orphans += [pscustomobject]@{RelativePath=$key;LocalFiles=$copies;Reason="no_exact_chat_attachment_metadata_storage_key"}; continue }
    if ($matches.Count -ne 1) { $ambiguous += [pscustomobject]@{RelativePath=$key;LocalFiles=$copies;Reason="multiple_metadata_rows_for_same_storage_key"}; continue }
    $m = $matches[0]
    if (@($known | Where-Object { [long]$_.metadata_id -eq $m.MetadataId -and (Normalize-Key ([string]$_.storage_key)) -ceq $m.StorageKey }).Count -gt 0) { $rediscovered += [pscustomobject]@{MetadataId=$m.MetadataId;StorageKey=$m.StorageKey;LocalFiles=$copies;Reason="known_unrecoverable_now_has_local_source"}; continue }
    if ($m.Availability -eq "missing") { $ambiguous += [pscustomobject]@{RelativePath=$key;LocalFiles=$copies;Reason="mapped_metadata_still_marked_missing"}; continue }
    $hashes = @($copies | Select-Object -ExpandProperty Sha256 | Sort-Object -Unique)
    $state = "single-local-copy"
    if ($copies.Count -gt 1 -and $hashes.Count -eq 1) { $dupSame++; $state="duplicate-local-copies-identical-sha256" }
    elseif ($copies.Count -gt 1) { $dupDifferent++; $ambiguous += [pscustomobject]@{RelativePath=$key;LocalFiles=$copies;Reason="duplicate_relative_path_has_different_sha256"}; continue }
    $mapped += [pscustomobject]@{ MetadataId=$m.MetadataId; StorageKey=$m.StorageKey; AvailabilityStatus=$m.Availability; CanonicalObjectBucket=$bucket; CanonicalObjectKey=(($prefix,"attachments",$m.StorageKey) -join "/"); LocalCopyCount=$copies.Count; LocalCopyState=$state; LocalFiles=$copies }
}
Write-Host "[INFO] stage=exact-mapping status=completed mapped=$($mapped.Count) orphans=$($orphans.Count) ambiguous=$($ambiguous.Count) rediscovered=$($rediscovered.Count)"

$mappedFiles=[int](($mapped|ForEach-Object{@($_.LocalFiles).Count}|Measure-Object -Sum).Sum)
$orphanFiles=[int](($orphans|ForEach-Object{@($_.LocalFiles).Count}|Measure-Object -Sum).Sum)
$ambiguousFiles=[int](($ambiguous|ForEach-Object{@($_.LocalFiles).Count}|Measure-Object -Sum).Sum)
$candidateBytes=[long](($mapped|ForEach-Object{$_.LocalFiles}|Measure-Object -Property Length -Sum).Sum)
$setHash=Candidate-Hash $mapped
$mappingPath=Join-Path $evidence "storage-local-exact-mapping-audit.json"
$manifestPath=Join-Path $evidence "storage-local-quarantine-candidate-manifest.json"
$orphanPath=Join-Path $evidence "storage-local-orphans.csv"

[ordered]@{schema_version=1;generated_at=(Get-Date).ToUniversalTime().ToString("o");git_commit=$head;physical_dialog_files=$local.Count;unique_dialog_relative_paths=@($local|Select-Object -ExpandProperty RelativePath|Sort-Object -Unique).Count;metadata_rows=$metadata.Count;known_unrecoverable_entries=$known.Count;exact_mapped_storage_keys=$mapped.Count;exact_mapped_physical_files=$mappedFiles;mapped_duplicate_keys_identical=$dupSame;mapped_duplicate_keys_different=$dupDifferent;orphan_unique_paths=$orphans.Count;orphan_physical_files=$orphanFiles;ambiguous_unique_paths=$ambiguous.Count;ambiguous_physical_files=$ambiguousFiles;rediscovered_known_loss_entries=$rediscovered.Count;candidate_bytes=$candidateBytes;candidate_set_sha256=$setHash;exact_mappings=$mapped;orphans=$orphans;ambiguous=$ambiguous;rediscovered_known_loss=$rediscovered} | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $mappingPath -Encoding UTF8
[ordered]@{schema_version=1;generated_at=(Get-Date).ToUniversalTime().ToString("o");git_commit=$head;source_inventory=$inventoryCsv;object_bucket=$bucket;object_key_prefix=$prefix;candidate_set_sha256=$setHash;quarantine_authorized=$false;physical_delete_authorized=$false;authorization_note="Read-only evidence only. Change authorization only in a separately reviewed copy.";exact_mapped_entries=$mapped} | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $manifestPath -Encoding UTF8
@($orphans | ForEach-Object { $o=$_; @($o.LocalFiles | ForEach-Object { [pscustomobject]@{RelativePath=$o.RelativePath;RootName=$_.RootName;FullName=$_.FullName;Length=$_.Length;Sha256=$_.Sha256;Reason=$o.Reason} }) }) | Export-Csv -LiteralPath $orphanPath -NoTypeInformation -Encoding UTF8

Write-Host "[RESULT] exact_mapped_storage_keys=$($mapped.Count)"
Write-Host "[RESULT] exact_mapped_physical_files=$mappedFiles"
Write-Host "[RESULT] mapped_duplicate_keys_identical=$dupSame"
Write-Host "[RESULT] mapped_duplicate_keys_different=$dupDifferent"
Write-Host "[RESULT] orphan_unique_paths=$($orphans.Count)"
Write-Host "[RESULT] orphan_physical_files=$orphanFiles"
Write-Host "[RESULT] ambiguous_unique_paths=$($ambiguous.Count)"
Write-Host "[RESULT] rediscovered_known_loss_entries=$($rediscovered.Count)"
Write-Host "[RESULT] candidate_bytes=$candidateBytes"
Write-Host "[RESULT] candidate_set_sha256=$setHash"
Write-Host "[RESULT] quarantine_authorized=false"
Write-Host "[RESULT] candidate_manifest=$manifestPath"
if ($rediscovered.Count -gt 0) { throw "STOP: a reviewed known-unrecoverable attachment now has a local source." }
if ($dupDifferent -gt 0) { throw "STOP: duplicate relative paths with different SHA-256 were found." }
if ($ambiguous.Count -gt 0) { throw "STOP: ambiguous local/metadata mappings were found." }
Write-Host "[GREEN] STORAGE EXACT LOCAL MAPPING AUDIT COMPLETED"
Write-Host "[GREEN] No legacy files, database rows, or MinIO objects were modified."
