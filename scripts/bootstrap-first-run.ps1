param(
    [switch]$Force,
    [switch]$SkipDocker,
    [switch]$ValidateOnly
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for bootstrap-first-run.ps1."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Test-PortBusy {
    param(
        [int]$Port
    )

    try {
        $listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Loopback, $Port)
        $listener.Start()
        $listener.Stop()
        return $false
    } catch {
        return $true
    }
}

function Find-FreePort {
    param(
        [int]$StartPort
    )

    $candidate = $StartPort
    while (Test-PortBusy -Port $candidate) {
        $candidate++
    }
    return $candidate
}

function Test-DockerComposeAvailable {
    $dockerCommand = Get-DockerCommandPath
    if (-not $dockerCommand) {
        return $false
    }

    Update-ProcessPathForDocker -DockerCommand $dockerCommand

    & $dockerCommand compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        return $false
    }

    & $dockerCommand info *> $null
    return $LASTEXITCODE -eq 0
}

function Get-BoolSetting {
    param(
        [string]$Name,
        [bool]$Default = $false
    )

    $rawValue = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return $Default
    }

    switch ($rawValue.Trim().ToLowerInvariant()) {
        "1" { return $true }
        "true" { return $true }
        "yes" { return $true }
        "on" { return $true }
        "0" { return $false }
        "false" { return $false }
        "no" { return $false }
        "off" { return $false }
        default { return $Default }
    }
}

function Get-IntSetting {
    param(
        [string]$Name,
        [int]$Default
    )

    $rawValue = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrWhiteSpace($rawValue)) {
        return $Default
    }

    $parsedValue = 0
    if ([int]::TryParse($rawValue.Trim(), [ref]$parsedValue)) {
        return $parsedValue
    }

    return $Default
}

