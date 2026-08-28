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

function Test-StorageKeySafe {
    param([string]$Value)

    $normalized = Normalize-StorageKey -Value $Value
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        return $false
    }
    foreach ($segment in ($normalized -split "/")) {
        if ($segment -eq "." -or $segment -eq "..") {
            return $false
        }
    }
    return $true
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
        $bytes[$i / 2] = [Convert]::ToByte($Hex.Substring($i, 2), 16)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
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
    if (-not (Test-StorageKeySafe -Value $normalizedKey)) {
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

function Get-RepairState {
    param([string[]]$Output)

    foreach ($line in @($Output)) {
        $match = [regex]::Match([string]$line, '\[REPAIR_RESULT\]\s+(canonical|local|legacy|missing|error)')
        if ($match.Success) {
            return $match.Groups[1].Value
        }
    }
    return "error"
}

function Get-PanelRuntimeObjectKeyPrefix {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix
    )

    $result = Assert-ComposeSuccess `
        -Docker $Docker `
        -ComposePrefix $ComposePrefix `
        -Arguments @(
            "exec", "-T", "panel-web", "/bin/sh", "-c",
            'printf "%s" "${APP_STORAGE_OBJECT_KEY_PREFIX:-iguana}"'
        ) `
        -ErrorMessage "Unable to resolve APP_STORAGE_OBJECT_KEY_PREFIX from panel-web"
    return Normalize-ObjectKeyPrefix -Value ((($result.Output | ForEach-Object { [string]$_ }) -join "").Trim())
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$repairShellScript = Join-Path $repoRoot "scripts/internal/storage-repair-mapping.sh"
$attachmentsRoot = Join-Path $repoRoot "attachments"
$javaBotAttachmentsRoot = Join-Path $repoRoot "java-bot/attachments"
$dotEnv = Read-DotEnv -Path $dotEnvPath

if (-not (Test-Path -LiteralPath $repairShellScript -PathType Leaf)) {
    throw "Repair shell helper is missing: $repairShellScript"
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
$requestedPrefix = Normalize-ObjectKeyPrefix -Value (Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_KEY_PREFIX" -DefaultValue "iguana")

Assert-ComposeSuccess `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -Arguments @("config", "-q") `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

if ($ValidateOnly) {
    Write-Host "[GREEN] PowerShell parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] requested_object_key_prefix=$requestedPrefix"
    Write-Host "[RESULT] repair validation is read-only and no runtime data was accessed."
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

$rowsRaw = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') || chr(9) || encode(convert_to(COALESCE(legacy_attachment_ref, ''), 'UTF8'), 'hex') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"

$rootDescriptors = @(
    [pscustomobject]@{ Name = "attachments"; HostPath = $attachmentsRoot },
    [pscustomobject]@{ Name = "java-bot"; HostPath = $javaBotAttachmentsRoot }
)

$environmentNames = @(
    "IGUANA_REPAIR_ACCESS_KEY",
    "IGUANA_REPAIR_SECRET_KEY",
    "IGUANA_REPAIR_BUCKET",
    "IGUANA_REPAIR_CANONICAL_KEY",
    "IGUANA_REPAIR_LEGACY_KEY",
    "IGUANA_REPAIR_LOCAL_PATH",
    "COMPOSE_IGNORE_ORPHANS"
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) {
    $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}

$checked = 0
$alreadyCanonical = 0
$recoveredLocal = 0
$recoveredLegacy = 0
$missing = @()
$errors = @()

try {
    [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_ACCESS_KEY", $objectAccessKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_SECRET_KEY", $objectSecretKey, "Process")
    [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_BUCKET", $objectBucket, "Process")
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "true", "Process")

    foreach ($line in ($rowsRaw -split "`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }

        $parts = $line -split "`t", 3
        if ($parts.Count -ne 3) {
            $errors += "Malformed PostgreSQL row: $line"
            continue
        }

        $id = [long]$parts[0]
        $storageKey = Normalize-StorageKey -Value (ConvertFrom-HexUtf8 -Hex $parts[1])
        $legacyAttachmentRef = ConvertFrom-HexUtf8 -Hex $parts[2]
        if ($id -le 0 -or -not (Test-StorageKeySafe -Value $storageKey)) {
            $errors += "Unsafe or invalid storage key for metadata_id=$id"
            continue
        }

        $checked++
        $canonicalKey = Join-ObjectKey -Prefix $runtimePrefix -Domain "attachments" -LogicalKey $storageKey
        $legacyKey = Join-ObjectKey -Prefix "" -Domain "attachments" -LogicalKey $storageKey
        $source = Find-ExistingLegacyAttachment `
            -RootDescriptors $rootDescriptors `
            -StorageKey $storageKey `
            -LegacyAttachmentRef $legacyAttachmentRef

        $localContainerPath = ""
        if ($null -ne $source) {
            if ([string]$source.RootName -eq "java-bot") {
                $localContainerPath = "/workspace/java-bot/attachments/$storageKey"
            } else {
                $localContainerPath = "/workspace/attachments/$storageKey"
            }
        }

        [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_CANONICAL_KEY", $canonicalKey, "Process")
        [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_LEGACY_KEY", $legacyKey, "Process")
        [Environment]::SetEnvironmentVariable("IGUANA_REPAIR_LOCAL_PATH", $localContainerPath, "Process")

        $arguments = @(
            "run", "--rm", "--no-deps", "-T",
            "-e", "IGUANA_REPAIR_ACCESS_KEY",
            "-e", "IGUANA_REPAIR_SECRET_KEY",
            "-e", "IGUANA_REPAIR_BUCKET",
            "-e", "IGUANA_REPAIR_CANONICAL_KEY",
            "-e", "IGUANA_REPAIR_LEGACY_KEY",
            "-e", "IGUANA_REPAIR_LOCAL_PATH",
            "--volume", "${repoRoot}:/workspace:ro",
            "--entrypoint", "/bin/sh",
            "minio-init",
            "/workspace/scripts/internal/storage-repair-mapping.sh"
        )
        $result = Invoke-ComposeCapture -Docker $docker -ComposePrefix $composePrefix -Arguments $arguments
        $state = Get-RepairState -Output $result.Output

        switch ($state) {
            "canonical" {
                if ($result.ExitCode -eq 0) {
                    $alreadyCanonical++
                } else {
                    $errors += "metadata_id=$id canonical marker returned exit_code=$($result.ExitCode)"
                }
                continue
            }
            "local" {
                if ($result.ExitCode -eq 0) {
                    $recoveredLocal++
                } else {
                    $errors += "metadata_id=$id local marker returned exit_code=$($result.ExitCode)"
                }
                continue
            }
            "legacy" {
                if ($result.ExitCode -eq 0) {
                    $recoveredLegacy++
                } else {
                    $errors += "metadata_id=$id legacy marker returned exit_code=$($result.ExitCode)"
                }
                continue
            }
            "missing" {
                if ($result.ExitCode -eq 4) {
                    $missing += [pscustomobject]@{
                        Id = $id
                        StorageKey = $storageKey
                        HasLocalSource = ($null -ne $source)
                        LegacyKey = $legacyKey
                    }
                } else {
                    $errors += "metadata_id=$id missing marker returned unexpected exit_code=$($result.ExitCode)"
                }
                continue
            }
            default {
                $errors += "metadata_id=$id exit_code=$($result.ExitCode) output=$($result.Output -join ' ')"
            }
        }
    }
} finally {
    foreach ($name in $environmentNames) {
        [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], "Process")
    }
}

Write-Host "[RESULT] STORAGE MAPPING REPAIR"
Write-Host "[RESULT] object_bucket=$objectBucket"
Write-Host "[RESULT] object_key_prefix=$runtimePrefix"
Write-Host "[RESULT] metadata_rows_checked=$checked"
Write-Host "[RESULT] canonical_already_present=$alreadyCanonical"
Write-Host "[RESULT] recovered_from_local_source=$recoveredLocal"
Write-Host "[RESULT] recovered_from_legacy_minio=$recoveredLegacy"
Write-Host "[RESULT] still_missing=$($missing.Count)"
Write-Host "[RESULT] execution_errors=$($errors.Count)"
Write-Host "[RESULT] no database rows or local source files were modified."

foreach ($item in ($missing | Select-Object -First 20)) {
    Write-Host "[WARN] metadata_id=$($item.Id) local_source=$($item.HasLocalSource) storage_key=$($item.StorageKey) legacy_object_key=$($item.LegacyKey)"
}
if ($missing.Count -gt 20) {
    Write-Host "[WARN] Additional missing mappings omitted: $($missing.Count - 20)"
}
foreach ($errorText in ($errors | Select-Object -First 10)) {
    Write-Host "[WARN] repair_error=$errorText"
}
if ($errors.Count -gt 10) {
    Write-Host "[WARN] Additional repair errors omitted: $($errors.Count - 10)"
}

if ($missing.Count -eq 0 -and $errors.Count -eq 0) {
    Write-Host "[GREEN] STORAGE MAPPING REPAIR COMPLETE: every object-backed attachment metadata row has a canonical S3 object."
} else {
    Write-Host "[WARN] Storage mapping gaps remain. Keep APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=true."
}
