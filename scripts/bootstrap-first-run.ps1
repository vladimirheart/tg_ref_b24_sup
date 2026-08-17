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

    $autoInstallDocker = Get-BoolSetting -Name "IGUANA_BOOTSTRAP_INSTALL_DOCKER" -Default $true
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

function Ensure-Directory {
    param(
        [string]$Path
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Build-EnvContent {
    param(
        [string]$Mode,
        [int]$PostgresPort,
        [string]$TransportMode,
        [int]$RabbitAmqpPort,
        [int]$RabbitHttpPort
    )

    $lines = @(
        "# Iguana first-run bootstrap",
        "# Generated by scripts/bootstrap-first-run.ps1",
        "IGUANA_BOOTSTRAP_DB_MODE=$Mode",
        "APP_POSTGRES_PORT=$PostgresPort",
        "APP_RABBITMQ_AMQP_PORT=$RabbitAmqpPort",
        "APP_RABBITMQ_HTTP_PORT=$RabbitHttpPort",
        ""
    )

    if ($Mode -eq "postgresql") {
        $lines += @(
            "APP_DB_MODE=postgresql",
            "SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:$PostgresPort/iguana",
            "SPRING_DATASOURCE_USERNAME=iguana",
            "SPRING_DATASOURCE_PASSWORD=iguana",
            ""
        )
    } else {
        $lines += @(
            "APP_DB_MODE=sqlite",
            ""
        )
    }

    $lines += @(
        "APP_INTEGRATION_TRANSPORT_MODE=$TransportMode",
        "SPRING_RABBITMQ_HOST=localhost",
        "SPRING_RABBITMQ_PORT=$RabbitAmqpPort",
        "SPRING_RABBITMQ_USERNAME=iguana",
        "SPRING_RABBITMQ_PASSWORD=iguana",
        ""
    )

    $lines += @(
        "APP_STORAGE_ATTACHMENTS=attachments",
        "APP_STORAGE_KNOWLEDGE_BASE=attachments/knowledge_base",
        "APP_STORAGE_AVATARS=attachments/avatars",
        "APP_STORAGE_WEBFORMS=attachments/forms",
        "APP_BOT_DATABASE_DIR=bot_databases",
        "APP_PANEL_LOG_DIR=logs",
        "APP_BOT_LOG_DIR=logs"
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
$allowSqliteFallback = Get-BoolSetting -Name "IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK" -Default $true

if ($bootstrapMode -notin @("auto", "sqlite", "postgresql")) {
    throw "Unsupported IGUANA_BOOTSTRAP_DB_MODE '$bootstrapMode'. Allowed values: auto, sqlite, postgresql."
}

$dockerAvailable = $false
if (-not $SkipDocker -and $bootstrapMode -ne "sqlite") {
    $dockerAvailable = Ensure-DockerAvailable -BootstrapMode $bootstrapMode -AllowInstallation (-not $ValidateOnly)
}

$effectiveMode = switch ($bootstrapMode) {
    "postgresql" {
        if (-not $dockerAvailable -and -not $SkipDocker) {
            throw "IGUANA_BOOTSTRAP_DB_MODE=postgresql requires Docker with docker compose."
        }
        "postgresql"
    }
    "sqlite" { "sqlite" }
    default {
        if ($dockerAvailable) {
            "postgresql"
        } elseif ($allowSqliteFallback) {
            "sqlite"
        } else {
            throw "Docker is unavailable after bootstrap checks, and IGUANA_BOOTSTRAP_ALLOW_SQLITE_FALLBACK=false blocks SQLite fallback."
        }
    }
}

$preferredPort = 5432
$effectivePort = if ($effectiveMode -eq "postgresql") { Find-FreePort -StartPort $preferredPort } else { $preferredPort }
$preferredRabbitAmqpPort = 5672
$preferredRabbitHttpPort = 15672
$effectiveRabbitAmqpPort = if ($effectiveMode -eq "postgresql") { Find-FreePort -StartPort $preferredRabbitAmqpPort } else { $preferredRabbitAmqpPort }
$effectiveRabbitHttpPort = if ($effectiveMode -eq "postgresql") { Find-FreePort -StartPort $preferredRabbitHttpPort } else { $preferredRabbitHttpPort }
$effectiveTransportMode = if ($effectiveMode -eq "postgresql") { "rabbitmq" } else { "jdbc" }

if ($ValidateOnly) {
    Write-Host "[INFO] Bootstrap validation succeeded."
    Write-Host "[INFO] Mode: $effectiveMode"
    if ($effectiveMode -eq "postgresql") {
        Write-Host "[INFO] PostgreSQL port: $effectivePort"
        Write-Host "[INFO] RabbitMQ AMQP port: $effectiveRabbitAmqpPort"
        Write-Host "[INFO] RabbitMQ HTTP port: $effectiveRabbitHttpPort"
    }
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
    $envContent = Build-EnvContent `
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

if ($effectiveMode -eq "postgresql" -and -not $SkipDocker) {
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
} elseif ($effectiveMode -eq "sqlite") {
    Write-Host "[INFO] Docker is unavailable, bootstrap stayed in SQLite+JDBC dev mode."
}

Write-Host "[INFO] First-run bootstrap completed."
