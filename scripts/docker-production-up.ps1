param(
    [switch]$Telegram,
    [switch]$Vk,
    [switch]$Max,
    [switch]$Edge,
    [switch]$Observability,
    [switch]$Backup,
    [switch]$Build,
    [switch]$NoDetach,
    [switch]$ValidateOnly,
    [switch]$AllowInsecureDefaults,
    [int]$WebReplicas = 0,
    [int]$WorkerReplicas = 0
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-up.ps1."
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

    & $dockerCommand compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose is unavailable."
    }

    return $dockerCommand
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

function Resolve-ReplicaCount {
    param(
        [int]$ExplicitValue,
        [hashtable]$DotEnv,
        [string]$SettingName,
        [int]$DefaultValue
    )

    if ($ExplicitValue -gt 0) {
        return $ExplicitValue
    }

    $rawValue = Get-SettingValue -DotEnv $DotEnv -Name $SettingName
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return $DefaultValue
    }

    $parsed = 0
    if (-not [int]::TryParse($rawValue, [ref]$parsed) -or $parsed -lt 1) {
        throw "$SettingName must be a positive integer."
    }
    return $parsed
}

function Test-TruthySetting {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    switch ($Value.Trim().ToLowerInvariant()) {
        "1" { return $true }
        "true" { return $true }
        "yes" { return $true }
        "on" { return $true }
        default { return $false }
    }
}

function Resolve-RepoPathFromSetting {
    param(
        [string]$RepoRoot,
        [hashtable]$DotEnv,
        [string]$Name,
        [string]$DefaultValue
    )

    $value = Get-SettingValue -DotEnv $DotEnv -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = $DefaultValue
    }

    if ([System.IO.Path]::IsPathRooted($value)) {
        return [System.IO.Path]::GetFullPath($value)
    }

    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $value))
}

function Assert-RequiredFile {
    param(
        [string]$Path,
        [string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "$Label is missing: $Path"
    }
}

function Assert-RequiredSetting {
    param(
        [hashtable]$DotEnv,
        [string]$Name,
        [string]$Message
    )

    $value = Get-SettingValue -DotEnv $DotEnv -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "$Message ($Name). Configure it via process environment or repository .env."
    }
    return $value
}

function Assert-NonDefaultSecret {
    param(
        [hashtable]$DotEnv,
        [string]$Name,
        [string[]]$DisallowedValues,
        [string]$Message
    )

    $value = Assert-RequiredSetting -DotEnv $DotEnv -Name $Name -Message $Message
    foreach ($candidate in $DisallowedValues) {
        if ($value -eq $candidate) {
            throw "$Message ($Name uses disallowed default '$candidate'). Update repository .env or process environment."
        }
    }
    return $value
}

