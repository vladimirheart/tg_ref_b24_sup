param(
    [string]$ProjectName = "",
    [switch]$Json
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-credential-migration-status.ps1."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Get-DockerCommandPath {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) {
        return $dockerCommand.Source
    }
    return $null
}

function Ensure-DockerAvailable {
    $dockerCommand = Get-DockerCommandPath
    if (-not $dockerCommand) {
        throw "Docker is not installed or not available in PATH."
    }

    & $dockerCommand version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker CLI is unavailable."
    }

    & $dockerCommand info *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker daemon is unavailable."
    }

    return $dockerCommand
}

function Invoke-DockerCli {
    param(
        [string]$DockerCommand,
        [string[]]$Arguments,
        [switch]$IgnoreExitCode
    )

    $output = & $DockerCommand @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    $text = (@($output) | ForEach-Object { "$_" }) -join [Environment]::NewLine
    if ($exitCode -ne 0 -and -not $IgnoreExitCode) {
        throw "docker $($Arguments -join ' ') failed with exit code $exitCode.$([Environment]::NewLine)$text"
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Text = $text
        Lines = @($output)
    }
}

function Read-DotEnvFile {
    param([string]$Path)

    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) {
        return $result
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
        $result[$name] = $value
    }

    return $result
}

function Get-SettingValue {
    param(
        [hashtable]$DotEnv,
        [string]$Name
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        return $fromEnvironment.Trim()
    }

    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($DotEnv[$Name])) {
        return [string]$DotEnv[$Name]
    }

    return ""
}

function Resolve-ComposeProjectName {
    param(
        [string]$ExplicitName,
        [hashtable]$DotEnv,
        [string]$RepoRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitName)) {
        return $ExplicitName.Trim()
    }

    $fromEnv = Get-SettingValue -DotEnv $DotEnv -Name "COMPOSE_PROJECT_NAME"
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        return $fromEnv.Trim()
    }

    return Split-Path -Leaf $RepoRoot
}

function Get-ComposeVolumeRecord {
    param(
        [string]$DockerCommand,
        [string]$ProjectName,
        [string]$VolumeLabel
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "volume", "ls",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--filter", "label=com.docker.compose.volume=$VolumeLabel",
        "--format", "{{.Name}}"
    ) -IgnoreExitCode
    $name = $result.Text.Trim()
    if (-not [string]::IsNullOrWhiteSpace($name)) {
        return [pscustomobject]@{
            Exists = $true
            Name = $name
        }
    }

    $fallbackName = "$ProjectName" + "_" + "$VolumeLabel"
    $fallback = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "volume", "ls",
        "--filter", "name=^$fallbackName$",
        "--format", "{{.Name}}"
    ) -IgnoreExitCode
    $fallbackResolved = $fallback.Text.Trim()
    if (-not [string]::IsNullOrWhiteSpace($fallbackResolved)) {
        return [pscustomobject]@{
            Exists = $true
            Name = $fallbackResolved
        }
    }

    return [pscustomobject]@{
        Exists = $false
        Name = $fallbackName
    }
}

function Get-ComposeContainerRecord {
    param(
        [string]$DockerCommand,
        [string]$ProjectName,
        [string]$ServiceName
    )

    $containerId = (Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "ps", "-aq",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--filter", "label=com.docker.compose.service=$ServiceName"
    ) -IgnoreExitCode).Text.Trim()
    if ([string]::IsNullOrWhiteSpace($containerId)) {
        return $null
    }

    $inspectJson = (Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @("inspect", $containerId)).Text
    $inspect = $inspectJson | ConvertFrom-Json
    if ($inspect -is [array]) {
        $inspect = $inspect[0]
    }

    return [pscustomobject]@{
        Id = $containerId
        Name = ([string]$inspect.Name).TrimStart("/")
        Running = (([string]$inspect.State.Status).Trim().ToLowerInvariant() -eq "running")
        Status = [string]$inspect.State.Status
        ConfigEnv = @($inspect.Config.Env)
    }
}