function Get-DockerCommandPath {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if ($dockerCommand) {
        return $dockerCommand.Source
    }

    $candidates = @(
        (Join-Path $env:ProgramFiles "Docker\Docker\resources\bin\docker.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Docker\Docker\resources\bin\docker.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\resources\bin\docker.exe")
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    return $null
}

function Update-ProcessPathForDocker {
    param(
        [string]$DockerCommand
    )

    if ([string]::IsNullOrWhiteSpace($DockerCommand)) {
        return
    }

    $dockerDirectory = Split-Path -Parent $DockerCommand
    if ([string]::IsNullOrWhiteSpace($dockerDirectory)) {
        return
    }

    $pathItems = @($env:PATH -split ";")
    if ($pathItems -notcontains $dockerDirectory) {
        $env:PATH = "$dockerDirectory;$env:PATH"
    }
}

function Get-DockerDesktopExecutablePath {
    $candidates = @(
        (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"),
        (Join-Path ${env:ProgramFiles(x86)} "Docker\Docker\Docker Desktop.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\Docker Desktop.exe")
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    return $null
}

function Install-DockerDesktop {
    $wingetCommand = Get-Command winget -ErrorAction SilentlyContinue
    if (-not $wingetCommand) {
        throw "Docker Desktop is missing and winget is unavailable, so bootstrap cannot install it automatically."
    }

    Write-Host "[INFO] Installing Docker Desktop via winget"
    & $wingetCommand.Source install -e --id Docker.DockerDesktop --accept-source-agreements --accept-package-agreements --silent
    if ($LASTEXITCODE -ne 0) {
        throw "winget failed to install Docker Desktop (exit code $LASTEXITCODE)."
    }
}

function Start-DockerDesktop {
    $dockerDesktop = Get-DockerDesktopExecutablePath
    if (-not $dockerDesktop) {
        throw "Docker Desktop appears to be installed, but its executable was not found."
    }

    Write-Host "[INFO] Starting Docker Desktop"
    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
}

function Wait-ForDockerReady {
    param(
        [int]$TimeoutSeconds = 300,
        [int]$PollSeconds = 3
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerComposeAvailable) {
            return $true
        }
        Start-Sleep -Seconds $PollSeconds
    }

    return $false
}

function Ensure-DockerAvailable {
    param(
        [string]$BootstrapMode,
        [bool]$AllowInstallation = $true
    )

    if (Test-DockerComposeAvailable) {
        return $true
    }

    $timeoutSeconds = Get-IntSetting -Name "IGUANA_BOOTSTRAP_DOCKER_READY_TIMEOUT_SECONDS" -Default 300
    if (Get-DockerDesktopExecutablePath) {
        try {
            Write-Host "[INFO] Docker Desktop is installed but not ready yet. Waiting for it to come online."
            Start-DockerDesktop
            if (Wait-ForDockerReady -TimeoutSeconds $timeoutSeconds) {
                return $true
            }
            Write-Warning "Docker Desktop is installed, but docker info did not become ready in time."
        } catch {
            Write-Warning $_.Exception.Message
        }
    }

    $autoInstallDocker = Get-BoolSetting -Name "IGUANA_BOOTSTRAP_INSTALL_DOCKER" -Default $false
    if ((-not $AllowInstallation) -or (-not $autoInstallDocker)) {
        return $false
    }

    try {
        Install-DockerDesktop
        Start-DockerDesktop
        if (-not (Wait-ForDockerReady -TimeoutSeconds $timeoutSeconds)) {
            throw "Docker Desktop did not become ready within $timeoutSeconds seconds."
        }
        return $true
    } catch {
        if ($BootstrapMode -eq "postgresql") {
            throw
        }
        Write-Warning $_.Exception.Message
        return $false
    }
}

function Write-Utf8NoBomFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function New-RandomHexToken {
    param(
        [int]$BytesLength = 32
    )

    $bytes = New-Object byte[] $BytesLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
}

function New-RandomBase64Token {
    param(
        [int]$BytesLength = 32
    )

    $bytes = New-Object byte[] $BytesLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Test-ValidMonitoringMasterKeyPayload {
    param(
        [string]$EncodedValue
    )

    if ([string]::IsNullOrWhiteSpace($EncodedValue)) {
        return $false
    }

    try {
        $decoded = [Convert]::FromBase64String($EncodedValue.Trim())
        return $decoded.Length -in @(16, 24, 32)
    } catch {
        return $false
    }
}

function Resolve-MonitoringMasterKeyValue {
    param(
        [string]$RepoRoot
    )

    $legacyKeyPath = Join-Path $RepoRoot "config\shared\monitoring-credentials.key"
    if (Test-Path -LiteralPath $legacyKeyPath) {
        $legacyEncoded = [System.IO.File]::ReadAllText($legacyKeyPath, [System.Text.Encoding]::UTF8).Trim()
        if (-not [string]::IsNullOrWhiteSpace($legacyEncoded)) {
            if (-not (Test-ValidMonitoringMasterKeyPayload -EncodedValue $legacyEncoded)) {
                throw "Legacy monitoring credentials key is not valid Base64 AES material: $legacyKeyPath"
            }
            return "base64:$legacyEncoded"
        }
    }

    return "base64:$(New-RandomBase64Token)"
}

function Ensure-Directory {
    param(
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-ExistingPersistentInfrastructureVolumes {
    param(
        [string]$DockerCommand,
        [string]$RepoRoot
    )

    $projectName = (Split-Path -Leaf $RepoRoot).ToLowerInvariant()
    $logicalVolumeNames = @("iguana-postgres-data", "iguana-rabbitmq-data")
    $existing = [System.Collections.Generic.List[string]]::new()

    foreach ($logicalName in $logicalVolumeNames) {
        foreach ($candidate in @($logicalName, "${projectName}_${logicalName}")) {
            & $DockerCommand volume inspect $candidate *> $null
            if ($LASTEXITCODE -eq 0 -and -not $existing.Contains($candidate)) {
                $existing.Add($candidate)
            }
        }
    }

    return $existing.ToArray()
}

function Build-EnvContent {
    param(
        [string]$RepoRoot,
        [string]$Mode,
        [int]$PostgresPort,
        [string]$TransportMode,
        [int]$RabbitAmqpPort,
        [int]$RabbitHttpPort
    )

    $postgresPassword = New-RandomHexToken
    $rabbitPassword = New-RandomHexToken
    $redisPassword = New-RandomHexToken
    $objectAccessKey = New-RandomHexToken -BytesLength 12
    $objectSecretKey = New-RandomHexToken
    $internalBotApiToken = New-RandomHexToken
    $rememberMeKey = New-RandomHexToken
    $monitoringMasterKey = Resolve-MonitoringMasterKeyValue -RepoRoot $RepoRoot
    $grafanaAdminPassword = New-RandomHexToken

    $lines = @(
        "# Iguana first-run bootstrap",
        "# Generated by scripts/bootstrap-first-run.ps1",
        "# Fresh-install secrets below are generated once for this .env.",
        "# Existing persisted infrastructure credentials are not rotated automatically.",
        "IGUANA_BOOTSTRAP_DB_MODE=$Mode",
        "APP_POSTGRES_PORT=$PostgresPort",
        "APP_RABBITMQ_AMQP_PORT=$RabbitAmqpPort",
        "APP_RABBITMQ_HTTP_PORT=$RabbitHttpPort",
        ""
    )

    $lines += @(
        "APP_DB_MODE=postgresql",
        "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PostgresPort/iguana",
        "SPRING_DATASOURCE_USERNAME=iguana",
        "SPRING_DATASOURCE_PASSWORD=$postgresPassword",
        "APP_COORDINATION_MODE=direct",
        "APP_COORDINATION_REQUIRED_FOR_POSTGRESQL=false",
        "APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL=false",
        "",
        "# Generated infrastructure/application secrets for local bootstrap and future production-like contour",
        "IGUANA_POSTGRES_DB=iguana",
        "IGUANA_POSTGRES_USER=iguana",
        "IGUANA_POSTGRES_PASSWORD=$postgresPassword",
        "IGUANA_RABBITMQ_USER=iguana",
        "IGUANA_RABBITMQ_PASSWORD=$rabbitPassword",
        "IGUANA_REDIS_PASSWORD=$redisPassword",
        "APP_STORAGE_OBJECT_BUCKET=iguana",
        "APP_STORAGE_OBJECT_REGION=us-east-1",
        "APP_STORAGE_OBJECT_KEY_PREFIX=iguana",
        "APP_STORAGE_OBJECT_ACCESS_KEY=$objectAccessKey",
        "APP_STORAGE_OBJECT_SECRET_KEY=$objectSecretKey",
        "APP_INTERNAL_BOT_API_TOKEN=$internalBotApiToken",
        "APP_SECURITY_REMEMBER_ME_KEY=$rememberMeKey",
        "MONITORING_CREDENTIALS_MASTER_KEY=$monitoringMasterKey",
        "IGUANA_GRAFANA_ADMIN_USER=admin",
        "IGUANA_GRAFANA_ADMIN_PASSWORD=$grafanaAdminPassword",
        ""
    )

    $lines += @(
        "APP_INTEGRATION_TRANSPORT_MODE=$TransportMode",
        "SPRING_RABBITMQ_HOST=localhost",
        "SPRING_RABBITMQ_PORT=$RabbitAmqpPort",
        "SPRING_RABBITMQ_USERNAME=iguana",
        "SPRING_RABBITMQ_PASSWORD=$rabbitPassword",
        ""
    )

    $lines += @(
        "APP_STORAGE_ATTACHMENTS=../attachments",
        "APP_STORAGE_KNOWLEDGE_BASE=../attachments/knowledge_base",
        "APP_STORAGE_AVATARS=../attachments/avatars",
        "APP_STORAGE_WEBFORMS=../attachments/forms",
        "APP_PANEL_LOG_DIR=../logs",
        "APP_BOT_LOG_DIR=../logs"
    )

    return ($lines -join [Environment]::NewLine) + [Environment]::NewLine
}

$repoRoot = Get-RepoRoot
$envFile = Join-Path $repoRoot ".env"
$composeFile = Join-Path $repoRoot "docker-compose.local-postgres.yml"
$bootstrapMode = [Environment]::GetEnvironmentVariable("IGUANA_BOOTSTRAP_DB_MODE")
if ([string]::IsNullOrWhiteSpace($bootstrapMode)) {
    $bootstrapMode = "auto"
}
$bootstrapMode = $bootstrapMode.Trim().ToLowerInvariant()
if ($bootstrapMode -notin @("auto", "postgresql")) {
    throw "Unsupported IGUANA_BOOTSTRAP_DB_MODE '$bootstrapMode'. Allowed values: auto, postgresql."
}

$dockerAvailable = $false
if (-not $SkipDocker) {
    $dockerAvailable = Ensure-DockerAvailable -BootstrapMode $bootstrapMode -AllowInstallation (-not $ValidateOnly)
}

$effectiveMode = switch ($bootstrapMode) {
    "postgresql" {
        if (-not $dockerAvailable -and -not $SkipDocker) {
            throw "IGUANA_BOOTSTRAP_DB_MODE=postgresql requires Docker with docker compose."
        }
        "postgresql"
    }
    default {
        if (-not $dockerAvailable -and -not $SkipDocker) {
            throw "Docker is unavailable after bootstrap checks. First-run bootstrap requires PostgreSQL/RabbitMQ via docker compose."
        }
        "postgresql"
    }
}

$preferredPort = 5432
$effectivePort = Find-FreePort -StartPort $preferredPort
$preferredRabbitAmqpPort = 5672
$preferredRabbitHttpPort = 15672
$effectiveRabbitAmqpPort = Find-FreePort -StartPort $preferredRabbitAmqpPort
$effectiveRabbitHttpPort = Find-FreePort -StartPort $preferredRabbitHttpPort
$effectiveTransportMode = "rabbitmq"

if ($ValidateOnly) {
    Write-Host "[INFO] Bootstrap validation succeeded."
    Write-Host "[INFO] Mode: $effectiveMode"
    Write-Host "[INFO] PostgreSQL port: $effectivePort"
    Write-Host "[INFO] RabbitMQ AMQP port: $effectiveRabbitAmqpPort"
    Write-Host "[INFO] RabbitMQ HTTP port: $effectiveRabbitHttpPort"
    Write-Host "[INFO] Transport mode: $effectiveTransportMode"
    exit 0
}

$directories = @(
    (Join-Path $repoRoot "attachments"),
    (Join-Path $repoRoot "attachments\knowledge_base"),
    (Join-Path $repoRoot "attachments\forms"),
    (Join-Path $repoRoot "attachments\avatars"),
    (Join-Path $repoRoot "logs"),
    (Join-Path $repoRoot "bot_databases")
)

foreach ($directory in $directories) {
    Ensure-Directory -Path $directory
}

if ((-not (Test-Path -LiteralPath $envFile)) -or $Force) {
    if ($dockerAvailable) {
        $dockerCommand = Get-DockerCommandPath
        $existingVolumes = Get-ExistingPersistentInfrastructureVolumes `
            -DockerCommand $dockerCommand `
            -RepoRoot $repoRoot
        if ($existingVolumes.Count -gt 0) {
            throw "Existing PostgreSQL/RabbitMQ persistent volume(s) detected: $($existingVolumes -join ', '). Refusing to create or regenerate .env because that can desynchronize persisted credentials. Use the controlled migration workflow from docs/runbooks/persisted-credential-migration-status.md or reset the contour explicitly."
        }
    }

    $envContent = Build-EnvContent `
        -RepoRoot $repoRoot `
        -Mode $effectiveMode `
        -PostgresPort $effectivePort `
        -TransportMode $effectiveTransportMode `
        -RabbitAmqpPort $effectiveRabbitAmqpPort `
        -RabbitHttpPort $effectiveRabbitHttpPort
    Write-Utf8NoBomFile -Path $envFile -Content $envContent
    Write-Host "[INFO] Created $envFile"
} else {
    Write-Host "[INFO] Keeping existing $envFile"
}

if (-not $SkipDocker) {
    Write-Host "[INFO] Starting local PostgreSQL and RabbitMQ via docker compose"
    $dockerCommand = Get-DockerCommandPath
    if (-not $dockerCommand) {
        throw "Docker became unavailable before docker compose startup."
    }
    Update-ProcessPathForDocker -DockerCommand $dockerCommand
    & $dockerCommand compose -f $composeFile up -d postgres rabbitmq
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to start local PostgreSQL/RabbitMQ containers."
    }
    Write-Host "[INFO] Local PostgreSQL is starting on localhost:$effectivePort"
    Write-Host "[INFO] Local RabbitMQ is starting on localhost:$effectiveRabbitAmqpPort (management UI: http://localhost:$effectiveRabbitHttpPort)"
}

Write-Host "[INFO] First-run bootstrap completed."
