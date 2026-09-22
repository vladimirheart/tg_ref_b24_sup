param(
    [ValidateSet("start", "stop", "status")]
    [string]$Action = "status",
    [ValidateSet("telegram", "vk", "max")]
    [string]$Bot = "telegram",
    [switch]$ConfirmEmergencyMode,
    [switch]$Build,
    [string]$ProjectName = ""
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-legacy-bots.ps1."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Get-DockerCommandPath {
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Docker is not installed or not available in PATH."
    }
    return $command.Source
}

function Invoke-DockerCommand {
    param(
        [string]$DockerCommand,
        [string[]]$Arguments,
        [switch]$IgnoreExitCode
    )

    $output = @(& $DockerCommand @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and -not $IgnoreExitCode) {
        $text = ($output | ForEach-Object { "$_" }) -join [Environment]::NewLine
        throw ("docker {0} failed with exit code {1}.{2}{3}" -f ($Arguments -join " "), $exitCode, [Environment]::NewLine, $text)
    }
    return [pscustomobject]@{
        ExitCode = $exitCode
        Lines = $output
        Text = (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine)
    }
}

function Read-DotEnvValue {
    param(
        [string]$Path,
        [string]$Name
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return ""
    }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -match ('^' + [regex]::Escape($Name) + '=(.*)$')) {
            return $Matches[1].Trim()
        }
    }
    return ""
}

function Resolve-ProjectName {
    param(
        [string]$RepoRoot,
        [string]$EnvPath,
        [string]$ExplicitName
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitName)) {
        return $ExplicitName.Trim()
    }
    $fromProcess = [Environment]::GetEnvironmentVariable("COMPOSE_PROJECT_NAME")
    if (-not [string]::IsNullOrWhiteSpace($fromProcess)) {
        return $fromProcess.Trim()
    }
    $fromFile = Read-DotEnvValue -Path $EnvPath -Name "COMPOSE_PROJECT_NAME"
    if (-not [string]::IsNullOrWhiteSpace($fromFile)) {
        return $fromFile
    }
    return (Split-Path -Leaf $RepoRoot)
}