function Convert-EnvListToMap {
    param([string[]]$Entries)

    $result = @{}
    foreach ($entry in $Entries) {
        if ([string]::IsNullOrWhiteSpace($entry)) {
            continue
        }
        $separatorIndex = $entry.IndexOf("=")
        if ($separatorIndex -lt 1) {
            continue
        }
        $name = $entry.Substring(0, $separatorIndex)
        $value = $entry.Substring($separatorIndex + 1)
        $result[$name] = $value
    }
    return $result
}

function Test-DisallowedSecret {
    param(
        [string]$Value,
        [string[]]$DisallowedValues
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }

    foreach ($candidate in $DisallowedValues) {
        if ($Value.Trim() -eq $candidate) {
            return $true
        }
    }

    return $false
}

function New-ComponentStatus {
    param(
        [string]$Component,
        [string]$CredentialKind,
        [string]$Status,
        [string]$Reason,
        [string]$NextAction,
        [bool]$VolumePresent,
        [string]$VolumeName,
        [bool]$ContainerRunning,
        [string]$ContainerName,
        [string]$ConfiguredSecretName,
        [bool]$ConfiguredSecretPresent,
        [bool]$ConfiguredSecretNonDefault,
        [string]$VerificationMethod,
        [string]$VerificationStatus
    )

    return [pscustomobject]@{
        component = $Component
        credential_kind = $CredentialKind
        status = $Status
        reason = $Reason
        next_action = $NextAction
        volume_present = $VolumePresent
        volume_name = $VolumeName
        container_running = $ContainerRunning
        container_name = $ContainerName
        configured_secret_name = $ConfiguredSecretName
        configured_secret_present = $ConfiguredSecretPresent
        configured_secret_non_default = $ConfiguredSecretNonDefault
        verification_method = $VerificationMethod
        verification_status = $VerificationStatus
    }
}

function Get-ContainerNameOrEmpty {
    param($ContainerRecord)

    if ($ContainerRecord) {
        return [string]$ContainerRecord.Name
    }
    return ""
}

function Test-ContainerRunning {
    param($ContainerRecord)

    return [bool]($ContainerRecord -and $ContainerRecord.Running)
}

function Test-PostgresCredential {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$UserName,
        [string]$DatabaseName,
        [string]$Password
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        "-e", "PGPASSWORD=$Password",
        $ContainerId,
        "psql",
        "-h", "127.0.0.1",
        "-U", $UserName,
        "-d", $DatabaseName,
        "-Atqc", "SELECT 1"
    ) -IgnoreExitCode
    return $result.ExitCode -eq 0 -and $result.Text.Trim() -eq "1"
}

function Test-RabbitMqCredential {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$UserName,
        [string]$Password
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        $ContainerId,
        "rabbitmqctl",
        "authenticate_user",
        $UserName,
        $Password
    ) -IgnoreExitCode
    return $result.ExitCode -eq 0
}

function Test-RedisCredential {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$Password
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        $ContainerId,
        "redis-cli",
        "-a", $Password,
        "ping"
    ) -IgnoreExitCode
    return $result.ExitCode -eq 0 -and $result.Text -match "PONG"
}

function Test-GrafanaCredential {
    param(
        [string]$BindHost,
        [string]$Port,
        [string]$UserName,
        [string]$Password
    )

    try {
        $token = [Convert]::ToBase64String([System.Text.Encoding]::ASCII.GetBytes("$UserName`:$Password"))
        $headers = @{ Authorization = "Basic $token" }
        $response = Invoke-WebRequest -Uri "http://$BindHost`:$Port/api/user" -Headers $headers -UseBasicParsing -TimeoutSec 10
        return $response.StatusCode -eq 200
    } catch {
        return $false
    }
}

function Get-MinIoRuntimeEnvMatch {
    param(
        [hashtable]$ContainerEnv,
        [string]$ConfiguredAccessKey,
        [string]$ConfiguredSecretKey
    )

    if (-not $ContainerEnv.ContainsKey("MINIO_ROOT_USER") -or -not $ContainerEnv.ContainsKey("MINIO_ROOT_PASSWORD")) {
        return $false
    }

    return $ContainerEnv["MINIO_ROOT_USER"] -eq $ConfiguredAccessKey -and $ContainerEnv["MINIO_ROOT_PASSWORD"] -eq $ConfiguredSecretKey
}

