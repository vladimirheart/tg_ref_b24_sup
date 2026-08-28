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

function Invoke-NativeCapture {
    param(
        [string]$Executable,
        [string[]]$Arguments
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Executable @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    return [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Get-NativeOutputLines {
    param([object[]]$Output)

    $lines = @()
    foreach ($item in @($Output)) {
        $text = [string]$item
        foreach ($line in ($text -split "`r?`n")) {
            $trimmed = $line.Trim()
            if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
                $lines += $trimmed
            }
        }
    }
    return $lines
}

function Assert-NativeSuccess {
    param(
        [string]$Executable,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $result = Invoke-NativeCapture -Executable $Executable -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Get-ContainerInspectObject {
    param(
        [string]$Docker,
        [string]$ContainerId
    )

    $result = Assert-NativeSuccess `
        -Executable $Docker `
        -Arguments @("inspect", $ContainerId) `
        -ErrorMessage "Unable to inspect container $ContainerId"

    $json = ($result.Output -join "`n").Trim()
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw "Docker inspect returned no JSON for container $ContainerId."
    }

    try {
        $parsed = @($json | ConvertFrom-Json)
    } catch {
        throw "Unable to parse docker inspect JSON for container ${ContainerId}: $($_.Exception.Message)"
    }
    if ($parsed.Count -ne 1) {
        throw "Expected one docker inspect object for container $ContainerId, got $($parsed.Count)."
    }
    return $parsed[0]
}

function Get-DockerServiceContainerIds {
    param(
        [string]$Docker,
        [string]$Service,
        [string]$ProjectName = ""
    )

    $arguments = @(
        "ps", "-q",
        "--filter", "label=com.docker.compose.service=$Service"
    )
    if (-not [string]::IsNullOrWhiteSpace($ProjectName)) {
        $arguments += @(
            "--filter", "label=com.docker.compose.project=$ProjectName"
        )
    }

    $result = Assert-NativeSuccess `
        -Executable $Docker `
        -Arguments $arguments `
        -ErrorMessage "Unable to inspect running Docker containers for service '$Service'"

    return @(
        Get-NativeOutputLines -Output $result.Output |
            Where-Object { $_ -match '^[0-9A-Fa-f]{12,64}$' }
    )
}

function Get-ContainerComposeProjectName {
    param(
        [string]$Docker,
        [string]$ContainerId
    )

    $inspect = Get-ContainerInspectObject -Docker $Docker -ContainerId $ContainerId
    $labels = $inspect.Config.Labels
    if ($null -eq $labels) {
        throw "Container $ContainerId has no Docker labels."
    }
    $property = $labels.PSObject.Properties["com.docker.compose.project"]
    if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        throw "Container $ContainerId has no com.docker.compose.project label."
    }
    return ([string]$property.Value).Trim()
}

function Resolve-RuntimeComposeProjectName {
    param([string]$Docker)

    $projects = @()
    foreach ($service in @("ops-worker", "panel-web")) {
        $ids = @(Get-DockerServiceContainerIds -Docker $Docker -Service $service)
        if ($ids.Count -lt 1) {
            throw "Expected at least one running $service container before cutover."
        }
        foreach ($containerId in $ids) {
            $projects += (Get-ContainerComposeProjectName -Docker $Docker -ContainerId $containerId)
        }
    }

    $uniqueProjects = @($projects | Sort-Object -Unique)
    if ($uniqueProjects.Count -ne 1) {
        throw "Running panel runtime spans multiple Compose projects: $($uniqueProjects -join ', ')"
    }
    return [string]$uniqueProjects[0]
}

function Wait-ContainersHealthy {
    param(
        [string]$Docker,
        [string[]]$ContainerIds,
        [string]$Service
    )

    if ($ContainerIds.Count -eq 0) {
        throw "No containers found for service '$Service' after recreate."
    }

    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $allHealthy = $true
        foreach ($containerId in $ContainerIds) {
            try {
                $inspect = Get-ContainerInspectObject -Docker $Docker -ContainerId $containerId
                $status = ([string]$inspect.State.Status).Trim().ToLowerInvariant()
                $health = ""
                if ($null -ne $inspect.State.Health) {
                    $health = ([string]$inspect.State.Health.Status).Trim().ToLowerInvariant()
                }
                if (-not [string]::IsNullOrWhiteSpace($health)) {
                    if ($health -ne "healthy") {
                        $allHealthy = $false
                        break
                    }
                } elseif ($status -ne "running") {
                    $allHealthy = $false
                    break
                }
            } catch {
                $allHealthy = $false
                break
            }
        }
        if ($allHealthy) {
            Write-Host "[GREEN] $Service containers are healthy."
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Service '$Service' did not become healthy after recreate."
}

function Assert-FallbackDisabledInContainers {
    param(
        [string]$Docker,
        [string[]]$ContainerIds,
        [string]$Service
    )

    foreach ($containerId in $ContainerIds) {
        $inspect = Get-ContainerInspectObject -Docker $Docker -ContainerId $containerId
        $environment = @($inspect.Config.Env)
        $fallbackLine = @(
            $environment |
                Where-Object { ([string]$_).StartsWith("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=") }
        )
        if ($fallbackLine.Count -ne 1 -or ([string]$fallbackLine[0]).Trim().ToLowerInvariant() -ne "app_storage_object_legacy_local_fallback_enabled=false") {
            throw "Fallback is not disabled inside $Service container $containerId."
        }
    }
    Write-Host "[GREEN] $Service runtime uses APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false."
}

function Invoke-OperatorScript {
    param(
        [string]$PowerShellExe,
        [string]$ScriptPath,
        [switch]$Validate
    )

    $arguments = @(
        "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", $ScriptPath
    )
    if ($Validate) {
        $arguments += "-ValidateOnly"
    }

    $result = Invoke-NativeCapture -Executable $PowerShellExe -Arguments $arguments
    foreach ($line in (Get-NativeOutputLines -Output $result.Output)) {
        Write-Host $line
    }
    if ($result.ExitCode -ne 0) {
        throw "Operator script failed: $ScriptPath (exit_code=$($result.ExitCode))"
    }
}

function Set-DotEnvFallbackDisabled {
    param([string]$EnvPath)

    if (-not (Test-Path -LiteralPath $EnvPath -PathType Leaf)) {
        throw "Production .env is missing: $EnvPath"
    }

    $original = [System.IO.File]::ReadAllText($EnvPath)
    $pattern = '(?m)^[ \t]*APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED[ \t]*=.*$'
    $matches = [regex]::Matches($original, $pattern)
    if ($matches.Count -gt 1) {
        throw "Production .env contains duplicate APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED entries. Resolve them manually before cutover."
    }

    $timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
    $backupPath = "${EnvPath}.storage-cutover-${timestamp}.bak"
    [System.IO.File]::Copy($EnvPath, $backupPath, $false)

    if ($matches.Count -eq 1) {
        $updated = [regex]::Replace(
            $original,
            $pattern,
            'APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false'
        )
    } else {
        $separator = if ($original.EndsWith("`r`n") -or $original.EndsWith("`n")) { "" } else { "`r`n" }
        $updated = $original + $separator + "APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED=false`r`n"
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($EnvPath, $updated, $utf8NoBom)
    return $backupPath
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$envPath = Join-Path $repoRoot ".env"
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$storageGate = Join-Path $repoRoot "scripts/docker-production-storage-cutover-gate.ps1"
$clientAvatarAudit = Join-Path $repoRoot "scripts/docker-production-client-avatar-cutover-audit.ps1"

foreach ($requiredFile in @($envPath, $composeFile, $storageGate, $clientAvatarAudit)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required cutover file is missing: $requiredFile"
    }
}

$powershellExe = Join-Path $PSHOME "powershell.exe"
if (-not (Test-Path -LiteralPath $powershellExe -PathType Leaf)) {
    $powershellCommand = Get-Command powershell.exe -ErrorAction SilentlyContinue
    if (-not $powershellCommand) {
        throw "powershell.exe is unavailable."
    }
    $powershellExe = $powershellCommand.Source
}

$composePrefix = @(
    "compose",
    "--project-directory", $repoRoot,
    "--env-file", $envPath,
    "-f", $composeFile
)

Assert-NativeSuccess `
    -Executable $docker `
    -Arguments ($composePrefix + @("config", "-q")) `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

if ($ValidateOnly) {
    Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $storageGate -Validate
    Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $clientAvatarAudit -Validate
    Write-Host "[GREEN] Storage fallback cutover helper validation passed."
    Write-Host "[RESULT] target_fallback_enabled=false"
    Write-Host "[RESULT] validation did not modify .env or runtime containers."
    return
}

$inheritedFallback = [Environment]::GetEnvironmentVariable(
    "APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED",
    "Process"
)
if (-not [string]::IsNullOrWhiteSpace($inheritedFallback) `
        -and $inheritedFallback.Trim().ToLowerInvariant() -ne "false") {
    throw "Current PowerShell environment overrides APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED='$inheritedFallback'. Clear that parent-shell override before cutover and rerun this helper."
}

Write-Host "[INFO] Running pre-cutover authoritative storage gate..."
Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $storageGate
Write-Host "[INFO] Running pre-cutover client avatar audit..."
Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $clientAvatarAudit

$runtimeProjectName = Resolve-RuntimeComposeProjectName -Docker $docker
$runtimeComposePrefix = $composePrefix + @("--project-name", $runtimeProjectName)
$workerIdsBefore = @(Get-DockerServiceContainerIds -Docker $docker -ProjectName $runtimeProjectName -Service "ops-worker")
$webIdsBefore = @(Get-DockerServiceContainerIds -Docker $docker -ProjectName $runtimeProjectName -Service "panel-web")
$workerReplicas = $workerIdsBefore.Count
$webReplicas = $webIdsBefore.Count
if ($workerReplicas -lt 1 -or $webReplicas -lt 1) {
    throw "Expected at least one running ops-worker and panel-web container before cutover. worker=$workerReplicas web=$webReplicas"
}

Write-Host "[INFO] Runtime Compose project: $runtimeProjectName"
Write-Host "[INFO] Preserving runtime scale: ops-worker=$workerReplicas panel-web=$webReplicas"
$backupPath = Set-DotEnvFallbackDisabled -EnvPath $envPath
Write-Host "[RESULT] env_backup=$backupPath"
Write-Host "[RESULT] persisted_fallback_enabled=false"

$oldIgnoreOrphans = [Environment]::GetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "Process")
$oldFallback = [Environment]::GetEnvironmentVariable("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED", "Process")
try {
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "true", "Process")
    [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED", "false", "Process")

    Assert-NativeSuccess `
        -Executable $docker `
        -Arguments ($runtimeComposePrefix + @(
            "up", "-d", "--no-deps", "--force-recreate",
            "--scale", "ops-worker=$workerReplicas",
            "ops-worker"
        )) `
        -ErrorMessage "Unable to recreate ops-worker with fallback disabled" | Out-Null

    $workerIdsAfter = @(Get-DockerServiceContainerIds -Docker $docker -ProjectName $runtimeProjectName -Service "ops-worker")
    if ($workerIdsAfter.Count -ne $workerReplicas) {
        throw "ops-worker replica count changed during cutover. before=$workerReplicas after=$($workerIdsAfter.Count)"
    }
    Wait-ContainersHealthy -Docker $docker -ContainerIds $workerIdsAfter -Service "ops-worker"
    Assert-FallbackDisabledInContainers -Docker $docker -ContainerIds $workerIdsAfter -Service "ops-worker"

    Assert-NativeSuccess `
        -Executable $docker `
        -Arguments ($runtimeComposePrefix + @(
            "up", "-d", "--no-deps", "--force-recreate",
            "--scale", "panel-web=$webReplicas",
            "panel-web"
        )) `
        -ErrorMessage "Unable to recreate panel-web with fallback disabled" | Out-Null

    $webIdsAfter = @(Get-DockerServiceContainerIds -Docker $docker -ProjectName $runtimeProjectName -Service "panel-web")
    if ($webIdsAfter.Count -ne $webReplicas) {
        throw "panel-web replica count changed during cutover. before=$webReplicas after=$($webIdsAfter.Count)"
    }
    Wait-ContainersHealthy -Docker $docker -ContainerIds $webIdsAfter -Service "panel-web"
    Assert-FallbackDisabledInContainers -Docker $docker -ContainerIds $webIdsAfter -Service "panel-web"

    Write-Host "[INFO] Running post-cutover authoritative storage gate..."
    Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $storageGate
    Write-Host "[INFO] Running post-cutover client avatar audit..."
    Invoke-OperatorScript -PowerShellExe $powershellExe -ScriptPath $clientAvatarAudit

    Write-Host "[GREEN] STORAGE FALLBACK CUTOVER COMPLETED."
    Write-Host "[RESULT] fallback_enabled=false"
    Write-Host "[RESULT] ops_worker_replicas=$workerReplicas"
    Write-Host "[RESULT] panel_web_replicas=$webReplicas"
    Write-Host "[RESULT] env_backup=$backupPath"
    Write-Host "[RESULT] MinIO, PostgreSQL, RabbitMQ, Redis, panel-direct, bots, observability and backup services were not recreated by this helper."
} finally {
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", $oldIgnoreOrphans, "Process")
    [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_LEGACY_LOCAL_FALLBACK_ENABLED", $oldFallback, "Process")
}
