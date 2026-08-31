param(
    [ValidateSet("postgresql", "rabbitmq", "redis", "minio", "grafana", "all")]
    [string]$Component = "",
    [string[]]$Components = @(),
    [string]$ProjectName = "",
    [string]$TargetPassword = "",
    [string]$TargetAccessKey = "",
    [string]$TargetSecretKey = "",
    [switch]$Apply,
    [switch]$Rehearsal,
    [string]$BackupDirectory = "",
    [int]$HealthTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-credential-migration-apply.ps1."
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

function Get-SupportedRotationComponents {
    return @("postgresql", "rabbitmq", "redis", "minio", "grafana")
}

function Resolve-RotationComponents {
    param(
        [string]$SingleComponent,
        [string[]]$MultipleComponents
    )

    if (-not [string]::IsNullOrWhiteSpace($SingleComponent) -and $MultipleComponents.Count -gt 0) {
        throw "Use either -Component or -Components, but not both."
    }

    $requested = New-Object 'System.Collections.Generic.List[string]'
    if (-not [string]::IsNullOrWhiteSpace($SingleComponent)) {
        $requested.Add($SingleComponent.Trim().ToLowerInvariant())
    }

    foreach ($entry in @($MultipleComponents)) {
        if ([string]::IsNullOrWhiteSpace($entry)) {
            continue
        }
        foreach ($token in $entry.Split(",", [System.StringSplitOptions]::RemoveEmptyEntries)) {
            $normalized = $token.Trim().ToLowerInvariant()
            if (-not [string]::IsNullOrWhiteSpace($normalized)) {
                $requested.Add($normalized)
            }
        }
    }

    if ($requested.Count -eq 0) {
        throw "Specify -Component <name>|all or -Components <name1,name2,...>."
    }

    $supported = Get-SupportedRotationComponents
    if (($requested -contains "all") -and $requested.Count -gt 1) {
        throw "Value 'all' cannot be combined with other components."
    }

    if ($requested.Count -eq 1 -and $requested[0] -eq "all") {
        return $supported
    }

    $requestedSet = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
    foreach ($candidate in $requested) {
        if (-not ($supported -contains $candidate)) {
            throw "Unsupported component '$candidate'. Supported values: $($supported -join ', ')."
        }
        [void]$requestedSet.Add($candidate)
    }

    $ordered = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidate in $supported) {
        if ($requestedSet.Contains($candidate)) {
            $ordered.Add($candidate)
        }
    }
    return @($ordered)
}

function New-PreApplyBackupSnapshot {
    param(
        [string]$DockerCommand,
        [string]$RepoRoot,
        [string]$ProjectName,
        [string[]]$SelectedComponents,
        [string]$RequestedDirectory
    )

    if ([string]::IsNullOrWhiteSpace($RequestedDirectory)) {
        return $null
    }

    $targetRoot = if ([System.IO.Path]::IsPathRooted($RequestedDirectory)) {
        $RequestedDirectory
    } else {
        Join-Path $RepoRoot $RequestedDirectory
    }

    $snapshotDir = Join-Path $targetRoot ("credential-rotation-{0}" -f (Get-Date -Format "yyyyMMdd-HHmmss"))
    [void](New-Item -ItemType Directory -Path $snapshotDir -Force)

    $envFile = Join-Path $RepoRoot ".env"
    if (Test-Path -LiteralPath $envFile) {
        Copy-FileExact -SourcePath $envFile -TargetPath (Join-Path $snapshotDir "env.before")
    }

    Write-Utf8NoBomFile -Path (Join-Path $snapshotDir "component-order.txt") -Content (($SelectedComponents -join [Environment]::NewLine) + [Environment]::NewLine)

    $containerSnapshot = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "ps",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--format", "table {{.Names}}`t{{.Status}}`t{{.Image}}"
    ) -IgnoreExitCode
    Write-Utf8NoBomFile -Path (Join-Path $snapshotDir "docker-ps.txt") -Content (($containerSnapshot.Text.TrimEnd()) + [Environment]::NewLine)

    return $snapshotDir
}