$repoRoot = Get-RepoRoot
$dockerCommand = Ensure-DockerAvailable
$envPath = Join-Path $repoRoot ".env"
$dotEnv = Read-DotEnvFile -Path $envPath
$resolvedProjectName = Resolve-ComposeProjectName -ExplicitName $ProjectName -DotEnv $dotEnv -RepoRoot $repoRoot

$components = @()

$postgresVolume = Get-ComposeVolumeRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -VolumeLabel "iguana-postgres-data"
$postgresContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -ServiceName "postgres"
$postgresPassword = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_PASSWORD"
$postgresUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_USER"
$postgresDb = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_DB"
if ([string]::IsNullOrWhiteSpace($postgresUser)) { $postgresUser = "iguana" }
if ([string]::IsNullOrWhiteSpace($postgresDb)) { $postgresDb = "iguana" }
if (-not $postgresVolume.Exists) {
    $components += New-ComponentStatus -Component "postgresql" -CredentialKind "persisted role password" `
        -Status "fresh" -Reason "Compose-managed PostgreSQL volume was not found." `
        -NextAction "Safe to initialize PostgreSQL with secure bootstrap-generated credentials." `
        -VolumePresent $false -VolumeName $postgresVolume.Name -ContainerRunning $false -ContainerName "" `
        -ConfiguredSecretName "IGUANA_POSTGRES_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($postgresPassword)) `
        -ConfiguredSecretNonDefault (-not (Test-DisallowedSecret -Value $postgresPassword -DisallowedValues @("iguana"))) `
        -VerificationMethod "live_postgres_auth" -VerificationStatus "not_applicable"
} elseif (Test-DisallowedSecret -Value $postgresPassword -DisallowedValues @("iguana")) {
    $components += New-ComponentStatus -Component "postgresql" -CredentialKind "persisted role password" `
        -Status "migration_required" -Reason "A PostgreSQL volume already exists, but IGUANA_POSTGRES_PASSWORD is missing or still uses the documented default." `
        -NextAction "Do not overwrite .env blindly. Prepare controlled PostgreSQL password rotation and verify the new login before updating runtime config." `
        -VolumePresent $true -VolumeName $postgresVolume.Name -ContainerRunning (Test-ContainerRunning -ContainerRecord $postgresContainer) -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $postgresContainer) `
        -ConfiguredSecretName "IGUANA_POSTGRES_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($postgresPassword)) `
        -ConfiguredSecretNonDefault $false -VerificationMethod "live_postgres_auth" -VerificationStatus "blocked_by_default_secret"
} elseif (-not (Test-ContainerRunning -ContainerRecord $postgresContainer)) {
    $components += New-ComponentStatus -Component "postgresql" -CredentialKind "persisted role password" `
        -Status "migration_required" -Reason "A PostgreSQL volume exists, but no running PostgreSQL container is available for live credential verification." `
        -NextAction "Start the production contour, then rerun this status helper before rotating or trusting the configured password." `
        -VolumePresent $true -VolumeName $postgresVolume.Name -ContainerRunning $false -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $postgresContainer) `
        -ConfiguredSecretName "IGUANA_POSTGRES_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_postgres_auth" -VerificationStatus "container_not_running"
} elseif (Test-PostgresCredential -DockerCommand $dockerCommand -ContainerId $postgresContainer.Id -UserName $postgresUser -DatabaseName $postgresDb -Password $postgresPassword) {
    $components += New-ComponentStatus -Component "postgresql" -CredentialKind "persisted role password" `
        -Status "ready" -Reason "Configured PostgreSQL password authenticated successfully against the live persisted database." `
        -NextAction "You can keep the current password or plan an explicit rotation runbook with backup/rollback." `
        -VolumePresent $true -VolumeName $postgresVolume.Name -ContainerRunning $true -ContainerName $postgresContainer.Name `
        -ConfiguredSecretName "IGUANA_POSTGRES_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_postgres_auth" -VerificationStatus "authenticated"
} else {
    $components += New-ComponentStatus -Component "postgresql" -CredentialKind "persisted role password" `
        -Status "migration_required" -Reason "Configured PostgreSQL password did not authenticate against the live persisted database." `
        -NextAction "Treat this as a credential drift. Stop and run a controlled PostgreSQL rotation/recovery path before changing .env further." `
        -VolumePresent $true -VolumeName $postgresVolume.Name -ContainerRunning $true -ContainerName $postgresContainer.Name `
        -ConfiguredSecretName "IGUANA_POSTGRES_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_postgres_auth" -VerificationStatus "auth_failed"
}

