param(
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

function Ensure-DockerAvailable {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCommand) {
        throw "Docker is not installed or not available in PATH."
    }
    & $dockerCommand.Source compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose is unavailable."
    }
    return $dockerCommand.Source
}

function Invoke-NativeCapture {
    param(
        [string]$Executable,
        [string[]]$Arguments
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Executable @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    return [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Invoke-DockerCapture {
    param(
        [string]$Docker,
        [string[]]$Arguments
    )
    return Invoke-NativeCapture -Executable $Docker -Arguments $Arguments
}

function Assert-DockerSuccess {
    param(
        [string]$Docker,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $result = Invoke-DockerCapture -Docker $Docker -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Normalize-StorageKey {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    $normalized = $Value.Trim().Replace("\", "/")
    while ($normalized.StartsWith("/")) {
        $normalized = $normalized.Substring(1)
    }
    return $normalized.Trim()
}

function Normalize-ObjectKeyPrefix {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    $normalized = $Value.Trim().Replace("\", "/")
    while ($normalized.StartsWith("/")) {
        $normalized = $normalized.Substring(1)
    }
    while ($normalized.EndsWith("/")) {
        $normalized = $normalized.Substring(0, $normalized.Length - 1)
    }
    return $normalized.Trim()
}

function Join-ObjectKey {
    param(
        [string]$Prefix,
        [string]$Domain,
        [string]$LogicalKey
    )

    $parts = @()
    $normalizedPrefix = Normalize-ObjectKeyPrefix -Value $Prefix
    if (-not [string]::IsNullOrWhiteSpace($normalizedPrefix)) {
        $parts += $normalizedPrefix
    }
    $parts += (Normalize-StorageKey -Value $Domain)
    $parts += (Normalize-StorageKey -Value $LogicalKey)
    return (($parts | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }) -join "/")
}

function ConvertFrom-HexUtf8 {
    param([string]$Hex)

    if ([string]::IsNullOrEmpty($Hex)) {
        return ""
    }
    if (($Hex.Length % 2) -ne 0 -or $Hex -notmatch '^[0-9A-Fa-f]+$') {
        throw "Invalid UTF-8 hex payload received from PostgreSQL."
    }
    $bytes = New-Object byte[] ($Hex.Length / 2)
    for ($i = 0; $i -lt $Hex.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($Hex.Substring($i, 2), 16)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Get-ResultInteger {
    param(
        [string[]]$Lines,
        [string]$Name
    )

    $pattern = '\[RESULT\]\s+' + [regex]::Escape($Name) + '=(\d+)'
    foreach ($line in @($Lines)) {
        $match = [regex]::Match([string]$line, $pattern)
        if ($match.Success) {
            return [int]$match.Groups[1].Value
        }
    }
    throw "Upstream storage audit did not emit [RESULT] $Name."
}

function Get-ContainerEnvOrDefault {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$Service,
        [string]$Name,
        [string]$DefaultValue = ""
    )

    $result = Invoke-DockerCapture -Docker $Docker -Arguments ($ComposePrefix + @(
        "exec", "-T", $Service, "printenv", $Name
    ))
    if ($result.ExitCode -eq 0) {
        $value = (($result.Output | ForEach-Object { [string]$_ }) -join "").Trim()
        if (-not [string]::IsNullOrWhiteSpace($value)) {
            return $value
        }
    }
    return $DefaultValue
}

function Test-CanonicalObject {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$RepoRoot,
        [string]$ObjectKey
    )

    [Environment]::SetEnvironmentVariable("IGUANA_GATE_OBJECT_KEY", $ObjectKey, "Process")
    $result = Invoke-DockerCapture -Docker $Docker -Arguments ($ComposePrefix + @(
        "run", "--rm", "--no-deps", "-T",
        "-e", "IGUANA_GATE_ACCESS_KEY",
        "-e", "IGUANA_GATE_SECRET_KEY",
        "-e", "IGUANA_GATE_BUCKET",
        "-e", "IGUANA_GATE_OBJECT_KEY",
        "--volume", "${RepoRoot}:/workspace:ro",
        "--entrypoint", "/bin/sh",
        "minio-init",
        "/workspace/scripts/internal/storage-cutover-object-stat.sh"
    ))

    if ($result.ExitCode -eq 0) {
        return $true
    }
    if ($result.ExitCode -eq 4) {
        return $false
    }
    throw "Canonical MinIO stat failed for object '$ObjectKey' with exit code $($result.ExitCode): $($result.Output -join ' ')"
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$upstreamAudit = Join-Path $repoRoot "scripts/docker-production-storage-cutover-audit.ps1"
$manifestPath = Join-Path $repoRoot "ai-context/storage-known-unrecoverable-dialog-attachments.json"
$objectStatHelper = Join-Path $repoRoot "scripts/internal/storage-cutover-object-stat.sh"

foreach ($requiredFile in @($composeFile, $upstreamAudit, $manifestPath, $objectStatHelper)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required cutover gate file is missing: $requiredFile"
    }
}

$helperBytes = [System.IO.File]::ReadAllBytes($objectStatHelper)
if ($helperBytes -contains [byte]13) {
    throw "storage-cutover-object-stat.sh contains CR bytes; LF checkout is required."
}

try {
    $manifest = (Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8) | ConvertFrom-Json
} catch {
    throw "Unable to parse known-unrecoverable manifest: $($_.Exception.Message)"
}

$manifestEntries = @($manifest.entries)
if ($manifestEntries.Count -eq 0) {
    throw "Known-unrecoverable manifest contains no entries."
}

$manifestById = @{}
$manifestByKey = @{}
foreach ($entry in $manifestEntries) {
    $id = [long]$entry.metadata_id
    $storageKey = Normalize-StorageKey -Value ([string]$entry.storage_key)
    if ($id -le 0 -or [string]::IsNullOrWhiteSpace($storageKey)) {
        throw "Known-unrecoverable manifest contains an invalid entry."
    }
    $idKey = [string]$id
    $storageKeyLookup = $storageKey.ToLowerInvariant()
    if ($manifestById.ContainsKey($idKey)) {
        throw "Duplicate metadata_id in known-unrecoverable manifest: $id"
    }
    if ($manifestByKey.ContainsKey($storageKeyLookup)) {
        throw "Duplicate storage_key in known-unrecoverable manifest: $storageKey"
    }
    $manifestEntry = [pscustomobject]@{
        Id = $id
        StorageKey = $storageKey
    }
    $manifestById[$idKey] = $manifestEntry
    $manifestByKey[$storageKeyLookup] = $manifestEntry
}

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $composeFile)

Assert-DockerSuccess `
    -Docker $docker `
    -Arguments ($composePrefix + @("config", "-q")) `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

$powershellExe = Join-Path $PSHOME "powershell.exe"
if (-not (Test-Path -LiteralPath $powershellExe -PathType Leaf)) {
    $powershellCommand = Get-Command powershell.exe -ErrorAction SilentlyContinue
    if (-not $powershellCommand) {
        throw "powershell.exe is unavailable for the upstream storage audit."
    }
    $powershellExe = $powershellCommand.Source
}

if ($ValidateOnly) {
    $upstreamValidation = Invoke-NativeCapture `
        -Executable $powershellExe `
        -Arguments @(
            "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", $upstreamAudit,
            "-ValidateOnly"
        )
    if ($upstreamValidation.ExitCode -ne 0) {
        throw "Upstream storage audit ValidateOnly failed with exit code $($upstreamValidation.ExitCode): $($upstreamValidation.Output -join ' ')"
    }
    Write-Host "[GREEN] Storage cutover gate parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] known_unrecoverable_manifest_entries=$($manifestEntries.Count)"
    Write-Host "[RESULT] object_stat_helper_lf=true"
    Write-Host "[RESULT] validation is read-only and no runtime data was accessed."
    return
}

$oldIgnoreOrphans = [Environment]::GetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "Process")
$environmentNames = @(
    "IGUANA_GATE_ACCESS_KEY",
    "IGUANA_GATE_SECRET_KEY",
    "IGUANA_GATE_BUCKET",
    "IGUANA_GATE_OBJECT_KEY"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

try {
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "true", "Process")

    $upstreamResult = Invoke-NativeCapture `
        -Executable $powershellExe `
        -Arguments @(
            "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", $upstreamAudit
        )

    $missingMetadataRows = Get-ResultInteger -Lines $upstreamResult.Output -Name "missing_metadata_rows"
    $missingPanelAvatars = Get-ResultInteger -Lines $upstreamResult.Output -Name "missing_s3_panel_avatars"
    $invalidPanelAvatarRefs = Get-ResultInteger -Lines $upstreamResult.Output -Name "invalid_panel_avatar_refs"

    Write-Host "[RESULT] upstream_missing_metadata_rows=$missingMetadataRows"
    Write-Host "[RESULT] upstream_missing_s3_panel_avatars=$missingPanelAvatars"
    Write-Host "[RESULT] upstream_invalid_panel_avatar_refs=$invalidPanelAvatarRefs"

    if ($missingMetadataRows -ne 0) {
        throw "Storage cutover remains blocked: missing_metadata_rows=$missingMetadataRows"
    }
    if ($missingPanelAvatars -ne 0) {
        throw "Storage cutover remains blocked: missing_s3_panel_avatars=$missingPanelAvatars"
    }
    if ($invalidPanelAvatarRefs -ne 0) {
        throw "Storage cutover remains blocked: invalid_panel_avatar_refs=$invalidPanelAvatarRefs"
    }

    $dbUser = Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "postgres" `
        -Name "POSTGRES_USER"
    $dbName = Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "postgres" `
        -Name "POSTGRES_DB"
    if ([string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbName)) {
        throw "Unable to resolve PostgreSQL runtime database/user."
    }

    $accessKey = Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "minio" `
        -Name "MINIO_ROOT_USER"
    $secretKey = Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "minio" `
        -Name "MINIO_ROOT_PASSWORD"
    $objectBucket = Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "panel-web" `
        -Name "APP_STORAGE_OBJECT_BUCKET" `
        -DefaultValue "iguana"
    $objectKeyPrefix = Normalize-ObjectKeyPrefix -Value (Get-ContainerEnvOrDefault `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "panel-web" `
        -Name "APP_STORAGE_OBJECT_KEY_PREFIX" `
        -DefaultValue "iguana")

    if ([string]::IsNullOrWhiteSpace($accessKey) -or [string]::IsNullOrWhiteSpace($secretKey)) {
        throw "Unable to resolve MinIO runtime credentials."
    }

    [Environment]::SetEnvironmentVariable("IGUANA_GATE_ACCESS_KEY", $accessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_GATE_SECRET_KEY", $secretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_GATE_BUCKET", $objectBucket, "Process")

    $sql = "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') || chr(9) || COALESCE(lower(availability_status), '') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"
    $metadataResult = Assert-DockerSuccess `
        -Docker $docker `
        -Arguments ($composePrefix + @(
            "exec", "-T", "postgres",
            "psql", "-U", $dbUser, "-d", $dbName, "-Atc", $sql
        )) `
        -ErrorMessage "Unable to read attachment metadata for cutover gate"

    $metadataRows = @()
    $metadataById = @{}
    foreach ($lineObject in $metadataResult.Output) {
        $line = ([string]$lineObject).Trim()
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line -split "`t", 3
        if ($parts.Count -ne 3) {
            throw "Malformed attachment metadata row: $line"
        }
        $id = [long]$parts[0]
        $storageKey = Normalize-StorageKey -Value (ConvertFrom-HexUtf8 -Hex $parts[1].Trim())
        $availabilityStatus = ([string]$parts[2]).Trim().ToLowerInvariant()
        if ($id -le 0 -or [string]::IsNullOrWhiteSpace($storageKey)) {
            throw "Invalid attachment metadata row received for cutover gate."
        }
        $row = [pscustomobject]@{
            Id = $id
            StorageKey = $storageKey
            AvailabilityStatus = $availabilityStatus
        }
        $metadataRows += $row
        $metadataById[[string]$id] = $row
    }

    $dbMissingRows = @($metadataRows | Where-Object { $_.AvailabilityStatus -eq "missing" })
    if ($dbMissingRows.Count -ne $manifestEntries.Count) {
        throw "PostgreSQL availability_status=missing count differs from reviewed manifest. db=$($dbMissingRows.Count) manifest=$($manifestEntries.Count)"
    }

    foreach ($manifestEntry in $manifestById.Values) {
        $idKey = [string]$manifestEntry.Id
        if (-not $metadataById.ContainsKey($idKey)) {
            throw "Reviewed known-unrecoverable metadata_id=$($manifestEntry.Id) is absent from current metadata. Review the manifest."
        }
        $dbRow = $metadataById[$idKey]
        if ($dbRow.StorageKey -cne $manifestEntry.StorageKey) {
            throw "Reviewed storage_key changed for metadata_id=$($manifestEntry.Id). manifest='$($manifestEntry.StorageKey)' db='$($dbRow.StorageKey)'"
        }
        if ($dbRow.AvailabilityStatus -ne "missing") {
            throw "Reviewed known-unrecoverable metadata_id=$($manifestEntry.Id) is no longer availability_status=missing. Review the manifest."
        }
    }

    $knownMissing = @()
    $unexpectedMissing = @()
    $staleManifest = @()

    foreach ($row in $metadataRows) {
        $objectKey = Join-ObjectKey `
            -Prefix $objectKeyPrefix `
            -Domain "attachments" `
            -LogicalKey $row.StorageKey
        $present = Test-CanonicalObject `
            -Docker $docker `
            -ComposePrefix $composePrefix `
            -RepoRoot $repoRoot `
            -ObjectKey $objectKey

        $idKey = [string]$row.Id
        $isManifestEntry = $manifestById.ContainsKey($idKey)
        if ($present) {
            if ($isManifestEntry) {
                $staleManifest += [pscustomobject]@{
                    Id = $row.Id
                    StorageKey = $row.StorageKey
                    Reason = "canonical_object_is_present"
                }
            }
            continue
        }

        if ($isManifestEntry `
                -and $row.AvailabilityStatus -eq "missing" `
                -and $manifestById[$idKey].StorageKey -ceq $row.StorageKey) {
            $knownMissing += [pscustomobject]@{
                Id = $row.Id
                StorageKey = $row.StorageKey
                ObjectKey = $objectKey
            }
        } else {
            $unexpectedMissing += [pscustomobject]@{
                Id = $row.Id
                StorageKey = $row.StorageKey
                AvailabilityStatus = $row.AvailabilityStatus
                ObjectKey = $objectKey
            }
        }
    }

    if ($staleManifest.Count -gt 0) {
        foreach ($item in $staleManifest) {
            Write-Host "[WARN] stale_known_unrecoverable metadata_id=$($item.Id) reason=$($item.Reason) storage_key=$($item.StorageKey)"
        }
        throw "Known-unrecoverable manifest is stale for $($staleManifest.Count) attachment(s). Review it before cutover."
    }

    if ($knownMissing.Count -ne $manifestEntries.Count) {
        throw "Canonical known-unrecoverable set differs from reviewed manifest. canonical_missing=$($knownMissing.Count) manifest=$($manifestEntries.Count)"
    }

    foreach ($item in $unexpectedMissing) {
        Write-Host "[WARN] unexpected_missing metadata_id=$($item.Id) availability=$($item.AvailabilityStatus) storage_key=$($item.StorageKey) object_key=$($item.ObjectKey)"
    }
    if ($unexpectedMissing.Count -gt 0) {
        throw "Storage cutover remains blocked: unexpected_missing_s3_dialog_objects=$($unexpectedMissing.Count)"
    }

    Write-Host "[RESULT] STORAGE CUTOVER GATE"
    Write-Host "[RESULT] object_bucket=$objectBucket"
    Write-Host "[RESULT] object_key_prefix=$objectKeyPrefix"
    Write-Host "[RESULT] attachment_mappings_checked=$($metadataRows.Count)"
    Write-Host "[RESULT] raw_missing_s3_dialog_objects=$($knownMissing.Count + $unexpectedMissing.Count)"
    Write-Host "[RESULT] known_unrecoverable_dialog_objects=$($knownMissing.Count)"
    Write-Host "[RESULT] unexpected_missing_s3_dialog_objects=$($unexpectedMissing.Count)"
    Write-Host "[RESULT] stale_known_unrecoverable_entries=$($staleManifest.Count)"
    Write-Host "[RESULT] missing_metadata_rows=$missingMetadataRows"
    Write-Host "[RESULT] missing_s3_panel_avatars=$missingPanelAvatars"
    Write-Host "[RESULT] invalid_panel_avatar_refs=$invalidPanelAvatarRefs"
    Write-Host "[RESULT] gate is read-only and did not modify database rows, MinIO objects, or local files."
    Write-Host "[GREEN] STORAGE CUTOVER GATE PASSED: all unexpected dialog mapping and panel avatar gaps are zero; $($knownMissing.Count) reviewed historical attachments remain explicitly known-unrecoverable."
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", $oldIgnoreOrphans, "Process")
}