function Invoke-RotationOrchestration {
    param(
        [string]$ScriptPath,
        [string[]]$SelectedComponents,
        [string]$ProjectName,
        [switch]$Apply,
        [switch]$Rehearsal,
        [string]$BackupDirectory,
        [int]$HealthTimeoutSeconds
    )

    $mode = if ($Apply) { "apply" } elseif ($Rehearsal) { "rehearsal" } else { "dry-run" }
    Write-Host "[INFO] Credential rotation orchestration mode: $mode"
    Write-Host "[INFO] Ordered component flow: $($SelectedComponents -join ', ')"

    if ($SelectedComponents.Count -gt 1 -and (
        -not [string]::IsNullOrWhiteSpace($TargetPassword) -or
        -not [string]::IsNullOrWhiteSpace($TargetAccessKey) -or
        -not [string]::IsNullOrWhiteSpace($TargetSecretKey)
    )) {
        throw "Explicit target credential arguments are supported only for single-component runs."
    }

    if ($Apply -and $Rehearsal) {
        throw "-Apply and -Rehearsal cannot be used together."
    }

    $snapshotDir = $null
    if ($Apply) {
        $repoRoot = Get-RepoRoot
        $dockerCommand = Ensure-DockerAvailable
        $envPath = Join-Path $repoRoot ".env"
        $state = Read-DotEnvState -Path $envPath
        $project = Resolve-ComposeProjectName -ExplicitName $ProjectName -Settings $state.Settings -RepoRoot $repoRoot
        $snapshotDir = New-PreApplyBackupSnapshot -DockerCommand $dockerCommand -RepoRoot $repoRoot -ProjectName $project -SelectedComponents $SelectedComponents -RequestedDirectory $BackupDirectory
        if ($snapshotDir) {
            Write-Host "[INFO] Pre-apply snapshot created at: $snapshotDir"
        }
    } elseif (-not [string]::IsNullOrWhiteSpace($BackupDirectory)) {
        Write-Warning "BackupDirectory is used only together with -Apply. Snapshot creation skipped."
    }

    for ($index = 0; $index -lt $SelectedComponents.Count; $index++) {
        $component = $SelectedComponents[$index]
        Write-Host "[INFO] Starting step $($index + 1)/$($SelectedComponents.Count): $component"
        $childArgs = @{
            Component = $component
            ProjectName = $ProjectName
            HealthTimeoutSeconds = $HealthTimeoutSeconds
        }
        if (-not [string]::IsNullOrWhiteSpace($TargetPassword)) {
            $childArgs["TargetPassword"] = $TargetPassword
        }
        if (-not [string]::IsNullOrWhiteSpace($TargetAccessKey)) {
            $childArgs["TargetAccessKey"] = $TargetAccessKey
        }
        if (-not [string]::IsNullOrWhiteSpace($TargetSecretKey)) {
            $childArgs["TargetSecretKey"] = $TargetSecretKey
        }
        if ($Apply) {
            $childArgs["Apply"] = $true
        }

        & $ScriptPath @childArgs
        Write-Host "[INFO] Completed step $($index + 1)/$($SelectedComponents.Count): $component"
    }

    switch ($mode) {
        "apply" { Write-Host "[INFO] Bulk credential rotation apply completed successfully." }
        "rehearsal" { Write-Host "[INFO] Bulk credential rotation rehearsal completed successfully." }
        default { Write-Host "[INFO] Bulk credential rotation dry-run completed successfully." }
    }
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

function Write-Utf8NoBomFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function Read-DotEnvState {
    param(
        [string]$Path
    )

    $lines = New-Object 'System.Collections.Generic.List[string]'
    $settings = @{}
    $indices = @{}

    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{
            Lines = $lines
            Settings = $settings
            Indices = $indices
        }
    }

    $index = 0
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $lines.Add($line)
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $trimmed = $line.Trim()
            if (-not $trimmed.StartsWith("#")) {
                $separatorIndex = $trimmed.IndexOf("=")
                if ($separatorIndex -gt 0) {
                    $name = $trimmed.Substring(0, $separatorIndex).Trim()
                    $value = $trimmed.Substring($separatorIndex + 1)
                    if (-not $settings.ContainsKey($name)) {
                        $settings[$name] = $value
                        $indices[$name] = $index
                    }
                }
            }
        }
        $index++
    }

    return [pscustomobject]@{
        Lines = $lines
        Settings = $settings
        Indices = $indices
    }
}

function Get-SettingValue {
    param(
        [hashtable]$Settings,
        [string]$Name
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        return $fromEnvironment.Trim()
    }

    if ($Settings.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Settings[$Name])) {
        return ([string]$Settings[$Name]).Trim()
    }

    return ""
}

function Resolve-ComposeProjectName {
    param(
        [string]$ExplicitName,
        [hashtable]$Settings,
        [string]$RepoRoot
    )

    if (-not [string]::IsNullOrWhiteSpace($ExplicitName)) {
        return $ExplicitName.Trim()
    }

    $fromEnv = Get-SettingValue -Settings $Settings -Name "COMPOSE_PROJECT_NAME"
    if (-not [string]::IsNullOrWhiteSpace($fromEnv)) {
        return $fromEnv.Trim()
    }

    return Split-Path -Leaf $RepoRoot
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
        HealthStatus = if ($inspect.State.Health) { [string]$inspect.State.Health.Status } else { "" }
        ExitCode = if ($null -ne $inspect.State.ExitCode) { [int]$inspect.State.ExitCode } else { -1 }
        ConfigEnv = @($inspect.Config.Env)
    }
}

function Wait-ForServiceReady {
    param(
        [string]$DockerCommand,
        [string]$ProjectName,
        [string]$ServiceName,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $container = Get-ComposeContainerRecord -DockerCommand $DockerCommand -ProjectName $ProjectName -ServiceName $ServiceName
        if ($container -and $container.Running) {
            if ([string]::IsNullOrWhiteSpace($container.HealthStatus) -or $container.HealthStatus.Trim().ToLowerInvariant() -eq "healthy") {
                return
            }
        }
        Start-Sleep -Seconds 3
    }

    throw "Service '$ServiceName' did not become ready within $TimeoutSeconds seconds."
}

function Wait-ForServiceCompletionSuccess {
    param(
        [string]$DockerCommand,
        [string]$ProjectName,
        [string]$ServiceName,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $container = Get-ComposeContainerRecord -DockerCommand $DockerCommand -ProjectName $ProjectName -ServiceName $ServiceName
        if ($container) {
            if ($container.Status.Trim().ToLowerInvariant() -eq "exited" -and $container.ExitCode -eq 0) {
                return
            }
            if ($container.Status.Trim().ToLowerInvariant() -eq "dead") {
                throw "Service '$ServiceName' entered dead state."
            }
        }
        Start-Sleep -Seconds 3
    }

    throw "Service '$ServiceName' did not complete successfully within $TimeoutSeconds seconds."
}

function Set-OrAddDotEnvSetting {
    param(
        $State,
        [string]$Name,
        [string]$Value
    )

    $line = "$Name=$Value"
    if ($State.Indices.ContainsKey($Name)) {
        $index = [int]$State.Indices[$Name]
        $State.Lines[$index] = $line
    } else {
        if ($State.Lines.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($State.Lines[$State.Lines.Count - 1])) {
            $State.Lines.Add("")
        }
        $State.Lines.Add($line)
        $State.Indices[$Name] = $State.Lines.Count - 1
    }
    $State.Settings[$Name] = $Value
}