$rabbitVolume = Get-ComposeVolumeRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -VolumeLabel "iguana-rabbitmq-data"
$rabbitContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -ServiceName "rabbitmq"
$rabbitPassword = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_RABBITMQ_PASSWORD"
$rabbitUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_RABBITMQ_USER"
if ([string]::IsNullOrWhiteSpace($rabbitUser)) { $rabbitUser = "iguana" }
if (-not $rabbitVolume.Exists) {
    $components += New-ComponentStatus -Component "rabbitmq" -CredentialKind "persisted default user password" `
        -Status "fresh" -Reason "Compose-managed RabbitMQ volume was not found." `
        -NextAction "Safe to initialize RabbitMQ with secure bootstrap-generated credentials." `
        -VolumePresent $false -VolumeName $rabbitVolume.Name -ContainerRunning $false -ContainerName "" `
        -ConfiguredSecretName "IGUANA_RABBITMQ_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($rabbitPassword)) `
        -ConfiguredSecretNonDefault (-not (Test-DisallowedSecret -Value $rabbitPassword -DisallowedValues @("iguana"))) `
        -VerificationMethod "live_rabbitmq_auth" -VerificationStatus "not_applicable"
} elseif (Test-DisallowedSecret -Value $rabbitPassword -DisallowedValues @("iguana")) {
    $components += New-ComponentStatus -Component "rabbitmq" -CredentialKind "persisted default user password" `
        -Status "migration_required" -Reason "A RabbitMQ volume already exists, but IGUANA_RABBITMQ_PASSWORD is missing or still uses the documented default." `
        -NextAction "Plan a controlled RabbitMQ password sync so queues/definitions survive and runtime config stays aligned." `
        -VolumePresent $true -VolumeName $rabbitVolume.Name -ContainerRunning (Test-ContainerRunning -ContainerRecord $rabbitContainer) -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $rabbitContainer) `
        -ConfiguredSecretName "IGUANA_RABBITMQ_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($rabbitPassword)) `
        -ConfiguredSecretNonDefault $false -VerificationMethod "live_rabbitmq_auth" -VerificationStatus "blocked_by_default_secret"
} elseif (-not (Test-ContainerRunning -ContainerRecord $rabbitContainer)) {
    $components += New-ComponentStatus -Component "rabbitmq" -CredentialKind "persisted default user password" `
        -Status "migration_required" -Reason "A RabbitMQ volume exists, but no running RabbitMQ container is available for live credential verification." `
        -NextAction "Start the contour, then rerun this helper before rotating RabbitMQ users or trusting the configured password." `
        -VolumePresent $true -VolumeName $rabbitVolume.Name -ContainerRunning $false -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $rabbitContainer) `
        -ConfiguredSecretName "IGUANA_RABBITMQ_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_rabbitmq_auth" -VerificationStatus "container_not_running"
} elseif (Test-RabbitMqCredential -DockerCommand $dockerCommand -ContainerId $rabbitContainer.Id -UserName $rabbitUser -Password $rabbitPassword) {
    $components += New-ComponentStatus -Component "rabbitmq" -CredentialKind "persisted default user password" `
        -Status "ready" -Reason "Configured RabbitMQ password authenticated successfully against the live broker." `
        -NextAction "You can keep the current broker credential or rotate it later through a controlled queue-safe workflow." `
        -VolumePresent $true -VolumeName $rabbitVolume.Name -ContainerRunning $true -ContainerName $rabbitContainer.Name `
        -ConfiguredSecretName "IGUANA_RABBITMQ_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_rabbitmq_auth" -VerificationStatus "authenticated"
} else {
    $components += New-ComponentStatus -Component "rabbitmq" -CredentialKind "persisted default user password" `
        -Status "migration_required" -Reason "Configured RabbitMQ password did not authenticate against the live broker." `
        -NextAction "Treat this as broker credential drift and execute a controlled RabbitMQ password synchronization before the next restart." `
        -VolumePresent $true -VolumeName $rabbitVolume.Name -ContainerRunning $true -ContainerName $rabbitContainer.Name `
        -ConfiguredSecretName "IGUANA_RABBITMQ_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_rabbitmq_auth" -VerificationStatus "auth_failed"
}

