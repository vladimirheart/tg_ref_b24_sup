param(
    [switch]$RestartRuntime
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
        return [string]$DotEnv[$Name]
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

function Query-Postgres {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $Docker @ComposePrefix exec -T postgres psql -U $User -d $Database -Atc $Sql 2>&1
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

function Invoke-MinioMirror {
    param(
        [string]$Docker,
        [string]$NetworkName,
        [string]$SourcePath,
        [string]$AccessKey,
        [string]$SecretKey,
        [string]$Bucket
    )

    $mcScriptTemplate = @'
set -eu
mc alias set local http://minio:9000 "__ACCESS_KEY__" "__SECRET_KEY__" >/dev/null
mc mb --ignore-existing local/__BUCKET__ >/dev/null
mc mirror --overwrite --exclude "avatars/*" --exclude "knowledge_base/*" --exclude "passport_photos/*" /source local/__BUCKET__/attachments >/dev/null
if [ -d /source/avatars ]; then
  mc mirror --overwrite /source/avatars local/__BUCKET__/avatars >/dev/null
fi
if [ -d /source/knowledge_base ]; then
  mc mirror --overwrite /source/knowledge_base local/__BUCKET__/knowledge_base >/dev/null
fi
if [ -d /source/passport_photos ]; then
  mc mirror --overwrite /source/passport_photos local/__BUCKET__/passport_photos >/dev/null
fi
'@
    $mcScript = $mcScriptTemplate.Replace("__ACCESS_KEY__", $AccessKey).Replace("__SECRET_KEY__", $SecretKey).Replace("__BUCKET__", $Bucket)

    & $Docker run --rm `
        --network $NetworkName `
        --mount "type=bind,src=$SourcePath,dst=/source,readonly" `
        --entrypoint /bin/sh `
        minio/mc:RELEASE.2025-07-21T05-28-08Z `
        -lc $mcScript

    if ($LASTEXITCODE -ne 0) {
        throw "MinIO mirror failed with exit code ${LASTEXITCODE}."
    }
}

function Convert-StorageKeyToLocalPath {
    param(
        [string]$RootPath,
        [string]$StorageKey
    )

    $relative = $StorageKey.Replace("/", [System.IO.Path]::DirectorySeparatorChar)
    return [System.IO.Path]::GetFullPath((Join-Path $RootPath $relative))
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$observabilityCompose = Join-Path $repoRoot "docker-compose.production-observability.yml"
$attachmentsRoot = Join-Path $repoRoot "attachments"
$dotEnv = Read-DotEnv -Path $dotEnvPath
$projectName = [System.IO.Path]::GetFileName($repoRoot)
$networkName = "${projectName}_default"

if (-not (Test-Path -LiteralPath $attachmentsRoot -PathType Container)) {
    throw "Attachments root is missing: $attachmentsRoot"
}

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $baseCompose, "-f", $observabilityCompose)

$dbUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_USER" -DefaultValue "iguana"
$dbName = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_DB" -DefaultValue "iguana"
$objectAccessKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -DefaultValue "iguana-minio"
$objectSecretKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -DefaultValue "iguana-minio-secret"
$objectBucket = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -DefaultValue "iguana"

$runningServicesRaw = & $docker @composePrefix ps --status running --services
if ($LASTEXITCODE -ne 0) {
    throw "Unable to inspect production contour services."
}
$runningServices = @($runningServicesRaw | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })

foreach ($required in @("postgres", "minio")) {
    if ($runningServices -notcontains $required) {
        throw "Required service is not running: $required"
    }
}

Write-Host "[INFO] Mirroring legacy media into MinIO bucket '$objectBucket'..."
Invoke-MinioMirror `
    -Docker $docker `
    -NetworkName $networkName `
    -SourcePath $attachmentsRoot `
    -AccessKey $objectAccessKey `
    -SecretKey $objectSecretKey `
    -Bucket $objectBucket

$metadataRows = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT id || '|' || storage_key FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' ORDER BY id"

$availableIds = New-Object System.Collections.Generic.List[string]

if (-not [string]::IsNullOrWhiteSpace($metadataRows)) {
    foreach ($line in ($metadataRows -split "`n")) {
        if ([string]::IsNullOrWhiteSpace($line)) {
            continue
        }
        $parts = $line.Split("|", 2, [System.StringSplitOptions]::None)
        if ($parts.Length -ne 2) {
            continue
        }
        $id = $parts[0].Trim()
        $storageKey = $parts[1].Trim()
        if ([string]::IsNullOrWhiteSpace($id) -or [string]::IsNullOrWhiteSpace($storageKey)) {
            continue
        }
        $candidatePath = Convert-StorageKeyToLocalPath -RootPath $attachmentsRoot -StorageKey $storageKey
        if ($candidatePath.StartsWith($attachmentsRoot, [System.StringComparison]::OrdinalIgnoreCase) `
                -and (Test-Path -LiteralPath $candidatePath -PathType Leaf)) {
            $availableIds.Add($id)
        }
    }
}

if ($availableIds.Count -gt 0) {
    $idList = ($availableIds | Sort-Object -Unique) -join ", "
    $updateSql = @"
UPDATE chat_attachment_metadata
   SET storage_provider = 's3',
       normalization_status = 'normalized',
       availability_status = 'available',
       updated_at = CURRENT_TIMESTAMP
 WHERE id IN ($idList);
"@
    Write-Host "[INFO] Marking $($availableIds.Count) attachment metadata row(s) as available..."
    Execute-Postgres `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql $updateSql
} else {
    Write-Host "[WARN] No attachment metadata rows with verified legacy files were found."
}

$statusSummary = Query-Postgres `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -User $dbUser `
    -Database $dbName `
    -Sql "SELECT availability_status || '=' || COUNT(*) FROM chat_attachment_metadata GROUP BY availability_status ORDER BY availability_status"

if ($RestartRuntime) {
    Write-Host "[INFO] Restarting panel runtime containers so health checks reconnect cleanly..."
    Invoke-Compose -Docker $docker -ComposePrefix $composePrefix -Arguments @("restart", "panel-web", "ops-worker")
}

Write-Host "[RESULT] Legacy storage backfill completed."
Write-Host "[RESULT] attachments_root=$attachmentsRoot"
Write-Host "[RESULT] metadata_rows_marked_available=$($availableIds.Count)"
if (-not [string]::IsNullOrWhiteSpace($statusSummary)) {
    foreach ($line in ($statusSummary -split "`n")) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            Write-Host "[RESULT] $line"
        }
    }
}
