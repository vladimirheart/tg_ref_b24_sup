param(
    [switch]$Telegram,
    [switch]$Vk,
    [switch]$Max,
    [switch]$Edge,
    [switch]$Build,
    [switch]$NoDetach,
    [switch]$ValidateOnly,
    [switch]$AllowInsecureDefaults
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
    param(
        [string]$Path
    )

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

function Test-TruthySetting {
    param(
        [string]$Value
    )

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
        [string[]]$Profiles,
        [bool]$Edge,
        [bool]$AllowInsecureDefaults
    )

    $dotEnvPath = Join-Path $RepoRoot ".env"
    $dotEnv = Read-DotEnvFile -Path $dotEnvPath

    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\settings.json") -Label "Shared config settings"
    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\locations.json") -Label "Shared config locations"
    Assert-RequiredFile -Path (Join-Path $RepoRoot "config\shared\org_structure.json") -Label "Shared config org structure"

    if ($AllowInsecureDefaults) {
        Assert-RequiredSetting -DotEnv $dotEnv -Name "APP_INTERNAL_BOT_API_TOKEN" -Message "Internal bot API token must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "APP_SECURITY_REMEMBER_ME_KEY" -Message "Remember-me key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "IGUANA_POSTGRES_PASSWORD" -Message "PostgreSQL password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "IGUANA_RABBITMQ_PASSWORD" -Message "RabbitMQ password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "IGUANA_REDIS_PASSWORD" -Message "Redis password must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -Message "Object storage access key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -Message "Object storage secret key must be configured" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -Message "Object storage bucket must be configured" | Out-Null
    } else {
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "APP_INTERNAL_BOT_API_TOKEN" -DisallowedValues @("change-me", "iguana-internal-bot-token") -Message "Internal bot API token must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "APP_SECURITY_REMEMBER_ME_KEY" -DisallowedValues @("change-me", "iguana-panel-remember-me") -Message "Remember-me key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "IGUANA_POSTGRES_PASSWORD" -DisallowedValues @("iguana") -Message "PostgreSQL password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "IGUANA_RABBITMQ_PASSWORD" -DisallowedValues @("iguana") -Message "RabbitMQ password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "IGUANA_REDIS_PASSWORD" -DisallowedValues @("iguana-redis") -Message "Redis password must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -DisallowedValues @("iguana-minio") -Message "Object storage access key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_SECRET_KEY" -DisallowedValues @("iguana-minio-secret") -Message "Object storage secret key must be overridden" | Out-Null
        Assert-NonDefaultSecret -DotEnv $dotEnv -Name "APP_STORAGE_OBJECT_BUCKET" -DisallowedValues @("iguana") -Message "Object storage bucket must be overridden for production-like launch" | Out-Null
    }

    if ($Profiles -contains "telegram") {
        Assert-RequiredSetting -DotEnv $dotEnv -Name "TELEGRAM_BOT_TOKEN" -Message "Telegram profile requires TELEGRAM_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "TELEGRAM_BOT_USERNAME" -Message "Telegram profile requires TELEGRAM_BOT_USERNAME" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "GROUP_CHAT_ID" -Message "Telegram profile requires GROUP_CHAT_ID" | Out-Null
    }
    if ($Profiles -contains "vk") {
        Assert-RequiredSetting -DotEnv $dotEnv -Name "VK_BOT_TOKEN" -Message "VK profile requires VK_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "VK_GROUP_ID" -Message "VK profile requires VK_GROUP_ID" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "VK_OPERATOR_CHAT_ID" -Message "VK profile requires VK_OPERATOR_CHAT_ID" | Out-Null
    }
    if ($Profiles -contains "max") {
        Assert-RequiredSetting -DotEnv $dotEnv -Name "MAX_BOT_TOKEN" -Message "MAX profile requires MAX_BOT_TOKEN" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "MAX_CHANNEL_ID" -Message "MAX profile requires MAX_CHANNEL_ID" | Out-Null
        Assert-RequiredSetting -DotEnv $dotEnv -Name "MAX_SUPPORT_CHAT_ID" -Message "MAX profile requires MAX_SUPPORT_CHAT_ID" | Out-Null
    }

    if ($Edge) {
        if ($AllowInsecureDefaults) {
            Assert-RequiredSetting -DotEnv $dotEnv -Name "IGUANA_PUBLIC_HOST" -Message "Edge contour requires IGUANA_PUBLIC_HOST" | Out-Null
        } else {
            Assert-NonDefaultSecret -DotEnv $dotEnv -Name "IGUANA_PUBLIC_HOST" -DisallowedValues @("localhost", "127.0.0.1", "example.com") -Message "Edge contour requires explicit public host" | Out-Null
        }

        $tlsEnabled = Test-TruthySetting -Value (Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_EDGE_TLS_ENABLED")
        if ($tlsEnabled) {
            $certDirectory = Resolve-RepoPathFromSetting -RepoRoot $RepoRoot -DotEnv $dotEnv -Name "IGUANA_EDGE_CERTS_DIR" -DefaultValue "./deploy/nginx/certs"
            Assert-RequiredFile -Path (Join-Path $certDirectory "fullchain.pem") -Label "Edge TLS certificate"
            Assert-RequiredFile -Path (Join-Path $certDirectory "privkey.pem") -Label "Edge TLS private key"
        }
    }
}

$repoRoot = Get-RepoRoot
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$edgeComposeFile = Join-Path $repoRoot "docker-compose.production-edge.yml"

if (-not (Test-Path -LiteralPath $composeFile)) {
    throw "Compose file not found: $composeFile"
}
if ($Edge -and -not (Test-Path -LiteralPath $edgeComposeFile)) {
    throw "Edge compose file not found: $edgeComposeFile"
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

Invoke-PreflightChecks -RepoRoot $repoRoot -Profiles $profiles -Edge:$Edge -AllowInsecureDefaults:$AllowInsecureDefaults

if ($ValidateOnly) {
    Write-Host "[INFO] Validation succeeded."
    Write-Host "[INFO] Compose file: $composeFile"
    if ($Edge) {
        Write-Host "[INFO] Edge compose file: $edgeComposeFile"
    }
    if ($profiles.Count -gt 0) {
        Write-Host "[INFO] Profiles: $($profiles -join ', ')"
    } else {
        Write-Host "[INFO] Profiles: none (infra + panel only)"
    }
    Write-Host "[INFO] Edge enabled: $Edge"
    Write-Host "[INFO] Insecure defaults allowed: $AllowInsecureDefaults"
    exit 0
}

$dockerCommand = Ensure-DockerAvailable

$arguments = @("compose", "-f", $composeFile)
if ($Edge) {
    $arguments += @("-f", $edgeComposeFile)
}
foreach ($profile in $profiles) {
    $arguments += @("--profile", $profile)
}
$arguments += "up"
if ($Build) {
    $arguments += "--build"
}
if (-not $NoDetach) {
    $arguments += "-d"
}

Write-Host "[INFO] Starting Iguana docker production contour"
Write-Host "[INFO] Profiles: $($(if ($profiles.Count -gt 0) { $profiles -join ', ' } else { 'none (infra + panel only)' }))"
Write-Host "[INFO] Edge enabled: $Edge"

& $dockerCommand @arguments
if ($LASTEXITCODE -ne 0) {
    throw "docker compose up failed with exit code $LASTEXITCODE."
}

Write-Host "[INFO] Iguana docker production contour started."