$redisVolume = Get-ComposeVolumeRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -VolumeLabel "iguana-redis-data"
$redisContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -ServiceName "redis"
$redisPassword = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_REDIS_PASSWORD"
if (-not $redisVolume.Exists) {
    $components += New-ComponentStatus -Component "redis" -CredentialKind "runtime password guarding a persisted dataset" `
        -Status "fresh" -Reason "Compose-managed Redis volume was not found." `
        -NextAction "Safe to initialize Redis with secure bootstrap-generated runtime password." `
        -VolumePresent $false -VolumeName $redisVolume.Name -ContainerRunning $false -ContainerName "" `
        -ConfiguredSecretName "IGUANA_REDIS_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($redisPassword)) `
        -ConfiguredSecretNonDefault (-not (Test-DisallowedSecret -Value $redisPassword -DisallowedValues @("iguana-redis"))) `
        -VerificationMethod "live_redis_auth" -VerificationStatus "not_applicable"
} elseif (Test-DisallowedSecret -Value $redisPassword -DisallowedValues @("iguana-redis")) {
    $components += New-ComponentStatus -Component "redis" -CredentialKind "runtime password guarding a persisted dataset" `
        -Status "migration_required" -Reason "A Redis volume already exists, but IGUANA_REDIS_PASSWORD is missing or still uses the documented default." `
        -NextAction "Prepare a coordinated Redis password switch for every client before changing runtime config." `
        -VolumePresent $true -VolumeName $redisVolume.Name -ContainerRunning (Test-ContainerRunning -ContainerRecord $redisContainer) -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $redisContainer) `
        -ConfiguredSecretName "IGUANA_REDIS_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($redisPassword)) `
        -ConfiguredSecretNonDefault $false -VerificationMethod "live_redis_auth" -VerificationStatus "blocked_by_default_secret"
} elseif (-not (Test-ContainerRunning -ContainerRecord $redisContainer)) {
    $components += New-ComponentStatus -Component "redis" -CredentialKind "runtime password guarding a persisted dataset" `
        -Status "migration_required" -Reason "A Redis volume exists, but no running Redis container is available for live credential verification." `
        -NextAction "Start the contour, rerun this helper, then coordinate a password switch across panel, bots and observability clients." `
        -VolumePresent $true -VolumeName $redisVolume.Name -ContainerRunning $false -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $redisContainer) `
        -ConfiguredSecretName "IGUANA_REDIS_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_redis_auth" -VerificationStatus "container_not_running"
} elseif (Test-RedisCredential -DockerCommand $dockerCommand -ContainerId $redisContainer.Id -Password $redisPassword) {
    $components += New-ComponentStatus -Component "redis" -CredentialKind "runtime password guarding a persisted dataset" `
        -Status "ready" -Reason "Configured Redis password authenticated successfully against the live runtime." `
        -NextAction "Keep the current password or plan a coordinated client switch with explicit restart sequencing." `
        -VolumePresent $true -VolumeName $redisVolume.Name -ContainerRunning $true -ContainerName $redisContainer.Name `
        -ConfiguredSecretName "IGUANA_REDIS_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_redis_auth" -VerificationStatus "authenticated"
} else {
    $components += New-ComponentStatus -Component "redis" -CredentialKind "runtime password guarding a persisted dataset" `
        -Status "migration_required" -Reason "Configured Redis password did not authenticate against the live runtime." `
        -NextAction "Treat this as runtime drift and fix the coordinated Redis password contract before restarting dependent services." `
        -VolumePresent $true -VolumeName $redisVolume.Name -ContainerRunning $true -ContainerName $redisContainer.Name `
        -ConfiguredSecretName "IGUANA_REDIS_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_redis_auth" -VerificationStatus "auth_failed"
}