function Invoke-PreflightChecks {
    param(
        [string]$RepoRoot,
        [hashtable]$DotEnv,
        [string[]]$Profiles,
        [bool]$EdgeEnabled,
        [bool]$ObservabilityEnabled,
        [bool]$BackupEnabled,
        [bool]$AllowInsecure
    )

    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\settings.json") -Label "Shared config settings"
    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\locations.json") -Label "Shared config locations"
    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\org_structure.json") -Label "Shared config org structure"

    if ($AllowInsecure) {
        Assert-RequiredSetting -DotEnv $DotEnv -Name "APP_INTERNAL_BOT_API_TOKEN" -Message "Internal bot API token must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "APP_SECURITY_REMEMBER_ME_KEY" -Message "Remember-me key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "MONITORING_CREDENTIALS_MASTER_KEY" -Message "Shared monitoring credentials master key is required by split backend roles" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_POSTGRES_PASSWORD" -Message "PostgreSQL password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_RABBITMQ_PASSWORD" -Message "RabbitMQ password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_REDIS_PASSWORD" -Message "Redis password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -Message "Object storage access key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -Message "Object storage secret key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -Message "Object storage bucket must be configured" | Out-Null
    } else {
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "APP_INTERNAL_BOT_API_TOKEN" -DisallowedValues @("change-me", "iguana-internal-bot-token") -Message "Internal bot API token must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "APP_SECURITY_REMEMBER_ME_KEY" -DisallowedValues @("change-me", "iguana-panel-remember-me") -Message "Remember-me key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "MONITORING_CREDENTIALS_MASTER_KEY" -DisallowedValues @("change-me", "iguana-monitoring-key") -Message "Shared monitoring credentials master key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "IGUANA_POSTGRES_PASSWORD" -DisallowedValues @("iguana") -Message "PostgreSQL password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "IGUANA_RABBITMQ_PASSWORD" -DisallowedValues @("iguana") -Message "RabbitMQ password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "IGUANA_REDIS_PASSWORD" -DisallowedValues @("iguana-redis") -Message "Redis password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -DisallowedValues @("iguana-minio") -Message "Object storage access key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -DisallowedValues @("iguana-minio-secret") -Message "Object storage secret key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $DotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -DisallowedValues @("iguana") -Message "Object storage bucket must be overridden for production-like launch" | Out-Null
    }

    if ($ObservabilityEnabled) {
        if ($AllowInsecure) {
            Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_GRAFANA_ADMIN_PASSWORD" -Message "Grafana admin password must be configured" | Out-Null
        } else {
            Assert-NonDefaultSecret -DotEnv $DotEnv -Name "IGUANA_GRAFANA_ADMIN_PASSWORD" -DisallowedValues @("change-me", "admin", "grafana") -Message "Grafana admin password must be overridden" | Out-Null
        }
    }

    if ($BackupEnabled) {
        $backupDestination = Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_BACKUP_DESTINATION_DIR" -Message "Backup contour requires an off-host mounted destination"
        $backupFailureDomain = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN"
        if (-not (Test-TruthySetting -Value $backupFailureDomain)) {
            throw "Backup contour requires external failure-domain acknowledgement in Settings -> Backup & recovery."
        }
        if (-not [System.IO.Path]::IsPathRooted($backupDestination)) {
            throw "IGUANA_BACKUP_DESTINATION_DIR must be an absolute off-host path."
        }
        $resolvedBackupDestination = [System.IO.Path]::GetFullPath($backupDestination)
        $repoPrefix = [System.IO.Path]::GetFullPath($RepoRoot)
        if (-not $repoPrefix.EndsWith([System.IO.Path]::DirectorySeparatorChar.ToString())) {
            $repoPrefix += [System.IO.Path]::DirectorySeparatorChar
        }
        if ($resolvedBackupDestination.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "IGUANA_BACKUP_DESTINATION_DIR must be outside the repository failure domain."
        }
        if (-not (Test-Path -LiteralPath $resolvedBackupDestination)) {
            throw "Backup destination does not exist or is not mounted: $resolvedBackupDestination"
        }
    }

    if ($Profiles -contains "telegram") {
        Assert-RequiredSetting -DotEnv $DotEnv -Name "TELEGRAM_BOT_TOKEN" -Message "Telegram profile requires TELEGRAM_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "TELEGRAM_BOT_USERNAME" -Message "Telegram profile requires TELEGRAM_BOT_USERNAME" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "GROUP_CHAT_ID" -Message "Telegram profile requires GROUP_CHAT_ID" | Out-Null
    }
    if ($Profiles -contains "vk") {
        Assert-RequiredSetting -DotEnv $DotEnv -Name "VK_BOT_TOKEN" -Message "VK profile requires VK_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "VK_GROUP_ID" -Message "VK profile requires VK_GROUP_ID" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "VK_OPERATOR_CHAT_ID" -Message "VK profile requires VK_OPERATOR_CHAT_ID" | Out-Null
    }
    if ($Profiles -contains "max") {
        Assert-RequiredSetting -DotEnv $DotEnv -Name "MAX_BOT_TOKEN" -Message "MAX profile requires MAX_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "MAX_CHANNEL_ID" -Message "MAX profile requires MAX_CHANNEL_ID" | Out-Null
        Assert-RequiredSetting -DotEnv $DotEnv -Name "MAX_SUPPORT_CHAT_ID" -Message "MAX profile requires MAX_SUPPORT_CHAT_ID" | Out-Null
    }

    if ($EdgeEnabled) {
        if ($AllowInsecure) {
            Assert-RequiredSetting -DotEnv $DotEnv -Name "IGUANA_PUBLIC_HOST" -Message "Edge contour requires IGUANA_PUBLIC_HOST" | Out-Null
        } else {
            Assert-NonDefaultSecret -DotEnv $DotEnv -Name "IGUANA_PUBLIC_HOST" -DisallowedValues @("localhost", "127.0.0.1", "example.com") -Message "Edge contour requires explicit public host" | Out-Null
        }

        $tlsEnabled = Test-TruthySetting -Value (Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_EDGE_TLS_ENABLED")
        if ($tlsEnabled) {
            $certDirectory = Resolve-RepoPathFromSetting -RepoRoot $RepoRoot -DotEnv $DotEnv -Name "IGUANA_EDGE_CERTS_DIR" -DefaultValue "./deploy/nginx/certs"
            Assert-RequiredFile -Path (Join-Path $certDirectory "fullchain.pem") -Label "Edge TLS certificate"
            Assert-RequiredFile -Path (Join-Path $certDirectory "privkey.pem") -Label "Edge TLS private key"
        }
    }
}

$repoRoot = Get-RepoRoot
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$edgeComposeFile = Join-Path $repoRoot "docker-compose.production-edge.yml"
$observabilityComposeFile = Join-Path $repoRoot "docker-compose.production-observability.yml"
$backupComposeFile = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"
$dotEnv = Read-DotEnvFile -Path $dotEnvPath
if ($Backup) {
    $backupConfigLibrary = Join-Path $PSScriptRoot "lib\backup-config.ps1"
    if (-not (Test-Path -LiteralPath $backupConfigLibrary)) {
        throw "Backup config library is missing: $backupConfigLibrary"
    }
    . $backupConfigLibrary
    Import-IguanaBackupSettings -RepoRoot $repoRoot | Out-Null
}

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if ($Edge -and -not (Test-Path -LiteralPath $edgeComposeFile)) {
    throw "Edge compose file not found: $edgeComposeFile"
}
if ($Observability -and -not (Test-Path -LiteralPath $observabilityComposeFile)) {
    throw "Observability compose file not found: $observabilityComposeFile"
}
if ($Backup -and -not (Test-Path -LiteralPath $backupComposeFile)) {
    throw "Backup compose file not found: $backupComposeFile"
}

$profiles = @()
if ($Telegram) {
    $profiles += "telegram"
}
if ($Vk) {
    $profiles += "vk"
}
if ($Max) {
    $profiles += "max"
}

$resolvedWebReplicas = Resolve-ReplicaCount -ExplicitValue $WebReplicas -DotEnv $dotEnv -SettingName "IGUANA_PANEL_WEB_REPLICAS" -DefaultValue 1
$resolvedWorkerReplicas = Resolve-ReplicaCount -ExplicitValue $WorkerReplicas -DotEnv $dotEnv -SettingName "IGUANA_OPS_WORKER_REPLICAS" -DefaultValue 1

$requiredDirectories = @(
    (Join-Path $repoRoot "attachments"),
    (Join-Path $repoRoot "attachments\knowledge_base"),
    (Join-Path $repoRoot "attachments\forms"),
    (Join-Path $repoRoot "attachments\avatars"),
    (Join-Path $repoRoot "logs"),
    (Join-Path $repoRoot "bot_databases"),
    (Join-Path $repoRoot "deploy\nginx\certs")
)

foreach ($directory in $requiredDirectories) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

Invoke-PreflightChecks -RepoRoot $repoRoot -DotEnv $dotEnv -Profiles $profiles -EdgeEnabled:$Edge -ObservabilityEnabled:$Observability -BackupEnabled:$Backup -AllowInsecure:$AllowInsecureDefaults

$dockerCommand = Ensure-DockerAvailable
$baseArguments = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath) {
    $baseArguments += @("--env-file", $dotEnvPath)
}
$baseArguments += @("-f", $composeFile)
if ($Edge) {
    $baseArguments += @("-f", $edgeComposeFile)
}
if ($Observability) {
    $baseArguments += @("-f", $observabilityComposeFile)
}
if ($Backup) {
    $baseArguments += @("-f", $backupComposeFile)
}
foreach ($profile in $profiles) {
    $baseArguments += @("--profile", $profile)
}