function Persist-DotEnvState {
    param(
        [string]$Path,
        $State
    )

    $content = ($State.Lines -join [Environment]::NewLine) + [Environment]::NewLine
    Write-Utf8NoBomFile -Path $Path -Content $content
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
        "--no-auth-warning",
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

function Resolve-CurrentPostgresPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [hashtable]$Settings,
        [string]$UserName,
        [string]$DatabaseName
    )

    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidate in @(
        (Get-SettingValue -Settings $Settings -Name "IGUANA_POSTGRES_PASSWORD"),
        (Get-SettingValue -Settings $Settings -Name "SPRING_DATASOURCE_PASSWORD"),
        "iguana"
    )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $candidates.Contains($candidate)) {
            $candidates.Add($candidate)
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-PostgresCredential -DockerCommand $DockerCommand -ContainerId $ContainerId -UserName $UserName -DatabaseName $DatabaseName -Password $candidate) {
            return $candidate
        }
    }

    throw "Unable to authenticate to live PostgreSQL with configured or documented fallback credentials."
}

function Resolve-CurrentRabbitPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [hashtable]$Settings,
        [string]$UserName
    )

    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidate in @(
        (Get-SettingValue -Settings $Settings -Name "IGUANA_RABBITMQ_PASSWORD"),
        (Get-SettingValue -Settings $Settings -Name "SPRING_RABBITMQ_PASSWORD"),
        "iguana"
    )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $candidates.Contains($candidate)) {
            $candidates.Add($candidate)
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-RabbitMqCredential -DockerCommand $DockerCommand -ContainerId $ContainerId -UserName $UserName -Password $candidate) {
            return $candidate
        }
    }

    throw "Unable to authenticate to live RabbitMQ with configured or documented fallback credentials."
}

function Resolve-CurrentRedisPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [hashtable]$Settings
    )

    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidate in @(
        (Get-SettingValue -Settings $Settings -Name "IGUANA_REDIS_PASSWORD"),
        (Get-SettingValue -Settings $Settings -Name "SPRING_DATA_REDIS_PASSWORD"),
        "iguana-redis"
    )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $candidates.Contains($candidate)) {
            $candidates.Add($candidate)
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-RedisCredential -DockerCommand $DockerCommand -ContainerId $ContainerId -Password $candidate) {
            return $candidate
        }
    }

    throw "Unable to authenticate to live Redis with configured or documented fallback credentials."
}

function Resolve-CurrentGrafanaPassword {
    param(
        [string]$BindHost,
        [string]$Port,
        [hashtable]$Settings,
        [string]$UserName
    )

    $candidates = New-Object 'System.Collections.Generic.List[string]'
    foreach ($candidate in @(
        (Get-SettingValue -Settings $Settings -Name "IGUANA_GRAFANA_ADMIN_PASSWORD"),
        "change-me",
        "admin",
        "grafana"
    )) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and -not $candidates.Contains($candidate)) {
            $candidates.Add($candidate)
        }
    }

    foreach ($candidate in $candidates) {
        if (Test-GrafanaCredential -BindHost $BindHost -Port $Port -UserName $UserName -Password $candidate) {
            return $candidate
        }
    }

    throw "Unable to authenticate to live Grafana with configured or documented fallback credentials."
}

function Resolve-CurrentMinIoCredentialPair {
    param(
        [string[]]$ConfigEnv
    )

    $envMap = Convert-EnvListToMap -Entries $ConfigEnv
    $accessKey = ""
    $secretKey = ""
    if ($envMap.ContainsKey("MINIO_ROOT_USER")) {
        $accessKey = [string]$envMap["MINIO_ROOT_USER"]
    }
    if ($envMap.ContainsKey("MINIO_ROOT_PASSWORD")) {
        $secretKey = [string]$envMap["MINIO_ROOT_PASSWORD"]
    }

    if ([string]::IsNullOrWhiteSpace($accessKey) -or [string]::IsNullOrWhiteSpace($secretKey)) {
        throw "Unable to resolve live MinIO root credentials from the running container environment."
    }

    return [pscustomobject]@{
        AccessKey = $accessKey
        SecretKey = $secretKey
    }
}

