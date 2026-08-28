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

function Get-NativeOutputLines {
    param([object[]]$Output)

    $lines = @()
    foreach ($item in @($Output)) {
        $text = [string]$item
        foreach ($line in ($text -split "`r?`n")) {
            $trimmed = $line.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
                $lines += $trimmed
            }
        }
    }
    return $lines
}

function Assert-DockerSuccess {
    param(
        [string]$Docker,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $result = Invoke-NativeCapture -Executable $Docker -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) {
            continue
        }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }
        $name = $trimmed.Substring(0, $separatorIndex).Trim()
        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        $values[$name] = $value
    }
    return $values
}

function Get-SettingValue {
    param(
        [hashtable]$DotEnv,
        [string]$Name,
        [string]$DefaultValue = ""
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        return $fromEnvironment.Trim()
    }
    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace([string]$DotEnv[$Name])) {
        return ([string]$DotEnv[$Name]).Trim()
    }
    return $DefaultValue
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
    $byteCount = [int]($Hex.Length / 2)
    $bytes = New-Object byte[] $byteCount
    for ($i = 0; $i -lt $Hex.Length; $i += 2) {
        $bytes[[int]($i / 2)] = [Convert]::ToByte($Hex.Substring($i, 2), 16)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Get-ContainerEnvRequired {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$Service,
        [string]$Name
    )

    $result = Assert-DockerSuccess `
        -Docker $Docker `
        -Arguments ($ComposePrefix + @("exec", "-T", $Service, "printenv", $Name)) `
        -ErrorMessage "Unable to resolve $Name from service '$Service'"
    $value = ((Get-NativeOutputLines -Output $result.Output) -join "").Trim()
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required runtime environment variable is empty: $Service/$Name"
    }
    return $value
}

function Invoke-PostgresQuery {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql,
        [string]$ErrorMessage
    )

    return Assert-DockerSuccess `
        -Docker $Docker `
        -Arguments ($ComposePrefix + @(
            "exec", "-T", "postgres",
            "psql", "-U", $User, "-d", $Database, "-Atc", $Sql
        )) `
        -ErrorMessage $ErrorMessage
}

