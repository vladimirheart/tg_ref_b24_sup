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

    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "exec", "-T", "postgres",
        "psql", "-U", $User, "-d", $Database, "-Atc", $Sql
    )
    if ($result.ExitCode -ne 0) {
        throw "PostgreSQL query failed: $($result.Output -join ' ')"
    }
    return (($result.Output | ForEach-Object { [string]$_ }) -join "`n").Trim()
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

function ConvertTo-LfLineEndings {
    param([string]$Value)

    if ($null -eq $Value) {
        return ""
    }
    return $Value.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Test-MinioObject {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$ObjectKey
    )

    [Environment]::SetEnvironmentVariable("IGUANA_AUDIT_OBJECT_KEY", $ObjectKey, "Process")
    $shellCommand = @'
set -eu
mc alias set local http://minio:9000 "$IGUANA_AUDIT_ACCESS_KEY" "$IGUANA_AUDIT_SECRET_KEY" >/dev/null
mc stat "local/$IGUANA_AUDIT_BUCKET/$IGUANA_AUDIT_OBJECT_KEY" >/dev/null
'@
    $normalizedShellCommand = ConvertTo-LfLineEndings -Value $shellCommand
    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "run", "--rm", "--no-deps", "-T",
        "-e", "IGUANA_AUDIT_ACCESS_KEY",
        "-e", "IGUANA_AUDIT_SECRET_KEY",
        "-e", "IGUANA_AUDIT_BUCKET",
        "-e", "IGUANA_AUDIT_OBJECT_KEY",
        "--entrypoint", "/bin/sh",
        "minio-init",
        "-c", $normalizedShellCommand
    )
    return ($result.ExitCode -eq 0)
}

function Get-PanelRuntimeObjectKeyPrefix {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix
    )

    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "exec", "-T", "panel-web", "/bin/sh", "-c",
        'printf "%s" "${APP_STORAGE_OBJECT_KEY_PREFIX:-iguana}"'
    )
    if ($result.ExitCode -ne 0) {
        throw "Unable to resolve APP_STORAGE_OBJECT_KEY_PREFIX from panel-web: $($result.Output -join ' ')"
    }
    return Normalize-ObjectKeyPrefix -Value ((($result.Output | ForEach-Object { [string]$_ }) -join "").Trim())
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$dotEnv = Read-DotEnv -Path $dotEnvPath

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
    Write-Host "[RESULT] audit is read-only and no runtime data was accessed."
    return
}