if ($Observability -and -not $ValidateOnly) {
    $alertmanagerTokenBootstrap = Join-Path $PSScriptRoot "ensure-alertmanager-ingestion-token.ps1"
    if (-not (Test-Path -LiteralPath $alertmanagerTokenBootstrap -PathType Leaf)) {
        throw "Alertmanager ingestion token bootstrap is missing: $alertmanagerTokenBootstrap"
    }
    $alertmanagerSecretsDir = Resolve-RepoPathFromSetting `
        -RepoRoot $repoRoot `
        -DotEnv $dotEnv `
        -Name "IGUANA_SECRETS_DIR" `
        -DefaultValue "config/secrets"
    & powershell.exe `
        -NoLogo `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File $alertmanagerTokenBootstrap `
        -SecretsDir $alertmanagerSecretsDir
    if ($LASTEXITCODE -ne 0) {
        throw "Alertmanager ingestion token bootstrap failed with exit code $LASTEXITCODE."
    }
}

if ($ValidateOnly) {
    $configArguments = $baseArguments + @("config", "-q")
    & $dockerCommand @configArguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose config validation failed with exit code $LASTEXITCODE."
    }

    Write-Host "[INFO] Validation succeeded."
    Write-Host "[INFO] panel-web replicas: $resolvedWebReplicas"
    Write-Host "[INFO] ops-worker replicas: $resolvedWorkerReplicas"
    Write-Host "[INFO] Edge enabled: $Edge"
    Write-Host "[INFO] Observability enabled: $Observability"
    Write-Host "[INFO] Backup enabled: $Backup"
    Write-Host "[INFO] Insecure defaults allowed: $AllowInsecureDefaults"
    exit 0
}

$arguments = $baseArguments + @(
    "up",
    "--remove-orphans",
    "--scale", "panel-web=$resolvedWebReplicas",
    "--scale", "ops-worker=$resolvedWorkerReplicas"
)
if ($Build) {
    $arguments += "--build"
}
if (-not $NoDetach) {
    $arguments += "-d"
}

Write-Host "[INFO] Starting Iguana docker production contour"
Write-Host "[INFO] panel-web replicas: $resolvedWebReplicas"
Write-Host "[INFO] ops-worker replicas: $resolvedWorkerReplicas"
Write-Host "[INFO] Profiles: $($(if ($profiles.Count -gt 0) { $profiles -join ', ' } else { 'none' }))"
Write-Host "[INFO] Edge enabled: $Edge"
Write-Host "[INFO] Observability enabled: $Observability"

$runnerStarter = Join-Path $PSScriptRoot "start-backup-policy-runner.ps1"
$runnerStopper = Join-Path $PSScriptRoot "stop-backup-policy-runner.ps1"
$runnerStartedBeforeCompose = $false

if ($NoDetach -and (Test-Path -LiteralPath $runnerStarter -PathType Leaf)) {
    & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $runnerStarter -DetachFromParent
    if ($LASTEXITCODE -eq 0) {
        $runnerStartedBeforeCompose = $true
    } else {
        Write-Warning "Backup policy background runner failed to start before foreground Docker launch."
    }
}

& $dockerCommand @arguments
if ($LASTEXITCODE -ne 0) {
    if ($runnerStartedBeforeCompose -and (Test-Path -LiteralPath $runnerStopper -PathType Leaf)) {
        & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $runnerStopper -WaitSeconds 3 *> $null
    }
    throw "docker compose up failed with exit code $LASTEXITCODE."
}

if (-not $NoDetach -and (Test-Path -LiteralPath $runnerStarter -PathType Leaf)) {
    & powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File $runnerStarter -DetachFromParent
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Docker contour started, but backup policy background runner failed to start."
    }
}

Write-Host "[INFO] Iguana docker production contour started."
if ($Observability) {
    $grafanaPort = (Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_PORT")
    if ([string]::IsNullOrWhiteSpace($grafanaPort)) { $grafanaPort = "3000" }
    $prometheusPort = (Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_PROMETHEUS_PORT")
    if ([string]::IsNullOrWhiteSpace($prometheusPort)) { $prometheusPort = "9090" }
    Write-Host "[INFO] Grafana: http://127.0.0.1:$grafanaPort"
    Write-Host "[INFO] Prometheus: http://127.0.0.1:$prometheusPort"
}
Write-Host "[INFO] Local loopback ingress: http://$((Get-SettingValue -DotEnv $dotEnv -Name 'APP_PANEL_BIND_HOST') -replace '^$','127.0.0.1'):$((Get-SettingValue -DotEnv $dotEnv -Name 'APP_HTTP_PORT') -replace '^$','8080')"