function Resolve-PanelAvatarReference {
    param([string]$Photo)

    if ([string]::IsNullOrWhiteSpace($Photo)) {
        return [pscustomobject]@{ Kind = "empty"; Filename = "" }
    }

    $normalized = $Photo.Trim().Replace("\", "/")
    $lower = $normalized.ToLowerInvariant()
    if ($lower.StartsWith("http://") -or $lower.StartsWith("https://") -or $lower.StartsWith("data:")) {
        return [pscustomobject]@{ Kind = "external"; Filename = "" }
    }
    if ($lower -eq "/avatar_default.svg" -or $lower -eq "avatar_default.svg") {
        return [pscustomobject]@{ Kind = "static"; Filename = "" }
    }

    $prefixes = @(
        "/api/attachments/avatars/",
        "api/attachments/avatars/",
        "/avatars/",
        "avatars/",
        "/static/user_photos/",
        "static/user_photos/",
        "/user_photos/",
        "user_photos/"
    )
    foreach ($prefix in $prefixes) {
        if ($lower.StartsWith($prefix)) {
            $candidate = $normalized.Substring($prefix.Length)
            $slashIndex = $candidate.LastIndexOf("/")
            if ($slashIndex -ge 0) {
                $candidate = $candidate.Substring($slashIndex + 1)
            }
            if ([string]::IsNullOrWhiteSpace($candidate) -or $candidate.Contains("..") -or $candidate -eq ".") {
                return [pscustomobject]@{ Kind = "invalid"; Filename = "" }
            }
            return [pscustomobject]@{ Kind = "object"; Filename = $candidate }
        }
    }

    if ($normalized.StartsWith("/")) {
        return [pscustomobject]@{ Kind = "static"; Filename = "" }
    }

    $filename = $normalized
    $lastSlash = $filename.LastIndexOf("/")
    if ($lastSlash -ge 0) {
        $filename = $filename.Substring($lastSlash + 1)
    }
    if ([string]::IsNullOrWhiteSpace($filename) -or $filename.Contains("..") -or $filename -eq ".") {
        return [pscustomobject]@{ Kind = "invalid"; Filename = "" }
    }
    return [pscustomobject]@{ Kind = "object"; Filename = $filename }
}

function Test-CanonicalObject {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$RepoRoot,
        [string]$ObjectKey
    )

    [Environment]::SetEnvironmentVariable("IGUANA_GATE_OBJECT_KEY", $ObjectKey, "Process")
    $result = Invoke-NativeCapture -Executable $Docker -Arguments ($ComposePrefix + @(
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
$manifestPath = Join-Path $repoRoot "ai-context/storage-known-unrecoverable-dialog-attachments.json"
$objectStatHelper = Join-Path $repoRoot "scripts/internal/storage-cutover-object-stat.sh"
$dotEnv = Read-DotEnv -Path $dotEnvPath

foreach ($requiredFile in @($composeFile, $manifestPath, $objectStatHelper)) {
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
    if ($manifestById.ContainsKey($idKey)) {
        throw "Duplicate metadata_id in known-unrecoverable manifest: $id"
    }
    if ($manifestByKey.ContainsKey($storageKey)) {
        throw "Duplicate storage_key in known-unrecoverable manifest: $storageKey"
    }
    $manifestEntry = [pscustomobject]@{
        Id = $id
        StorageKey = $storageKey
    }
    $manifestById[$idKey] = $manifestEntry
    $manifestByKey[$storageKey] = $manifestEntry
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

$requestedObjectKeyPrefix = Normalize-ObjectKeyPrefix -Value (Get-SettingValue `
    -DotEnv $dotEnv `
    -Name "APP_STORAGE_OBJECT_KEY_PREFIX" `
    -DefaultValue "iguana")
$requestedObjectBucket = Get-SettingValue `
    -DotEnv $dotEnv `
    -Name "APP_STORAGE_OBJECT_BUCKET" `
    -DefaultValue "iguana"

if ($ValidateOnly) {
    Write-Host "[GREEN] Storage cutover gate parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] known_unrecoverable_manifest_entries=$($manifestEntries.Count)"
    Write-Host "[RESULT] requested_object_key_prefix=$requestedObjectKeyPrefix"
    Write-Host "[RESULT] requested_object_bucket=$requestedObjectBucket"
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

    $dbUser = Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "postgres" `
        -Name "POSTGRES_USER"
    $dbName = Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "postgres" `
        -Name "POSTGRES_DB"
    $accessKey = Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "minio" `
        -Name "MINIO_ROOT_USER"
    $secretKey = Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "minio" `
        -Name "MINIO_ROOT_PASSWORD"
    $objectBucket = Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "panel-web" `
        -Name "APP_STORAGE_OBJECT_BUCKET"
    $objectKeyPrefix = Normalize-ObjectKeyPrefix -Value (Get-ContainerEnvRequired `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Service "panel-web" `
        -Name "APP_STORAGE_OBJECT_KEY_PREFIX")

    if ($objectKeyPrefix -cne $requestedObjectKeyPrefix) {
        throw "Object key prefix mismatch: .env/process requests '$requestedObjectKeyPrefix' but panel-web uses '$objectKeyPrefix'."
    }
    if ($objectBucket -cne $requestedObjectBucket) {
        throw "Object bucket mismatch: .env/process requests '$requestedObjectBucket' but panel-web uses '$objectBucket'."
    }

    [Environment]::SetEnvironmentVariable("IGUANA_GATE_ACCESS_KEY", $accessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_GATE_SECRET_KEY", $secretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_GATE_BUCKET", $objectBucket, "Process")

    $missingMetadataSql = "SELECT COUNT(*) FROM chat_history ch WHERE ch.attachment IS NOT NULL AND btrim(ch.attachment) <> '' AND NOT EXISTS (SELECT 1 FROM chat_attachment_metadata cam WHERE cam.chat_history_id = ch.id)"
    $missingMetadataResult = Invoke-PostgresQuery `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql $missingMetadataSql `
        -ErrorMessage "Unable to count attachment metadata gaps"
    $missingMetadataText = ((Get-NativeOutputLines -Output $missingMetadataResult.Output) -join "").Trim()
    $missingMetadataRows = 0
    if (-not [int]::TryParse($missingMetadataText, [ref]$missingMetadataRows)) {
        throw "Invalid missing metadata count returned by PostgreSQL: '$missingMetadataText'"
    }
    if ($missingMetadataRows -ne 0) {
        throw "Storage cutover remains blocked: missing_metadata_rows=$missingMetadataRows"
    }

    $attachmentSql = "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') || chr(9) || COALESCE(lower(availability_status), '') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"
    $metadataResult = Invoke-PostgresQuery `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql $attachmentSql `
        -ErrorMessage "Unable to read attachment metadata for cutover gate"

    $metadataRows = @()
    $metadataById = @{}
    foreach ($line in (Get-NativeOutputLines -Output $metadataResult.Output)) {
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

    $panelAvatarSql = "SELECT id || chr(9) || encode(convert_to(COALESCE(photo, ''), 'UTF8'), 'hex') FROM users WHERE photo IS NOT NULL AND btrim(photo) <> '' ORDER BY id"
    $panelAvatarResult = Invoke-PostgresQuery `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql $panelAvatarSql `
        -ErrorMessage "Unable to read panel avatar references"

    $panelAvatarObjectCount = 0
    $panelAvatarStaticOrExternalCount = 0
    $invalidPanelAvatarRefs = @()
    $missingPanelAvatars = @()
    foreach ($line in (Get-NativeOutputLines -Output $panelAvatarResult.Output)) {
        $parts = $line -split "`t", 2
        if ($parts.Count -ne 2) {
            throw "Malformed panel avatar row: $line"
        }
        $userId = [long]$parts[0]
        $photo = ConvertFrom-HexUtf8 -Hex $parts[1].Trim()
        $resolved = Resolve-PanelAvatarReference -Photo $photo
        if ($resolved.Kind -eq "external" -or $resolved.Kind -eq "static" -or $resolved.Kind -eq "empty") {
            $panelAvatarStaticOrExternalCount++
            continue
        }
        if ($resolved.Kind -ne "object") {
            $invalidPanelAvatarRefs += [pscustomobject]@{ Id = $userId; Photo = $photo }
            continue
        }
        $panelAvatarObjectCount++
        $objectKey = Join-ObjectKey `
            -Prefix $objectKeyPrefix `
            -Domain "avatars" `
            -LogicalKey ([string]$resolved.Filename)
        if (-not (Test-CanonicalObject `
                -Docker $docker `
                -ComposePrefix $composePrefix `
                -RepoRoot $repoRoot `
                -ObjectKey $objectKey)) {
            $missingPanelAvatars += [pscustomobject]@{
                Id = $userId
                Photo = $photo
                ObjectKey = $objectKey
            }
        }
    }

    foreach ($item in $missingPanelAvatars) {
        Write-Host "[WARN] panel_avatar user_id=$($item.Id) photo=$($item.Photo) object_key=$($item.ObjectKey)"
    }
    foreach ($item in $invalidPanelAvatarRefs) {
        Write-Host "[WARN] invalid_panel_avatar_ref user_id=$($item.Id) photo=$($item.Photo)"
    }
    if ($missingPanelAvatars.Count -gt 0) {
        throw "Storage cutover remains blocked: missing_s3_panel_avatars=$($missingPanelAvatars.Count)"
    }
    if ($invalidPanelAvatarRefs.Count -gt 0) {
        throw "Storage cutover remains blocked: invalid_panel_avatar_refs=$($invalidPanelAvatarRefs.Count)"
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
    Write-Host "[RESULT] panel_avatar_object_refs_checked=$panelAvatarObjectCount"
    Write-Host "[RESULT] panel_avatar_static_or_external_refs=$panelAvatarStaticOrExternalCount"
    Write-Host "[RESULT] missing_s3_panel_avatars=$($missingPanelAvatars.Count)"
    Write-Host "[RESULT] invalid_panel_avatar_refs=$($invalidPanelAvatarRefs.Count)"
    Write-Host "[RESULT] gate is read-only and did not modify database rows, MinIO objects, or local files."
    Write-Host "[GREEN] STORAGE CUTOVER GATE PASSED: all unexpected dialog mapping and panel avatar gaps are zero; $($knownMissing.Count) reviewed historical attachments remain explicitly known-unrecoverable."
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", $oldIgnoreOrphans, "Process")
}