$runningResult = Invoke-ComposeCapture -Docker $docker -ComposePrefix $composePrefix -Arguments @(
    "ps", "--status", "running", "--services"
)
if ($runningResult.ExitCode -ne 0) {
    throw "Unable to inspect production contour services: $($runningResult.Output -join ' ')"
}
$runningServices = @($runningResult.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
foreach ($required in @("postgres", "minio", "panel-web")) {
    if ($runningServices -notcontains $required) {
        throw "Required service is not running: $required"
    }
}

$runtimeObjectKeyPrefix = Get-PanelRuntimeObjectKeyPrefix -Docker $docker -ComposePrefix $composePrefix
if ($runtimeObjectKeyPrefix -ne $requestedObjectKeyPrefix) {
    throw "Object key prefix mismatch: launcher/.env requests '$requestedObjectKeyPrefix' but panel-web uses '$runtimeObjectKeyPrefix'."
}
$objectKeyPrefix = $runtimeObjectKeyPrefix

$attachmentRowsRaw = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT json_build_object('id', id, 'storage_key', storage_key)::text FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"

$panelAvatarRowsRaw = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT json_build_object('id', id, 'photo', photo)::text FROM users WHERE photo IS NOT NULL AND btrim(photo) <> '' ORDER BY id"

$missingMetadataRows = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT COUNT(*) FROM chat_history ch WHERE ch.attachment IS NOT NULL AND btrim(ch.attachment) <> '' AND NOT EXISTS (SELECT 1 FROM chat_attachment_metadata cam WHERE cam.chat_history_id = ch.id)"

$environmentNames = @(
    "IGUANA_AUDIT_ACCESS_KEY",
    "IGUANA_AUDIT_SECRET_KEY",
    "IGUANA_AUDIT_BUCKET",
    "IGUANA_AUDIT_OBJECT_KEY"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$attachmentCount = 0
$missingAttachments = @()
$panelAvatarObjectCount = 0
$panelAvatarStaticOrExternalCount = 0
$invalidPanelAvatarRefs = @()
$missingPanelAvatars = @()

try {
    [Environment]::SetEnvironmentVariable("IGUANA_AUDIT_ACCESS_KEY", $objectAccessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_AUDIT_SECRET_KEY", $objectSecretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_AUDIT_BUCKET", $objectBucket, "Process")

    if (-not [string]::IsNullOrWhiteSpace($attachmentRowsRaw)) {
        foreach ($line in ($attachmentRowsRaw -split "`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            $row = $line | ConvertFrom-Json
            $storageKey = Normalize-StorageKey -Value ([string]$row.storage_key)
            if ([string]::IsNullOrWhiteSpace($storageKey)) {
                continue
            }
            $attachmentCount++
            $objectKey = Join-ObjectKey -Prefix $objectKeyPrefix -Domain "attachments" -LogicalKey $storageKey
            if (-not (Test-MinioObject -Docker $docker -ComposePrefix $composePrefix -ObjectKey $objectKey)) {
                $missingAttachments += [pscustomobject]@{
                    Id = [long]$row.id
                    StorageKey = $storageKey
                    ObjectKey = $objectKey
                }
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($panelAvatarRowsRaw)) {
        foreach ($line in ($panelAvatarRowsRaw -split "`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) {
                continue
            }
            $row = $line | ConvertFrom-Json
            $photo = if ($null -eq $row.photo) { "" } else { [string]$row.photo }
            $resolved = Resolve-PanelAvatarReference -Photo $photo
            if ($resolved.Kind -eq "external" -or $resolved.Kind -eq "static" -or $resolved.Kind -eq "empty") {
                $panelAvatarStaticOrExternalCount++
                continue
            }
            if ($resolved.Kind -ne "object") {
                $invalidPanelAvatarRefs += [pscustomobject]@{ Id = [long]$row.id; Photo = $photo }
                continue
            }
            $panelAvatarObjectCount++
            $objectKey = Join-ObjectKey -Prefix $objectKeyPrefix -Domain "avatars" -LogicalKey ([string]$resolved.Filename)
            if (-not (Test-MinioObject -Docker $docker -ComposePrefix $composePrefix -ObjectKey $objectKey)) {
                $missingPanelAvatars += [pscustomobject]@{
                    Id = [long]$row.id
                    Photo = $photo
                    ObjectKey = $objectKey
                }
            }
        }
    }
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

Write-Host "[RESULT] STORAGE CUTOVER AUDIT"
Write-Host "[RESULT] object_bucket=$objectBucket"
Write-Host "[RESULT] object_key_prefix=$objectKeyPrefix"
Write-Host "[RESULT] attachment_mappings_checked=$attachmentCount"
Write-Host "[RESULT] missing_s3_dialog_objects=$($missingAttachments.Count)"
Write-Host "[RESULT] missing_metadata_rows=$missingMetadataRows"
Write-Host "[RESULT] panel_avatar_object_refs_checked=$panelAvatarObjectCount"
Write-Host "[RESULT] panel_avatar_static_or_external_refs=$panelAvatarStaticOrExternalCount"
Write-Host "[RESULT] missing_s3_panel_avatars=$($missingPanelAvatars.Count)"
Write-Host "[RESULT] invalid_panel_avatar_refs=$($invalidPanelAvatarRefs.Count)"

foreach ($item in ($missingAttachments | Select-Object -First 20)) {
    Write-Host "[WARN] attachment metadata_id=$($item.Id) storage_key=$($item.StorageKey) object_key=$($item.ObjectKey)"
}
foreach ($item in ($missingPanelAvatars | Select-Object -First 20)) {
    Write-Host "[WARN] panel_avatar user_id=$($item.Id) photo=$($item.Photo) object_key=$($item.ObjectKey)"
}
foreach ($item in ($invalidPanelAvatarRefs | Select-Object -First 20)) {
    Write-Host "[WARN] invalid_panel_avatar_ref user_id=$($item.Id) photo=$($item.Photo)"
}

$failureCount = $missingAttachments.Count + $missingPanelAvatars.Count + $invalidPanelAvatarRefs.Count
if ([int]$missingMetadataRows -gt 0) {
    $failureCount++
}

if ($failureCount -gt 0) {
    throw "Storage cutover audit failed. Keep APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true until all mapping gaps are resolved."
}

Write-Host "[GREEN] STORAGE CUTOVER AUDIT PASSED: canonical S3 mappings are complete for current attachment metadata and panel avatar references."
