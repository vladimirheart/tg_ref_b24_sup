param(
    [switch]$RestartRuntime,
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

function Invoke-Compose {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$Arguments
    )

    & $Docker @ComposePrefix @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Invoke-ComposeCapture {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$Arguments
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Docker @ComposePrefix @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    return [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Query-Postgres {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Docker @ComposePrefix exec -T postgres psql -U $User -d $Database -Atc $Sql 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    if ($code -ne 0) {
        throw "PostgreSQL query failed: $($output -join ' ')"
    }

    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Execute-Postgres {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    Invoke-Compose -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "exec", "-T", "postgres",
        "psql", "-U", $User, "-d", $Database, "-c", $Sql
    )
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

function Test-PathInsideRoot {
    param(
        [string]$CandidatePath,
        [string]$RootPath
    )

    $candidate = [System.IO.Path]::GetFullPath($CandidatePath)
    $root = [System.IO.Path]::GetFullPath($RootPath)
    $separator = [string][System.IO.Path]::DirectorySeparatorChar
    $rootPrefix = $root
    if (-not $rootPrefix.EndsWith($separator)) {
        $rootPrefix += $separator
    }
    return $candidate.StartsWith($rootPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}

function Find-ExistingLegacyAttachment {
    param(
        [object[]]$RootDescriptors,
        [string]$StorageKey,
        [string]$LegacyAttachmentRef
    )

    $normalizedKey = Normalize-StorageKey -Value $StorageKey
    if ([string]::IsNullOrWhiteSpace($normalizedKey)) {
        return $null
    }

    $orderedRoots = @($RootDescriptors)
    $normalizedLegacyRef = Normalize-StorageKey -Value $LegacyAttachmentRef
    if (-not [string]::IsNullOrWhiteSpace($normalizedLegacyRef) `
            -and $normalizedLegacyRef.ToLowerInvariant().Contains("java-bot/attachments/")) {
        $orderedRoots = @($RootDescriptors | Where-Object { $_.Name -eq "java-bot" }) `
            + @($RootDescriptors | Where-Object { $_.Name -ne "java-bot" })
    }

    foreach ($root in $orderedRoots) {
        if ($null -eq $root -or [string]::IsNullOrWhiteSpace([string]$root.HostPath)) {
            continue
        }
        if (-not (Test-Path -LiteralPath $root.HostPath -PathType Container)) {
            continue
        }

        $relative = $normalizedKey.Replace("/", [string][System.IO.Path]::DirectorySeparatorChar)
        $candidatePath = [System.IO.Path]::GetFullPath((Join-Path $root.HostPath $relative))
        if ((Test-PathInsideRoot -CandidatePath $candidatePath -RootPath $root.HostPath) `
                -and (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
            return [pscustomobject]@{
                HostPath = $candidatePath
                RootName = [string]$root.Name
            }
        }
    }

    return $null
}

function Invoke-MinioClient {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$ShellCommand,
        [string]$WorkspaceRoot = "",
        [string]$SourceFile = "",
        [switch]$IncludeObjectKey
    )

    $arguments = @(
        "run", "--rm", "--no-deps", "-T",
        "-e", "IGUANA_BACKFILL_ACCESS_KEY",
        "-e", "IGUANA_BACKFILL_SECRET_KEY",
        "-e", "IGUANA_BACKFILL_BUCKET",
        "-e", "IGUANA_BACKFILL_KEY_PREFIX"
    )
    if ($IncludeObjectKey) {
        $arguments += @("-e", "IGUANA_BACKFILL_OBJECT_KEY")
    }
    if (-not [string]::IsNullOrWhiteSpace($WorkspaceRoot)) {
        $arguments += @("--volume", "${WorkspaceRoot}:/workspace:ro")
    }
    if (-not [string]::IsNullOrWhiteSpace($SourceFile)) {
        $arguments += @("--volume", "${SourceFile}:/source/file:ro")
    }
    $arguments += @(
        "--entrypoint", "/bin/sh",
        "minio-init",
        "-c", $ShellCommand
    )

    return Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments $arguments
}

function Get-PanelRuntimeObjectKeyPrefix {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$RunningServices
    )

    if ($RunningServices -notcontains "panel-web") {
        return "iguana"
    }

    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "exec", "-T", "panel-web", "/bin/sh", "-c",
        'printf "%s" "${APP_STORAGE_OBJECT_KEY_PREFIX:-iguana}"'
    )
    if ($result.ExitCode -ne 0) {
        throw "Unable to resolve APP_STORAGE_OBJECT_KEY_PREFIX from panel-web: $($result.Output -join ' ')"
    }
    $value = (($result.Output | ForEach-Object { [string]$_ }) -join "").Trim()
    return Normalize-ObjectKeyPrefix -Value $value
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$attachmentsRoot = Join-Path $repoRoot "attachments"
$javaBotAttachmentsRoot = Join-Path $repoRoot "java-bot/attachments"
$dotEnv = Read-DotEnv -Path $dotEnvPath

if (-not (Test-Path -LiteralPath $attachmentsRoot -PathType Container)) {
    throw "Attachments root is missing: $attachmentsRoot"
}

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $baseCompose)

$dbUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_USER" -DefaultValue "iguana"
$dbName = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_DB" -DefaultValue "iguana"
$objectAccessKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -DefaultValue "iguana-minio"
$objectSecretKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -DefaultValue "iguana-minio-secret"
$objectBucket = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -DefaultValue "iguana"
$requestedObjectKeyPrefix = Normalize-ObjectKeyPrefix -Value (Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_KEY_PREFIX" -DefaultValue "iguana")

Invoke-Compose -Docker $docker -ComposePrefix $composePrefix -Arguments @("config", "-q")

if ($ValidateOnly) {
    Write-Host "[GREEN] PowerShell parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] requested_object_key_prefix=$requestedObjectKeyPrefix"
    Write-Host "[RESULT] no containers, database rows, or files were modified."
    return
}

$runningServicesResult = Invoke-ComposeCapture -Docker $docker -ComposePrefix $composePrefix -Arguments @(
    "ps", "--status", "running", "--services"
)
if ($runningServicesResult.ExitCode -ne 0) {
    throw "Unable to inspect production contour services: $($runningServicesResult.Output -join ' ')"
}
$runningServices = @($runningServicesResult.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })

foreach ($required in @("postgres", "minio")) {
    if ($runningServices -notcontains $required) {
        throw "Required service is not running: $required"
    }
}

$runtimeObjectKeyPrefix = Get-PanelRuntimeObjectKeyPrefix `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -RunningServices $runningServices

if ($runtimeObjectKeyPrefix -ne $requestedObjectKeyPrefix) {
    throw "Object key prefix mismatch: launcher/.env requests '$requestedObjectKeyPrefix' but panel-web uses '$runtimeObjectKeyPrefix'. Refusing to backfill ambiguous S3 keys."
}
$objectKeyPrefix = $runtimeObjectKeyPrefix

$dialogAttachmentRoots = @(
    [pscustomobject]@{
        Name = "attachments"
        HostPath = $attachmentsRoot
    },
    [pscustomobject]@{
        Name = "java-bot"
        HostPath = $javaBotAttachmentsRoot
    }
)

$metadataRowsRaw = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT json_build_object('id', id, 'storage_key', storage_key, 'legacy_attachment_ref', legacy_attachment_ref)::text FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"

$metadataRows = @()
if (-not [string]::IsNullOrWhiteSpace($metadataRowsRaw)) {
    foreach ($line in ($metadataRowsRaw -split "`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        try {
            $row = $line | ConvertFrom-Json
        } catch {
            throw "Unable to parse attachment metadata row as JSON: $line"
        }
        $id = [long]$row.id
        $storageKey = Normalize-StorageKey -Value ([string]$row.storage_key)
        if ($id -le 0 -or [string]::IsNullOrWhiteSpace($storageKey)) {
            continue
        }
        $legacyAttachmentRef = if ($null -eq $row.legacy_attachment_ref) { "" } else { [string]$row.legacy_attachment_ref }
        $source = Find-ExistingLegacyAttachment `
            -RootDescriptors $dialogAttachmentRoots `
            -StorageKey $storageKey `
            -LegacyAttachmentRef $legacyAttachmentRef
        $metadataRows += [pscustomobject]@{
            Id = $id
            StorageKey = $storageKey
            LegacyAttachmentRef = $legacyAttachmentRef
            ObjectKey = Join-ObjectKey -Prefix $objectKeyPrefix -Domain "attachments" -LogicalKey $storageKey
            Source = $source
        }
    }
}

$environmentNames = @(
    "IGUANA_BACKFILL_ACCESS_KEY",
    "IGUANA_BACKFILL_SECRET_KEY",
    "IGUANA_BACKFILL_BUCKET",
    "IGUANA_BACKFILL_KEY_PREFIX",
    "IGUANA_BACKFILL_OBJECT_KEY"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$availableIds = @()
$missingMappings = @()
$localSourceCount = 0

try {
    [Environment]::SetEnvironmentVariable("IGUANA_BACKFILL_ACCESS_KEY", $objectAccessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_BACKFILL_SECRET_KEY", $objectSecretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_BACKFILL_BUCKET", $objectBucket, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_BACKFILL_KEY_PREFIX", $objectKeyPrefix, "Process")

    $mirrorCommand = @'
set -eu
mc alias set local http://minio:9000 "$IGUANA_BACKFILL_ACCESS_KEY" "$IGUANA_BACKFILL_SECRET_KEY" >/dev/null
mc mb --ignore-existing "local/$IGUANA_BACKFILL_BUCKET" >/dev/null
object_root="local/$IGUANA_BACKFILL_BUCKET"
if [ -n "$IGUANA_BACKFILL_KEY_PREFIX" ]; then
  object_root="$object_root/$IGUANA_BACKFILL_KEY_PREFIX"
fi
for source in /workspace/attachments /workspace/java-bot/attachments; do
  if [ -d "$source" ]; then
    mc mirror --overwrite --exclude "avatars/*" --exclude "knowledge_base/*" --exclude "passport_photos/*" "$source" "$object_root/attachments" >/dev/null
  fi
done
for source in /workspace/attachments/avatars /workspace/spring-panel/attachments/avatars /workspace/spring-panel/src/main/resources/static/user_photos; do
  if [ -d "$source" ]; then
    mc mirror --overwrite "$source" "$object_root/avatars" >/dev/null
  fi
done
if [ -d /workspace/attachments/knowledge_base ]; then
  mc mirror --overwrite /workspace/attachments/knowledge_base "$object_root/knowledge_base" >/dev/null
fi
if [ -d /workspace/attachments/passport_photos ]; then
  mc mirror --overwrite /workspace/attachments/passport_photos "$object_root/passport_photos" >/dev/null
fi
'@

    Write-Host "[INFO] Mirroring legacy media into canonical MinIO runtime prefix '$objectKeyPrefix'..."
    $mirrorResult = Invoke-MinioClient `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -ShellCommand $mirrorCommand `
        -WorkspaceRoot $repoRoot
    if ($mirrorResult.ExitCode -ne 0) {
        throw "MinIO mirror failed with exit code $($mirrorResult.ExitCode): $($mirrorResult.Output -join ' ')"
    }

    $copyAndVerifyCommand = @'
set -eu
mc alias set local http://minio:9000 "$IGUANA_BACKFILL_ACCESS_KEY" "$IGUANA_BACKFILL_SECRET_KEY" >/dev/null
mc cp --overwrite /source/file "local/$IGUANA_BACKFILL_BUCKET/$IGUANA_BACKFILL_OBJECT_KEY" >/dev/null
mc stat "local/$IGUANA_BACKFILL_BUCKET/$IGUANA_BACKFILL_OBJECT_KEY" >/dev/null
'@
    $verifyOnlyCommand = @'
set -eu
mc alias set local http://minio:9000 "$IGUANA_BACKFILL_ACCESS_KEY" "$IGUANA_BACKFILL_SECRET_KEY" >/dev/null
mc stat "local/$IGUANA_BACKFILL_BUCKET/$IGUANA_BACKFILL_OBJECT_KEY" >/dev/null
'@

    foreach ($row in $metadataRows) {
        [Environment]::SetEnvironmentVariable("IGUANA_BACKFILL_OBJECT_KEY", [string]$row.ObjectKey, "Process")
        $result = $null
        if ($null -ne $row.Source) {
            $localSourceCount++
            $result = Invoke-MinioClient `
                -Docker $docker `
                -ComposePrefix $composePrefix `
                -ShellCommand $copyAndVerifyCommand `
                -SourceFile ([string]$row.Source.HostPath) `
                -IncludeObjectKey
        } else {
            $result = Invoke-MinioClient `
                -Docker $docker `
                -ComposePrefix $composePrefix `
                -ShellCommand $verifyOnlyCommand `
                -IncludeObjectKey
        }

        if ($result.ExitCode -eq 0) {
            $availableIds += [long]$row.Id
        } else {
            $missingMappings += [pscustomobject]@{
                Id = [long]$row.Id
                StorageKey = [string]$row.StorageKey
                ObjectKey = [string]$row.ObjectKey
                HasLocalSource = ($null -ne $row.Source)
            }
        }
    }
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

$availableIds = @($availableIds | Sort-Object -Unique)
if ($availableIds.Count -gt 0) {
    $idList = ($availableIds | ForEach-Object { [string]$_ }) -join ", "
    $updateSql = @"
UPDATE chat_attachment_metadata
   SET storage_provider = 's3',
       normalization_status = 'normalized',
       availability_status = 'available',
       updated_at = CURRENT_TIMESTAMP
 WHERE id IN ($idList);
"@
    Write-Host "[INFO] Marking $($availableIds.Count) S3-verified attachment metadata row(s) as available..."
    Execute-Postgres `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql $updateSql
} else {
    Write-Host "[WARN] No attachment metadata rows were verified at their canonical S3 object keys."
}

$statusSummary = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT availability_status || '=' || COUNT(*) FROM chat_attachment_metadata GROUP BY availability_status ORDER BY availability_status"

$missingMetadataRows = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT COUNT(*) FROM chat_history ch WHERE ch.attachment IS NOT NULL AND btrim(ch.attachment) <> '' AND NOT EXISTS (SELECT 1 FROM chat_attachment_metadata cam WHERE cam.chat_history_id = ch.id)"

if ($RestartRuntime) {
    Write-Host "[INFO] Restarting panel runtime containers so health checks reconnect cleanly..."
    Invoke-Compose -Docker $docker -ComposePrefix $composePrefix -Arguments @("restart", "panel-web", "ops-worker")
}

Write-Host "[RESULT] Legacy storage backfill completed without deleting local source files."
Write-Host "[RESULT] object_bucket=$objectBucket"
Write-Host "[RESULT] object_key_prefix=$objectKeyPrefix"
Write-Host "[RESULT] metadata_rows_considered=$($metadataRows.Count)"
Write-Host "[RESULT] metadata_rows_with_local_source=$localSourceCount"
Write-Host "[RESULT] metadata_rows_verified_s3=$($availableIds.Count)"
Write-Host "[RESULT] missing_s3_dialog_objects=$($missingMappings.Count)"
Write-Host "[RESULT] missing_metadata_rows=$missingMetadataRows"
if (-not [string]::IsNullOrWhiteSpace($statusSummary)) {
    foreach ($line in ($statusSummary -split "`n")) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            Write-Host "[RESULT] $line"
        }
    }
}
if ($missingMappings.Count -gt 0) {
    Write-Host "[WARN] Canonical S3 mapping failures remain. Local fallback must stay enabled."
    foreach ($item in ($missingMappings | Select-Object -First 20)) {
        Write-Host "[WARN] metadata_id=$($item.Id) storage_key=$($item.StorageKey) object_key=$($item.ObjectKey) local_source=$($item.HasLocalSource)"
    }
    if ($missingMappings.Count -gt 20) {
        Write-Host "[WARN] Additional mapping failures omitted: $($missingMappings.Count - 20)"
    }
}