$minioVolume = Get-ComposeVolumeRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -VolumeLabel "iguana-minio-data"
$minioContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -ServiceName "minio"
$minioAccessKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY"
$minioSecretKey = Get-SettingValue -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY"
$minioHasNonDefaultConfig = (-not (Test-DisallowedSecret -Value $minioAccessKey -DisallowedValues @("iguana-minio"))) -and `
    (-not (Test-DisallowedSecret -Value $minioSecretKey -DisallowedValues @("iguana-minio-secret")))
if (-not $minioVolume.Exists) {
    $components += New-ComponentStatus -Component "minio" -CredentialKind "runtime root credentials guarding a persisted object set" `
        -Status "fresh" -Reason "Compose-managed MinIO volume was not found." `
        -NextAction "Safe to initialize MinIO with secure bootstrap-generated object-storage credentials." `
        -VolumePresent $false -VolumeName $minioVolume.Name -ContainerRunning $false -ContainerName "" `
        -ConfiguredSecretName "APP_STORAGE_OBJECT_ACCESS_KEY,APP_STORAGE_OBJECT_SECRET_KEY" -ConfiguredSecretPresent ((-not [string]::IsNullOrWhiteSpace($minioAccessKey)) -and (-not [string]::IsNullOrWhiteSpace($minioSecretKey))) `
        -ConfiguredSecretNonDefault $minioHasNonDefaultConfig -VerificationMethod "live_runtime_env_match" -VerificationStatus "not_applicable"
} elseif (-not $minioHasNonDefaultConfig) {
    $components += New-ComponentStatus -Component "minio" -CredentialKind "runtime root credentials guarding a persisted object set" `
        -Status "migration_required" -Reason "A MinIO volume already exists, but object-storage credentials are missing or still use the documented defaults." `
        -NextAction "Replace default MinIO credentials through a controlled restart plan only after confirming how clients will switch." `
        -VolumePresent $true -VolumeName $minioVolume.Name -ContainerRunning (Test-ContainerRunning -ContainerRecord $minioContainer) -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $minioContainer) `
        -ConfiguredSecretName "APP_STORAGE_OBJECT_ACCESS_KEY,APP_STORAGE_OBJECT_SECRET_KEY" -ConfiguredSecretPresent ((-not [string]::IsNullOrWhiteSpace($minioAccessKey)) -and (-not [string]::IsNullOrWhiteSpace($minioSecretKey))) `
        -ConfiguredSecretNonDefault $false -VerificationMethod "live_runtime_env_match" -VerificationStatus "blocked_by_default_secret"
} elseif (-not (Test-ContainerRunning -ContainerRecord $minioContainer)) {
    $components += New-ComponentStatus -Component "minio" -CredentialKind "runtime root credentials guarding a persisted object set" `
        -Status "migration_required" -Reason "A MinIO volume exists, but no running MinIO container is available to confirm the live runtime credential contract." `
        -NextAction "Start the contour and rerun this helper before treating MinIO credentials as aligned with the persisted bucket/object set." `
        -VolumePresent $true -VolumeName $minioVolume.Name -ContainerRunning $false -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $minioContainer) `
        -ConfiguredSecretName "APP_STORAGE_OBJECT_ACCESS_KEY,APP_STORAGE_OBJECT_SECRET_KEY" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "live_runtime_env_match" -VerificationStatus "container_not_running"
} else {
    $minioRuntimeEnv = Convert-EnvListToMap -Entries $minioContainer.ConfigEnv
    if (Get-MinIoRuntimeEnvMatch -ContainerEnv $minioRuntimeEnv -ConfiguredAccessKey $minioAccessKey -ConfiguredSecretKey $minioSecretKey) {
        $components += New-ComponentStatus -Component "minio" -CredentialKind "runtime root credentials guarding a persisted object set" `
            -Status "ready" -Reason "Configured MinIO credentials match the live container runtime environment for the existing object-storage volume." `
            -NextAction "Keep the current credentials or plan an explicit client-coordinated secret rotation." `
            -VolumePresent $true -VolumeName $minioVolume.Name -ContainerRunning $true -ContainerName $minioContainer.Name `
            -ConfiguredSecretName "APP_STORAGE_OBJECT_ACCESS_KEY,APP_STORAGE_OBJECT_SECRET_KEY" -ConfiguredSecretPresent $true `
            -ConfiguredSecretNonDefault $true -VerificationMethod "live_runtime_env_match" -VerificationStatus "matched"
    } else {
        $components += New-ComponentStatus -Component "minio" -CredentialKind "runtime root credentials guarding a persisted object set" `
            -Status "migration_required" -Reason "Configured MinIO credentials do not match the live container runtime environment for the existing object-storage volume." `
            -NextAction "Treat this as object-storage credential drift and reconcile runtime config before the next restart or cutover." `
            -VolumePresent $true -VolumeName $minioVolume.Name -ContainerRunning $true -ContainerName $minioContainer.Name `
            -ConfiguredSecretName "APP_STORAGE_OBJECT_ACCESS_KEY,APP_STORAGE_OBJECT_SECRET_KEY" -ConfiguredSecretPresent $true `
            -ConfiguredSecretNonDefault $true -VerificationMethod "live_runtime_env_match" -VerificationStatus "mismatched"
    }
}

$grafanaVolume = Get-ComposeVolumeRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -VolumeLabel "iguana-grafana-data"
$grafanaContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $resolvedProjectName -ServiceName "grafana"
$grafanaUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_ADMIN_USER"
$grafanaPassword = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_ADMIN_PASSWORD"
$grafanaBindHost = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_BIND_HOST"
$grafanaPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_PORT"
if ([string]::IsNullOrWhiteSpace($grafanaUser)) { $grafanaUser = "admin" }
if ([string]::IsNullOrWhiteSpace($grafanaBindHost)) { $grafanaBindHost = "127.0.0.1" }
if ([string]::IsNullOrWhiteSpace($grafanaPort)) { $grafanaPort = "3000" }
if (-not $grafanaVolume.Exists) {
    $components += New-ComponentStatus -Component "grafana" -CredentialKind "persisted admin credential" `
        -Status "fresh" -Reason "Compose-managed Grafana volume was not found." `
        -NextAction "If observability is enabled later, initialize Grafana with a non-default admin password." `
        -VolumePresent $false -VolumeName $grafanaVolume.Name -ContainerRunning $false -ContainerName "" `
        -ConfiguredSecretName "IGUANA_GRAFANA_ADMIN_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($grafanaPassword)) `
        -ConfiguredSecretNonDefault (-not (Test-DisallowedSecret -Value $grafanaPassword -DisallowedValues @("change-me", "admin", "grafana"))) `
        -VerificationMethod "http_basic_auth:/api/user" -VerificationStatus "not_applicable"
} elseif (Test-DisallowedSecret -Value $grafanaPassword -DisallowedValues @("change-me", "admin", "grafana")) {
    $components += New-ComponentStatus -Component "grafana" -CredentialKind "persisted admin credential" `
        -Status "migration_required" -Reason "A Grafana volume already exists, but IGUANA_GRAFANA_ADMIN_PASSWORD is missing or still uses the documented default placeholder." `
        -NextAction "Prepare an explicit Grafana admin reset workflow against the persisted DB before changing runtime config." `
        -VolumePresent $true -VolumeName $grafanaVolume.Name -ContainerRunning (Test-ContainerRunning -ContainerRecord $grafanaContainer) -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $grafanaContainer) `
        -ConfiguredSecretName "IGUANA_GRAFANA_ADMIN_PASSWORD" -ConfiguredSecretPresent (-not [string]::IsNullOrWhiteSpace($grafanaPassword)) `
        -ConfiguredSecretNonDefault $false -VerificationMethod "http_basic_auth:/api/user" -VerificationStatus "blocked_by_default_secret"
} elseif (-not (Test-ContainerRunning -ContainerRecord $grafanaContainer)) {
    $components += New-ComponentStatus -Component "grafana" -CredentialKind "persisted admin credential" `
        -Status "migration_required" -Reason "A Grafana volume exists, but no running Grafana container is available for live admin authentication verification." `
        -NextAction "Start observability and rerun this helper before rotating or trusting the configured admin password." `
        -VolumePresent $true -VolumeName $grafanaVolume.Name -ContainerRunning $false -ContainerName (Get-ContainerNameOrEmpty -ContainerRecord $grafanaContainer) `
        -ConfiguredSecretName "IGUANA_GRAFANA_ADMIN_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "http_basic_auth:/api/user" -VerificationStatus "container_not_running"
} elseif (Test-GrafanaCredential -BindHost $grafanaBindHost -Port $grafanaPort -UserName $grafanaUser -Password $grafanaPassword) {
    $components += New-ComponentStatus -Component "grafana" -CredentialKind "persisted admin credential" `
        -Status "ready" -Reason "Configured Grafana admin password authenticated successfully against the live API." `
        -NextAction "Keep the current admin password or rotate it later through a controlled observability runbook." `
        -VolumePresent $true -VolumeName $grafanaVolume.Name -ContainerRunning $true -ContainerName $grafanaContainer.Name `
        -ConfiguredSecretName "IGUANA_GRAFANA_ADMIN_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "http_basic_auth:/api/user" -VerificationStatus "authenticated"
} else {
    $components += New-ComponentStatus -Component "grafana" -CredentialKind "persisted admin credential" `
        -Status "migration_required" -Reason "Configured Grafana admin password did not authenticate against the live API for the existing persisted DB." `
        -NextAction "Treat this as Grafana admin drift and execute a controlled admin reset/rotation path before the next restart." `
        -VolumePresent $true -VolumeName $grafanaVolume.Name -ContainerRunning $true -ContainerName $grafanaContainer.Name `
        -ConfiguredSecretName "IGUANA_GRAFANA_ADMIN_PASSWORD" -ConfiguredSecretPresent $true `
        -ConfiguredSecretNonDefault $true -VerificationMethod "http_basic_auth:/api/user" -VerificationStatus "auth_failed"
}

$migrationRequiredCount = @($components | Where-Object { $_.status -eq "migration_required" }).Count
$freshCount = @($components | Where-Object { $_.status -eq "fresh" }).Count
$readyCount = @($components | Where-Object { $_.status -eq "ready" }).Count
$overallStatus = if ($migrationRequiredCount -gt 0) {
    "migration_required"
} elseif ($freshCount -eq $components.Count) {
    "fresh"
} elseif ($readyCount -eq $components.Count) {
    "ready"
} else {
    "mixed"
}

$report = [pscustomobject]@{
    checked_at = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ssK")
    project_name = $resolvedProjectName
    env_file = $envPath
    overall_status = $overallStatus
    summary = [pscustomobject]@{
        total = $components.Count
        fresh = $freshCount
        ready = $readyCount
        migration_required = $migrationRequiredCount
    }
    components = $components
}

if ($Json) {
    $report | ConvertTo-Json -Depth 6
    exit 0
}

Write-Host ("[INFO] Project: {0}" -f $report.project_name)
Write-Host ("[INFO] Overall status: {0}" -f $report.overall_status)
Write-Host ("[INFO] Summary: total={0}, fresh={1}, ready={2}, migration_required={3}" -f $report.summary.total, $report.summary.fresh, $report.summary.ready, $report.summary.migration_required)
Write-Host ""
$components | Select-Object component, status, verification_status, reason, next_action | Format-Table -Wrap -AutoSize