function Update-PostgresPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$UserName,
        [string]$DatabaseName,
        [string]$CurrentPassword,
        [string]$NewPassword
    )

    $safeUser = $UserName.Replace('"', '""')
    $safePassword = $NewPassword.Replace("'", "''")
    Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        "-e", "PGPASSWORD=$CurrentPassword",
        $ContainerId,
        "psql",
        "-h", "127.0.0.1",
        "-U", $UserName,
        "-d", $DatabaseName,
        "-v", "ON_ERROR_STOP=1",
        "-c", "ALTER USER `"$safeUser`" WITH PASSWORD '$safePassword';"
    ) | Out-Null
}

function Update-RabbitPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$UserName,
        [string]$NewPassword
    )

    Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        $ContainerId,
        "rabbitmqctl",
        "change_password",
        $UserName,
        $NewPassword
    ) | Out-Null
}

function Update-RedisPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$CurrentPassword,
        [string]$NewPassword
    )

    Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        $ContainerId,
        "redis-cli",
        "--no-auth-warning",
        "-a", $CurrentPassword,
        "CONFIG",
        "SET",
        "requirepass",
        $NewPassword
    ) | Out-Null
}

function Update-GrafanaPassword {
    param(
        [string]$DockerCommand,
        [string]$ContainerId,
        [string]$NewPassword
    )

    Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "exec",
        $ContainerId,
        "grafana",
        "cli",
        "--homepath", "/usr/share/grafana",
        "--config", "/etc/grafana/grafana.ini",
        "admin",
        "reset-admin-password",
        $NewPassword
    ) | Out-Null
}

function Get-RunningServiceNames {
    param(
        [string]$DockerCommand,
        [string]$ProjectName
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "ps",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--format", "{{.Names}}"
    ) -IgnoreExitCode

    $serviceNames = New-Object 'System.Collections.Generic.List[string]'
    foreach ($line in $result.Lines) {
        $name = "$line".Trim()
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }
        $serviceRecord = Get-ComposeServiceNameFromContainer -DockerCommand $DockerCommand -ContainerNameOrId $name
        if (-not [string]::IsNullOrWhiteSpace($serviceRecord) -and -not $serviceNames.Contains($serviceRecord)) {
            $serviceNames.Add($serviceRecord)
        }
    }
    return @($serviceNames)
}

function Get-ComposeServiceNameFromContainer {
    param(
        [string]$DockerCommand,
        [string]$ContainerNameOrId
    )

    $inspectJson = (Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "inspect",
        $ContainerNameOrId
    ) -IgnoreExitCode).Text
    if ([string]::IsNullOrWhiteSpace($inspectJson)) {
        return ""
    }

    $inspect = $inspectJson | ConvertFrom-Json
    if ($inspect -is [array]) {
        $inspect = $inspect[0]
    }

    if ($inspect.Config -and $inspect.Config.Labels) {
        return [string]$inspect.Config.Labels.'com.docker.compose.service'
    }

    return ""
}

function Get-ComposeFilesForServices {
    param(
        [string[]]$Services,
        [string[]]$RunningServices,
        [string]$RepoRoot
    )

    $files = New-Object 'System.Collections.Generic.List[string]'
    $files.Add((Join-Path $RepoRoot "docker-compose.production-contour.yml"))

    $observabilityServices = @(
        "postgres-exporter",
        "redis-exporter",
        "alertmanager",
        "prometheus",
        "loki",
        "alloy",
        "grafana"
    )
    if ($RunningServices | Where-Object { $observabilityServices -contains $_ }) {
        $files.Add((Join-Path $RepoRoot "docker-compose.production-observability.yml"))
    }

    return @($files)
}

function Invoke-ComposeRecreate {
    param(
        [string]$DockerCommand,
        [string[]]$ComposeFiles,
        [string[]]$Services,
        [string]$ProjectName
    )

    if (-not $Services -or $Services.Count -eq 0) {
        return
    }

    $arguments = New-Object 'System.Collections.Generic.List[string]'
    $arguments.Add("compose")
    foreach ($file in $ComposeFiles) {
        $arguments.Add("-f")
        $arguments.Add($file)
    }
    $arguments.Add("-p")
    $arguments.Add($ProjectName)
    $arguments.Add("up")
    $arguments.Add("-d")
    $arguments.Add("--force-recreate")
    foreach ($service in $Services) {
        $arguments.Add($service)
    }

    Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @($arguments) | Out-Null
}

function Copy-FileExact {
    param(
        [string]$SourcePath,
        [string]$TargetPath
    )

    $content = [System.IO.File]::ReadAllText($SourcePath, [System.Text.Encoding]::UTF8)
    Write-Utf8NoBomFile -Path $TargetPath -Content $content
}

function Get-ComposeNetworkName {
    param(
        [string]$DockerCommand,
        [string]$ProjectName
    )

    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "network", "ls",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--filter", "label=com.docker.compose.network=default",
        "--format", "{{.Name}}"
    ) -IgnoreExitCode
    $resolved = $result.Text.Trim()
    if (-not [string]::IsNullOrWhiteSpace($resolved)) {
        return $resolved
    }
    return "$ProjectName" + "_default"
}

function Test-MinIoBucketAccess {
    param(
        [string]$DockerCommand,
        [string]$NetworkName,
        [string]$AccessKey,
        [string]$SecretKey,
        [string]$BucketName
    )

    $command = 'mc alias set local http://minio:9000 "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null && mc ls "local/$MINIO_BUCKET" >/dev/null'
    $result = Invoke-DockerCli -DockerCommand $DockerCommand -Arguments @(
        "run",
        "--rm",
        "--network", $NetworkName,
        "-e", "MINIO_ACCESS_KEY=$AccessKey",
        "-e", "MINIO_SECRET_KEY=$SecretKey",
        "-e", "MINIO_BUCKET=$BucketName",
        "--entrypoint", "/bin/sh",
        "minio/mc:RELEASE.2025-07-21T05-28-08Z",
        "-c",
        $command
    ) -IgnoreExitCode
    return $result.ExitCode -eq 0
}

if ($Apply -and $Rehearsal) {
    throw "-Apply and -Rehearsal cannot be used together."
}

$selectedComponents = @(Resolve-RotationComponents -SingleComponent $Component -MultipleComponents $Components)
$scriptPath = $PSCommandPath
$useOrchestration = $Rehearsal -or
    ($selectedComponents.Count -gt 1) -or
    ($Components.Count -gt 0) -or
    ($Component -eq "all") -or
    (-not [string]::IsNullOrWhiteSpace($BackupDirectory))

if ($useOrchestration) {
    Invoke-RotationOrchestration -ScriptPath $scriptPath -SelectedComponents $selectedComponents -ProjectName $ProjectName -Apply:$Apply -Rehearsal:$Rehearsal -BackupDirectory $BackupDirectory -HealthTimeoutSeconds $HealthTimeoutSeconds
    return
}

$Component = $selectedComponents[0]
$repoRoot = Get-RepoRoot
$dockerCommand = Ensure-DockerAvailable
$envPath = Join-Path $repoRoot ".env"
$state = Read-DotEnvState -Path $envPath
$project = Resolve-ComposeProjectName -ExplicitName $ProjectName -Settings $state.Settings -RepoRoot $repoRoot
$runningServices = Get-RunningServiceNames -DockerCommand $dockerCommand -ProjectName $project

$backupPath = Join-Path $repoRoot (".env.credential-migration-{0}-{1}.bak" -f $Component, (Get-Date -Format "yyyyMMdd-HHmmss"))
$liveChanged = $false
$envUpdated = $false
$restartAttempted = $false

switch ($Component) {
    "postgresql" {
        $serviceName = "postgres"
        $container = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
        if (-not $container -or -not $container.Running) {
            throw "PostgreSQL container is not running for compose project '$project'."
        }

        $userName = Get-SettingValue -Settings $state.Settings -Name "IGUANA_POSTGRES_USER"
        if ([string]::IsNullOrWhiteSpace($userName)) { $userName = "iguana" }
        $databaseName = Get-SettingValue -Settings $state.Settings -Name "IGUANA_POSTGRES_DB"
        if ([string]::IsNullOrWhiteSpace($databaseName)) { $databaseName = "iguana" }
        $currentPassword = Resolve-CurrentPostgresPassword -DockerCommand $dockerCommand -ContainerId $container.Id -Settings $state.Settings -UserName $userName -DatabaseName $databaseName
        $newPassword = $TargetPassword
        if ([string]::IsNullOrWhiteSpace($newPassword)) {
            $newPassword = New-RandomHexToken
        }
        if ($newPassword -eq $currentPassword) {
            throw "Target PostgreSQL password must differ from the current live password."
        }

        $restartServices = New-Object 'System.Collections.Generic.List[string]'
        foreach ($candidate in @("ops-worker", "panel-web", "postgres-exporter")) {
            if ($runningServices -contains $candidate -and -not $restartServices.Contains($candidate)) {
                $restartServices.Add($candidate)
            }
        }
        $composeFiles = Get-ComposeFilesForServices -Services @($restartServices) -RunningServices $runningServices -RepoRoot $repoRoot

        if (-not $Apply) {
            Write-Host "[INFO] Dry-run: PostgreSQL credential migration plan is ready."
            Write-Host "[INFO] Compose project: $project"
            Write-Host "[INFO] Live PostgreSQL authentication succeeded with the current credential candidate."
            Write-Host "[INFO] Planned updates: IGUANA_POSTGRES_PASSWORD and SPRING_DATASOURCE_PASSWORD in repository .env."
            Write-Host "[INFO] Planned dependent service recreate: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint file would be created at: $backupPath"
            return
        }

        if (Test-Path -LiteralPath $envPath) {
            Copy-FileExact -SourcePath $envPath -TargetPath $backupPath
        }

        try {
            Update-PostgresPassword -DockerCommand $dockerCommand -ContainerId $container.Id -UserName $userName -DatabaseName $databaseName -CurrentPassword $currentPassword -NewPassword $newPassword
            $liveChanged = $true

            if (-not (Test-PostgresCredential -DockerCommand $dockerCommand -ContainerId $container.Id -UserName $userName -DatabaseName $databaseName -Password $newPassword)) {
                throw "PostgreSQL accepted the password change command, but live verification with the new credential failed."
            }

            Set-OrAddDotEnvSetting -State $state -Name "IGUANA_POSTGRES_PASSWORD" -Value $newPassword
            $springUrl = Get-SettingValue -Settings $state.Settings -Name "SPRING_DATASOURCE_URL"
            if ([string]::IsNullOrWhiteSpace($springUrl) -or $springUrl -match "^jdbc:postgresql://(localhost|127\.0\.0\.1):") {
                Set-OrAddDotEnvSetting -State $state -Name "SPRING_DATASOURCE_PASSWORD" -Value $newPassword
            }
            Persist-DotEnvState -Path $envPath -State $state
            $envUpdated = $true

            Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
            $restartAttempted = $true

            foreach ($service in $restartServices) {
                Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $service -TimeoutSeconds $HealthTimeoutSeconds
            }

            $postgresAfterRestart = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
            if (-not $postgresAfterRestart -or -not $postgresAfterRestart.Running) {
                throw "PostgreSQL container is not running after dependent service recreation."
            }
            if (-not (Test-PostgresCredential -DockerCommand $dockerCommand -ContainerId $postgresAfterRestart.Id -UserName $userName -DatabaseName $databaseName -Password $newPassword)) {
                throw "PostgreSQL auth verification failed after dependent service recreation."
            }

            Write-Host "[INFO] PostgreSQL credential rotation applied successfully."
            Write-Host "[INFO] Updated repository .env and recreated dependent services: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint: $backupPath"
        } catch {
            if ($liveChanged) {
                try {
                    $currentContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
                    if ($currentContainer -and $currentContainer.Running) {
                        Update-PostgresPassword -DockerCommand $dockerCommand -ContainerId $currentContainer.Id -UserName $userName -DatabaseName $databaseName -CurrentPassword $newPassword -NewPassword $currentPassword
                    }
                } catch {
                    Write-Warning "Best-effort PostgreSQL rollback failed. Manual intervention may be required."
                }
            }

            if ($envUpdated -and (Test-Path -LiteralPath $backupPath)) {
                Copy-FileExact -SourcePath $backupPath -TargetPath $envPath
            }

            if ($restartAttempted) {
                try {
                    Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
                } catch {
                    Write-Warning "Best-effort dependent service rollback recreate failed. Manual restart may be required."
                }
            }

            throw
        }
    }
    "rabbitmq" {
        $serviceName = "rabbitmq"
        $container = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
        if (-not $container -or -not $container.Running) {
            throw "RabbitMQ container is not running for compose project '$project'."
        }

        $userName = Get-SettingValue -Settings $state.Settings -Name "IGUANA_RABBITMQ_USER"
        if ([string]::IsNullOrWhiteSpace($userName)) { $userName = "iguana" }
        $currentPassword = Resolve-CurrentRabbitPassword -DockerCommand $dockerCommand -ContainerId $container.Id -Settings $state.Settings -UserName $userName
        $newPassword = $TargetPassword
        if ([string]::IsNullOrWhiteSpace($newPassword)) {
            $newPassword = New-RandomHexToken
        }
        if ($newPassword -eq $currentPassword) {
            throw "Target RabbitMQ password must differ from the current live password."
        }

        $restartServices = New-Object 'System.Collections.Generic.List[string]'
        foreach ($candidate in @("ops-worker", "panel-web", "bot-telegram", "bot-vk", "bot-max")) {
            if ($runningServices -contains $candidate -and -not $restartServices.Contains($candidate)) {
                $restartServices.Add($candidate)
            }
        }
        $composeFiles = Get-ComposeFilesForServices -Services @($restartServices) -RunningServices $runningServices -RepoRoot $repoRoot

        if (-not $Apply) {
            Write-Host "[INFO] Dry-run: RabbitMQ credential migration plan is ready."
            Write-Host "[INFO] Compose project: $project"
            Write-Host "[INFO] Live RabbitMQ authentication succeeded with the current credential candidate."
            Write-Host "[INFO] Planned updates: IGUANA_RABBITMQ_PASSWORD and SPRING_RABBITMQ_PASSWORD in repository .env."
            Write-Host "[INFO] Planned dependent service recreate: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint file would be created at: $backupPath"
            return
        }

        if (Test-Path -LiteralPath $envPath) {
            Copy-FileExact -SourcePath $envPath -TargetPath $backupPath
        }

        try {
            Update-RabbitPassword -DockerCommand $dockerCommand -ContainerId $container.Id -UserName $userName -NewPassword $newPassword
            $liveChanged = $true

            if (-not (Test-RabbitMqCredential -DockerCommand $dockerCommand -ContainerId $container.Id -UserName $userName -Password $newPassword)) {
                throw "RabbitMQ accepted the password change command, but live verification with the new credential failed."
            }

            Set-OrAddDotEnvSetting -State $state -Name "IGUANA_RABBITMQ_PASSWORD" -Value $newPassword
            $rabbitHost = Get-SettingValue -Settings $state.Settings -Name "SPRING_RABBITMQ_HOST"
            if ([string]::IsNullOrWhiteSpace($rabbitHost) -or $rabbitHost -eq "localhost" -or $rabbitHost -eq "127.0.0.1") {
                Set-OrAddDotEnvSetting -State $state -Name "SPRING_RABBITMQ_PASSWORD" -Value $newPassword
            }
            Persist-DotEnvState -Path $envPath -State $state
            $envUpdated = $true

            Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
            $restartAttempted = $true

            foreach ($service in $restartServices) {
                Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $service -TimeoutSeconds $HealthTimeoutSeconds
            }

            $rabbitAfterRestart = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
            if (-not $rabbitAfterRestart -or -not $rabbitAfterRestart.Running) {
                throw "RabbitMQ container is not running after dependent service recreation."
            }
            if (-not (Test-RabbitMqCredential -DockerCommand $dockerCommand -ContainerId $rabbitAfterRestart.Id -UserName $userName -Password $newPassword)) {
                throw "RabbitMQ auth verification failed after dependent service recreation."
            }

            Write-Host "[INFO] RabbitMQ credential rotation applied successfully."
            Write-Host "[INFO] Updated repository .env and recreated dependent services: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint: $backupPath"
        } catch {
            if ($liveChanged) {
                try {
                    $currentContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
                    if ($currentContainer -and $currentContainer.Running) {
                        Update-RabbitPassword -DockerCommand $dockerCommand -ContainerId $currentContainer.Id -UserName $userName -NewPassword $currentPassword
                    }
                } catch {
                    Write-Warning "Best-effort RabbitMQ rollback failed. Manual intervention may be required."
                }
            }

            if ($envUpdated -and (Test-Path -LiteralPath $backupPath)) {
                Copy-FileExact -SourcePath $backupPath -TargetPath $envPath
            }

            if ($restartAttempted) {
                try {
                    Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
                } catch {
                    Write-Warning "Best-effort dependent service rollback recreate failed. Manual restart may be required."
                }
            }

            throw
        }
    }
    "redis" {
        $serviceName = "redis"
        $container = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
        if (-not $container -or -not $container.Running) {
            throw "Redis container is not running for compose project '$project'."
        }

        $currentPassword = Resolve-CurrentRedisPassword -DockerCommand $dockerCommand -ContainerId $container.Id -Settings $state.Settings
        $newPassword = $TargetPassword
        if ([string]::IsNullOrWhiteSpace($newPassword)) {
            $newPassword = New-RandomHexToken
        }
        if ($newPassword -eq $currentPassword) {
            throw "Target Redis password must differ from the current live password."
        }

        $restartServices = New-Object 'System.Collections.Generic.List[string]'
        foreach ($candidate in @("redis", "redis-exporter", "ops-worker", "panel-web", "bot-telegram", "bot-vk", "bot-max")) {
            if ($runningServices -contains $candidate -and -not $restartServices.Contains($candidate)) {
                $restartServices.Add($candidate)
            }
        }
        $composeFiles = Get-ComposeFilesForServices -Services @($restartServices) -RunningServices $runningServices -RepoRoot $repoRoot

        if (-not $Apply) {
            Write-Host "[INFO] Dry-run: Redis credential migration plan is ready."
            Write-Host "[INFO] Compose project: $project"
            Write-Host "[INFO] Live Redis authentication succeeded with the current credential candidate."
            Write-Host "[INFO] Planned updates: IGUANA_REDIS_PASSWORD and SPRING_DATA_REDIS_PASSWORD in repository .env."
            Write-Host "[INFO] Planned service recreate: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint file would be created at: $backupPath"
            return
        }

        if (Test-Path -LiteralPath $envPath) {
            Copy-FileExact -SourcePath $envPath -TargetPath $backupPath
        }

        try {
            Update-RedisPassword -DockerCommand $dockerCommand -ContainerId $container.Id -CurrentPassword $currentPassword -NewPassword $newPassword
            $liveChanged = $true

            if (-not (Test-RedisCredential -DockerCommand $dockerCommand -ContainerId $container.Id -Password $newPassword)) {
                throw "Redis accepted the password change command, but live verification with the new credential failed."
            }

            Set-OrAddDotEnvSetting -State $state -Name "IGUANA_REDIS_PASSWORD" -Value $newPassword
            $redisHost = Get-SettingValue -Settings $state.Settings -Name "SPRING_DATA_REDIS_HOST"
            if ([string]::IsNullOrWhiteSpace($redisHost) -or $redisHost -eq "localhost" -or $redisHost -eq "127.0.0.1") {
                Set-OrAddDotEnvSetting -State $state -Name "SPRING_DATA_REDIS_PASSWORD" -Value $newPassword
            }
            Persist-DotEnvState -Path $envPath -State $state
            $envUpdated = $true

            Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
            $restartAttempted = $true

            foreach ($service in $restartServices) {
                Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $service -TimeoutSeconds $HealthTimeoutSeconds
            }

            $redisAfterRestart = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
            if (-not $redisAfterRestart -or -not $redisAfterRestart.Running) {
                throw "Redis container is not running after coordinated service recreation."
            }
            if (-not (Test-RedisCredential -DockerCommand $dockerCommand -ContainerId $redisAfterRestart.Id -Password $newPassword)) {
                throw "Redis auth verification failed after coordinated service recreation."
            }

            Write-Host "[INFO] Redis credential rotation applied successfully."
            Write-Host "[INFO] Updated repository .env and recreated services: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint: $backupPath"
        } catch {
            if ($liveChanged) {
                try {
                    $currentContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
                    if ($currentContainer -and $currentContainer.Running) {
                        Update-RedisPassword -DockerCommand $dockerCommand -ContainerId $currentContainer.Id -CurrentPassword $newPassword -NewPassword $currentPassword
                    }
                } catch {
                    Write-Warning "Best-effort Redis rollback failed. Manual intervention may be required."
                }
            }

            if ($envUpdated -and (Test-Path -LiteralPath $backupPath)) {
                Copy-FileExact -SourcePath $backupPath -TargetPath $envPath
            }

            if ($restartAttempted) {
                try {
                    Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
                } catch {
                    Write-Warning "Best-effort coordinated Redis rollback recreate failed. Manual restart may be required."
                }
            }

            throw
        }
    }
    "minio" {
        $serviceName = "minio"
        $initServiceName = "minio-init"
        $container = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
        if (-not $container -or -not $container.Running) {
            throw "MinIO container is not running for compose project '$project'."
        }

        $networkName = Get-ComposeNetworkName -DockerCommand $dockerCommand -ProjectName $project
        $bucketName = Get-SettingValue -Settings $state.Settings -Name "APP_STORAGE_OBJECT_BUCKET"
        if ([string]::IsNullOrWhiteSpace($bucketName)) {
            $bucketName = "iguana"
        }

        $currentMinio = Resolve-CurrentMinIoCredentialPair -ConfigEnv $container.ConfigEnv
        if (-not (Test-MinIoBucketAccess -DockerCommand $dockerCommand -NetworkName $networkName -AccessKey $currentMinio.AccessKey -SecretKey $currentMinio.SecretKey -BucketName $bucketName)) {
            throw "Unable to verify live MinIO bucket access with the current runtime credentials."
        }

        $newAccessKey = $TargetAccessKey
        if ([string]::IsNullOrWhiteSpace($newAccessKey)) {
            $newAccessKey = New-RandomHexToken -BytesLength 12
        }
        $newSecretKey = $TargetSecretKey
        if ([string]::IsNullOrWhiteSpace($newSecretKey)) {
            $newSecretKey = New-RandomHexToken -BytesLength 32
        }
        if ($newAccessKey -eq $currentMinio.AccessKey -and $newSecretKey -eq $currentMinio.SecretKey) {
            throw "At least one MinIO target credential value must differ from the current live runtime."
        }

        $restartServices = New-Object 'System.Collections.Generic.List[string]'
        foreach ($candidate in @("minio", "minio-init", "ops-worker", "panel-web", "bot-telegram", "bot-vk", "bot-max")) {
            if (($candidate -eq "minio" -or $candidate -eq "minio-init") -or ($runningServices -contains $candidate)) {
                if (-not $restartServices.Contains($candidate)) {
                    $restartServices.Add($candidate)
                }
            }
        }
        $composeFiles = Get-ComposeFilesForServices -Services @($restartServices) -RunningServices $runningServices -RepoRoot $repoRoot

        if (-not $Apply) {
            Write-Host "[INFO] Dry-run: MinIO credential migration plan is ready."
            Write-Host "[INFO] Compose project: $project"
            Write-Host "[INFO] Live MinIO bucket access succeeded with the current runtime credentials."
            Write-Host "[INFO] Planned updates: APP_STORAGE_OBJECT_ACCESS_KEY and APP_STORAGE_OBJECT_SECRET_KEY in repository .env."
            Write-Host "[INFO] Planned service recreate: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint file would be created at: $backupPath"
            return
        }

        if (Test-Path -LiteralPath $envPath) {
            Copy-FileExact -SourcePath $envPath -TargetPath $backupPath
        }

        try {
            Set-OrAddDotEnvSetting -State $state -Name "APP_STORAGE_OBJECT_ACCESS_KEY" -Value $newAccessKey
            Set-OrAddDotEnvSetting -State $state -Name "APP_STORAGE_OBJECT_SECRET_KEY" -Value $newSecretKey
            Persist-DotEnvState -Path $envPath -State $state
            $envUpdated = $true

            Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
            $restartAttempted = $true

            Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName -TimeoutSeconds $HealthTimeoutSeconds
            Wait-ForServiceCompletionSuccess -DockerCommand $dockerCommand -ProjectName $project -ServiceName $initServiceName -TimeoutSeconds $HealthTimeoutSeconds
            foreach ($service in $restartServices) {
                if ($service -ne $serviceName -and $service -ne $initServiceName) {
                    Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $service -TimeoutSeconds $HealthTimeoutSeconds
                }
            }

            $minioAfterRestart = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
            if (-not $minioAfterRestart -or -not $minioAfterRestart.Running) {
                throw "MinIO container is not running after coordinated service recreation."
            }

            $runtimeEnv = Convert-EnvListToMap -Entries $minioAfterRestart.ConfigEnv
            if (-not $runtimeEnv.ContainsKey("MINIO_ROOT_USER") -or -not $runtimeEnv.ContainsKey("MINIO_ROOT_PASSWORD")) {
                throw "MinIO runtime environment does not expose MINIO_ROOT_USER/MINIO_ROOT_PASSWORD after recreate."
            }
            if ($runtimeEnv["MINIO_ROOT_USER"] -ne $newAccessKey -or $runtimeEnv["MINIO_ROOT_PASSWORD"] -ne $newSecretKey) {
                throw "MinIO runtime environment does not match the newly persisted object-storage credentials."
            }
            if (-not (Test-MinIoBucketAccess -DockerCommand $dockerCommand -NetworkName $networkName -AccessKey $newAccessKey -SecretKey $newSecretKey -BucketName $bucketName)) {
                throw "MinIO bucket access verification failed after coordinated service recreation."
            }

            Write-Host "[INFO] MinIO credential rotation applied successfully."
            Write-Host "[INFO] Updated repository .env and recreated services: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint: $backupPath"
        } catch {
            if ($envUpdated -and (Test-Path -LiteralPath $backupPath)) {
                Copy-FileExact -SourcePath $backupPath -TargetPath $envPath
            }

            if ($restartAttempted) {
                try {
                    Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
                } catch {
                    Write-Warning "Best-effort coordinated MinIO rollback recreate failed. Manual restart may be required."
                }
            }

            throw
        }
    }
    "grafana" {
        $serviceName = "grafana"
        $container = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
        if (-not $container -or -not $container.Running) {
            throw "Grafana container is not running for compose project '$project'."
        }

        $userName = Get-SettingValue -Settings $state.Settings -Name "IGUANA_GRAFANA_ADMIN_USER"
        if ([string]::IsNullOrWhiteSpace($userName)) { $userName = "admin" }
        $bindHost = Get-SettingValue -Settings $state.Settings -Name "IGUANA_GRAFANA_BIND_HOST"
        if ([string]::IsNullOrWhiteSpace($bindHost)) { $bindHost = "127.0.0.1" }
        $port = Get-SettingValue -Settings $state.Settings -Name "IGUANA_GRAFANA_PORT"
        if ([string]::IsNullOrWhiteSpace($port)) { $port = "3000" }

        $currentPassword = Resolve-CurrentGrafanaPassword -BindHost $bindHost -Port $port -Settings $state.Settings -UserName $userName
        $newPassword = $TargetPassword
        if ([string]::IsNullOrWhiteSpace($newPassword)) {
            $newPassword = New-RandomHexToken
        }
        if ($newPassword -eq $currentPassword) {
            throw "Target Grafana password must differ from the current live password."
        }

        $restartServices = New-Object 'System.Collections.Generic.List[string]'
        foreach ($candidate in @("grafana")) {
            if ($runningServices -contains $candidate -and -not $restartServices.Contains($candidate)) {
                $restartServices.Add($candidate)
            }
        }
        $composeFiles = Get-ComposeFilesForServices -Services @($restartServices) -RunningServices $runningServices -RepoRoot $repoRoot

        if (-not $Apply) {
            Write-Host "[INFO] Dry-run: Grafana credential migration plan is ready."
            Write-Host "[INFO] Compose project: $project"
            Write-Host "[INFO] Live Grafana authentication succeeded with the current credential candidate."
            Write-Host "[INFO] Planned updates: IGUANA_GRAFANA_ADMIN_PASSWORD in repository .env."
            Write-Host "[INFO] Planned service recreate: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint file would be created at: $backupPath"
            return
        }

        if (Test-Path -LiteralPath $envPath) {
            Copy-FileExact -SourcePath $envPath -TargetPath $backupPath
        }

        try {
            Update-GrafanaPassword -DockerCommand $dockerCommand -ContainerId $container.Id -NewPassword $newPassword
            $liveChanged = $true

            if (-not (Test-GrafanaCredential -BindHost $bindHost -Port $port -UserName $userName -Password $newPassword)) {
                throw "Grafana accepted the password change command, but live verification with the new credential failed."
            }

            Set-OrAddDotEnvSetting -State $state -Name "IGUANA_GRAFANA_ADMIN_PASSWORD" -Value $newPassword
            Persist-DotEnvState -Path $envPath -State $state
            $envUpdated = $true

            Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
            $restartAttempted = $true

            foreach ($service in $restartServices) {
                Wait-ForServiceReady -DockerCommand $dockerCommand -ProjectName $project -ServiceName $service -TimeoutSeconds $HealthTimeoutSeconds
            }

            $grafanaAfterRestart = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
            if (-not $grafanaAfterRestart -or -not $grafanaAfterRestart.Running) {
                throw "Grafana container is not running after service recreation."
            }
            if (-not (Test-GrafanaCredential -BindHost $bindHost -Port $port -UserName $userName -Password $newPassword)) {
                throw "Grafana auth verification failed after service recreation."
            }

            Write-Host "[INFO] Grafana credential rotation applied successfully."
            Write-Host "[INFO] Updated repository .env and recreated services: $(@($restartServices) -join ', ')"
            Write-Host "[INFO] Rollback checkpoint: $backupPath"
        } catch {
            if ($liveChanged) {
                try {
                    $currentContainer = Get-ComposeContainerRecord -DockerCommand $dockerCommand -ProjectName $project -ServiceName $serviceName
                    if ($currentContainer -and $currentContainer.Running) {
                        Update-GrafanaPassword -DockerCommand $dockerCommand -ContainerId $currentContainer.Id -NewPassword $currentPassword
                    }
                } catch {
                    Write-Warning "Best-effort Grafana rollback failed. Manual intervention may be required."
                }
            }

            if ($envUpdated -and (Test-Path -LiteralPath $backupPath)) {
                Copy-FileExact -SourcePath $backupPath -TargetPath $envPath
            }

            if ($restartAttempted) {
                try {
                    Invoke-ComposeRecreate -DockerCommand $dockerCommand -ComposeFiles $composeFiles -Services @($restartServices) -ProjectName $project
                } catch {
                    Write-Warning "Best-effort Grafana rollback recreate failed. Manual restart may be required."
                }
            }

            throw
        }
    }
}