function Get-RunningServiceIds {
    param(
        [string]$DockerCommand,
        [string]$Project,
        [string]$Service
    )

    $result = Invoke-DockerCommand -DockerCommand $DockerCommand -Arguments @(
        "ps", "-q",
        "--filter", "label=com.docker.compose.project=$Project",
        "--filter", "label=com.docker.compose.service=$Service"
    )
    return @($result.Lines | ForEach-Object { "$_".Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-RunningLegacyServices {
    param(
        [string]$DockerCommand,
        [string]$Project
    )

    $running = New-Object 'System.Collections.Generic.List[string]'
    foreach ($service in @("bot-telegram", "bot-vk", "bot-max")) {
        if ((Get-RunningServiceIds -DockerCommand $DockerCommand -Project $Project -Service $service).Count -gt 0) {
            $running.Add($service)
        }
    }
    return @($running)
}

function Assert-BotRunnerStopped {
    param(
        [string]$DockerCommand,
        [string]$Project
    )

    $runnerIds = @(Get-RunningServiceIds -DockerCommand $DockerCommand -Project $Project -Service "bot-runner")
    if ($runnerIds.Count -gt 0) {
        throw "Emergency legacy bot start blocked: bot-runner is running. Stop bot-runner first; mixed runtime ownership is forbidden."
    }
}

function Assert-BaseRuntimeAvailable {
    param(
        [string]$DockerCommand,
        [string]$Project
    )

    $missing = New-Object 'System.Collections.Generic.List[string]'
    foreach ($service in @("postgres", "rabbitmq", "redis", "minio", "panel-web")) {
        if ((Get-RunningServiceIds -DockerCommand $DockerCommand -Project $Project -Service $service).Count -eq 0) {
            $missing.Add($service)
        }
    }
    if ($missing.Count -gt 0) {
        throw ("Emergency legacy bot start blocked: required base services are not running: {0}" -f ($missing -join ", "))
    }
}

function Get-ComposeBaseArguments {
    param(
        [string]$RepoRoot,
        [string]$EnvPath,
        [string]$Project
    )

    $arguments = New-Object 'System.Collections.Generic.List[string]'
    $arguments.Add("compose")
    $arguments.Add("--project-directory")
    $arguments.Add($RepoRoot)
    if (Test-Path -LiteralPath $EnvPath -PathType Leaf) {
        $arguments.Add("--env-file")
        $arguments.Add($EnvPath)
    }
    $arguments.Add("-f")
    $arguments.Add((Join-Path $RepoRoot "docker-compose.production-contour.yml"))
    $arguments.Add("-f")
    $arguments.Add((Join-Path $RepoRoot "docker-compose.production-legacy-bots.yml"))
    $arguments.Add("-p")
    $arguments.Add($Project)
    return @($arguments)
}

$repoRoot = Get-RepoRoot
$envPath = Join-Path $repoRoot ".env"
$mainCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$legacyCompose = Join-Path $repoRoot "docker-compose.production-legacy-bots.yml"
if (-not (Test-Path -LiteralPath $mainCompose -PathType Leaf)) {
    throw "Main production compose file is missing: $mainCompose"
}
if (-not (Test-Path -LiteralPath $legacyCompose -PathType Leaf)) {
    throw "Emergency legacy compose file is missing: $legacyCompose"
}

$dockerCommand = Get-DockerCommandPath
Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments @("compose", "version") | Out-Null
Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments @("info") | Out-Null

$project = Resolve-ProjectName -RepoRoot $repoRoot -EnvPath $envPath -ExplicitName $ProjectName
$serviceName = "bot-$Bot"
$composeBase = Get-ComposeBaseArguments -RepoRoot $repoRoot -EnvPath $envPath -Project $project

if ($Action -eq "status") {
    $runnerIds = @(Get-RunningServiceIds -DockerCommand $dockerCommand -Project $project -Service "bot-runner")
    $legacyRunning = @(Get-RunningLegacyServices -DockerCommand $dockerCommand -Project $project)
    Write-Host ("PROJECT={0}" -f $project)
    Write-Host ("BOT_RUNNER_RUNNING={0}" -f ($runnerIds.Count -gt 0).ToString().ToLowerInvariant())
    Write-Host ("LEGACY_RUNNING={0}" -f ($(if ($legacyRunning.Count -gt 0) { $legacyRunning -join "," } else { "none" })))
    if ($runnerIds.Count -gt 0 -and $legacyRunning.Count -gt 0) {
        Write-Host "MIXED_OWNERSHIP=BLOCK"
        exit 20
    }
    Write-Host "MIXED_OWNERSHIP=GREEN"
    exit 0
}

if ($Action -eq "stop") {
    $stopArguments = $composeBase + @("stop", $serviceName)
    Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments $stopArguments -IgnoreExitCode | Out-Null
    $rmArguments = $composeBase + @("rm", "-f", "-s", $serviceName)
    Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments $rmArguments -IgnoreExitCode | Out-Null
    if ((Get-RunningServiceIds -DockerCommand $dockerCommand -Project $project -Service $serviceName).Count -gt 0) {
        throw "Emergency legacy bot stop failed: $serviceName is still running."
    }
    Write-Host ("LEGACY_BOT_STOP=GREEN | service={0}" -f $serviceName)
    exit 0
}

if (-not $ConfirmEmergencyMode) {
    throw "Emergency legacy bot start requires -ConfirmEmergencyMode."
}

Assert-BotRunnerStopped -DockerCommand $dockerCommand -Project $project
Assert-BaseRuntimeAvailable -DockerCommand $dockerCommand -Project $project

$configArguments = $composeBase + @("config", "-q")
Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments $configArguments | Out-Null

$startArguments = New-Object 'System.Collections.Generic.List[string]'
foreach ($entry in $composeBase) { $startArguments.Add($entry) }
$startArguments.Add("up")
$startArguments.Add("-d")
$startArguments.Add("--no-deps")
if ($Build) {
    $startArguments.Add("--build")
}
$startArguments.Add($serviceName)
Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments @($startArguments) | Out-Null

try {
    Assert-BotRunnerStopped -DockerCommand $dockerCommand -Project $project
} catch {
    $stopArguments = $composeBase + @("stop", $serviceName)
    Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments $stopArguments -IgnoreExitCode | Out-Null
    $rmArguments = $composeBase + @("rm", "-f", "-s", $serviceName)
    Invoke-DockerCommand -DockerCommand $dockerCommand -Arguments $rmArguments -IgnoreExitCode | Out-Null
    throw "Emergency legacy bot start rolled back because bot-runner appeared during startup. Mixed runtime ownership was prevented."
}

if ((Get-RunningServiceIds -DockerCommand $dockerCommand -Project $project -Service $serviceName).Count -eq 0) {
    throw "Emergency legacy bot start failed: $serviceName is not running."
}

Write-Host ("LEGACY_BOT_START=GREEN | service={0}; project={1}" -f $serviceName, $project)
Write-Host "BOT_RUNNER_RUNNING=false"
Write-Host "MIXED_OWNERSHIP=GREEN"
