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
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) { continue }
        $values[$trimmed.Substring(0, $separatorIndex).Trim()] = $trimmed.Substring($separatorIndex + 1).Trim()
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

function Assert-ComposeSuccess {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )
    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Query-Postgres {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )
    $result = Assert-ComposeSuccess `
        -Docker $Docker `
        -ComposePrefix $ComposePrefix `
        -Arguments @("exec", "-T", "postgres", "psql", "-U", $User, "-d", $Database, "-Atc", $Sql) `
        -ErrorMessage "PostgreSQL query failed"
    return (($result.Output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Normalize-ObjectKeyPrefix {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $normalized = $Value.Trim().Replace("\", "/")
    while ($normalized.StartsWith("/")) { $normalized = $normalized.Substring(1) }
    while ($normalized.EndsWith("/")) { $normalized = $normalized.Substring(0, $normalized.Length - 1) }
    return $normalized.Trim()
}

function Join-AvatarObjectKey {
    param(
        [string]$Prefix,
        [string]$Filename
    )
    $normalizedPrefix = Normalize-ObjectKeyPrefix -Value $Prefix
    $normalizedFilename = $Filename.Trim().Replace("\", "/")
    while ($normalizedFilename.StartsWith("/")) { $normalizedFilename = $normalizedFilename.Substring(1) }
    if ([string]::IsNullOrWhiteSpace($normalizedPrefix)) {
        return "avatars/$normalizedFilename"
    }
    return "$normalizedPrefix/avatars/$normalizedFilename"
}

function Get-ClientAvatarObjectKeys {
    param(
        [string]$Prefix,
        [long]$UserId,
        [bool]$Full
    )

    $base = if ($Full) { "${UserId}_full" } else { "$UserId" }
    $extensions = @(".jpg", ".jpeg", ".png", ".gif", ".webp")
    $keys = @()
    foreach ($extension in $extensions) {
        $keys += Join-AvatarObjectKey -Prefix $Prefix -Filename ($base + $extension)
    }
    return $keys
}

function Get-LegacyLocalClientAvatarUsers {
    param([string]$RepoRoot)

    $avatarsRoot = Join-Path $RepoRoot "attachments/avatars"
    if (-not (Test-Path -LiteralPath $avatarsRoot -PathType Container)) {
        return @()
    }

    $users = New-Object 'System.Collections.Generic.HashSet[long]'
    foreach ($file in @(Get-ChildItem -LiteralPath $avatarsRoot -File -Recurse -Force -ErrorAction Stop)) {
        if ([string]::IsNullOrWhiteSpace($file.Name)) {
            continue
        }
        if ($file.Name -match '^(?<userId>\d+)(?:_full)?\.(jpg|jpeg|png|gif|webp)$') {
            [void]$users.Add([long]$matches.userId)
        }
    }

    return @($users | Sort-Object)
}

function ConvertTo-LfLineEndings {
    param([string]$Value)
    if ($null -eq $Value) { return "" }
    return $Value.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Test-MinioObject {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$ObjectKey
    )
    [Environment]::SetEnvironmentVariable("IGUANA_CLIENT_AVATAR_AUDIT_OBJECT_KEY", $ObjectKey, "Process")
    $shellCommand = @'
set -eu
mc alias set local http://minio:9000 "$IGUANA_CLIENT_AVATAR_AUDIT_ACCESS_KEY" "$IGUANA_CLIENT_AVATAR_AUDIT_SECRET_KEY" >/dev/null
mc stat "local/$IGUANA_CLIENT_AVATAR_AUDIT_BUCKET/$IGUANA_CLIENT_AVATAR_AUDIT_OBJECT_KEY" >/dev/null
'@
    $normalizedShellCommand = ConvertTo-LfLineEndings -Value $shellCommand
    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments @(
        "run", "--rm", "--no-deps", "-T",
        "-e", "IGUANA_CLIENT_AVATAR_AUDIT_ACCESS_KEY",
        "-e", "IGUANA_CLIENT_AVATAR_AUDIT_SECRET_KEY",
        "-e", "IGUANA_CLIENT_AVATAR_AUDIT_BUCKET",
        "-e", "IGUANA_CLIENT_AVATAR_AUDIT_OBJECT_KEY",
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
    $result = Assert-ComposeSuccess `
        -Docker $Docker `
        -ComposePrefix $ComposePrefix `
        -Arguments @("exec", "-T", "panel-web", "/bin/sh", "-c", 'printf "%s" "${APP_STORAGE_OBJECT_KEY_PREFIX:-iguana}"') `
        -ErrorMessage "Unable to resolve APP_STORAGE_OBJECT_KEY_PREFIX from panel-web"
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
$requestedPrefix = Normalize-ObjectKeyPrefix -Value (Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_KEY_PREFIX" -DefaultValue "iguana")

Assert-ComposeSuccess `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -Arguments @("config", "-q") `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

if ($ValidateOnly) {
    Write-Host "[GREEN] PowerShell parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] requested_object_key_prefix=$requestedPrefix"
    Write-Host "[RESULT] audit_sources=client_avatar_history,attachments/avatars"
    Write-Host "[RESULT] client_avatar_filename_variants=.jpg,.jpeg,.png,.gif,.webp"
    Write-Host "[RESULT] client-avatar audit is read-only and no runtime data was accessed."
    return
}

$running = Assert-ComposeSuccess `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -Arguments @("ps", "--status", "running", "--services") `
    -ErrorMessage "Unable to inspect production contour services"
$runningServices = @($running.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
foreach ($required in @("postgres", "minio", "panel-web")) {
    if ($runningServices -notcontains $required) {
        throw "Required service is not running: $required"
    }
}

$runtimePrefix = Get-PanelRuntimeObjectKeyPrefix -Docker $docker -ComposePrefix $composePrefix
if ($runtimePrefix -ne $requestedPrefix) {
    throw "Object key prefix mismatch: launcher/.env requests '$requestedPrefix' but panel-web uses '$runtimePrefix'."
}

$clientRowsRaw = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT json_build_object('user_id', user_id)::text FROM (SELECT DISTINCT user_id FROM client_avatar_history WHERE user_id IS NOT NULL AND user_id > 0) q ORDER BY user_id"

$historyUserIds = New-Object 'System.Collections.Generic.HashSet[long]'
if (-not [string]::IsNullOrWhiteSpace($clientRowsRaw)) {
    foreach ($line in ($clientRowsRaw -split "`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $row = $line | ConvertFrom-Json
        $userId = [long]$row.user_id
        if ($userId -le 0) { continue }
        [void]$historyUserIds.Add($userId)
    }
}

$legacyLocalUserIds = @(Get-LegacyLocalClientAvatarUsers -RepoRoot $repoRoot)
$allUserIds = New-Object 'System.Collections.Generic.HashSet[long]'
$userSources = @{}
foreach ($userId in @($historyUserIds)) {
    [void]$allUserIds.Add($userId)
    $userSources[$userId] = New-Object 'System.Collections.Generic.HashSet[string]'
    [void]$userSources[$userId].Add("client_avatar_history")
}
foreach ($userId in $legacyLocalUserIds) {
    [void]$allUserIds.Add($userId)
    if (-not $userSources.ContainsKey($userId)) {
        $userSources[$userId] = New-Object 'System.Collections.Generic.HashSet[string]'
    }
    [void]$userSources[$userId].Add("attachments/avatars")
}

$environmentNames = @(
    "IGUANA_CLIENT_AVATAR_AUDIT_ACCESS_KEY",
    "IGUANA_CLIENT_AVATAR_AUDIT_SECRET_KEY",
    "IGUANA_CLIENT_AVATAR_AUDIT_BUCKET",
    "IGUANA_CLIENT_AVATAR_AUDIT_OBJECT_KEY"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$checkedClients = 0
$missingClients = @()
try {
    [Environment]::SetEnvironmentVariable("IGUANA_CLIENT_AVATAR_AUDIT_ACCESS_KEY", $objectAccessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_CLIENT_AVATAR_AUDIT_SECRET_KEY", $objectSecretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_CLIENT_AVATAR_AUDIT_BUCKET", $objectBucket, "Process")

    foreach ($userId in @($allUserIds | Sort-Object)) {
        if ($userId -le 0) { continue }
        $checkedClients++
        $candidateKeys = @()
        $candidateKeys += @(Get-ClientAvatarObjectKeys -Prefix $runtimePrefix -UserId $userId -Full $false)
        $candidateKeys += @(Get-ClientAvatarObjectKeys -Prefix $runtimePrefix -UserId $userId -Full $true)
        $foundAny = $false
        foreach ($objectKey in $candidateKeys) {
            if (Test-MinioObject -Docker $docker -ComposePrefix $composePrefix -ObjectKey $objectKey) {
                $foundAny = $true
                break
            }
        }
        if (-not $foundAny) {
            $missingClients += [pscustomobject]@{
                UserId = $userId
                CandidateKeys = $candidateKeys
                Sources = @($userSources[$userId] | Sort-Object) -join ","
            }
        }
    }
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

Write-Host "[RESULT] CLIENT AVATAR CUTOVER AUDIT"
Write-Host "[RESULT] object_bucket=$objectBucket"
Write-Host "[RESULT] object_key_prefix=$runtimePrefix"
Write-Host "[RESULT] client_avatar_filename_variants=.jpg,.jpeg,.png,.gif,.webp"
Write-Host "[RESULT] client_avatar_history_users_checked=$($historyUserIds.Count)"
Write-Host "[RESULT] legacy_local_avatar_users_checked=$($legacyLocalUserIds.Count)"
Write-Host "[RESULT] effective_client_avatar_users_checked=$checkedClients"
Write-Host "[RESULT] missing_s3_client_avatars=$($missingClients.Count)"
foreach ($item in ($missingClients | Select-Object -First 20)) {
    Write-Host "[WARN] client_avatar user_id=$($item.UserId) sources=$($item.Sources) expected_any_of=$(@($item.CandidateKeys) -join ';')"
}
if ($missingClients.Count -gt 20) {
    Write-Host "[WARN] Additional missing client avatars omitted: $($missingClients.Count - 20)"
}

if ($missingClients.Count -gt 0) {
    throw "Client avatar cutover audit failed. Keep APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true until mapped client avatar objects are restored."
}

Write-Host "[GREEN] CLIENT AVATAR CUTOVER AUDIT PASSED: every client avatar candidate from avatar history or legacy local avatar roots has at least one canonical runtime avatar object."
