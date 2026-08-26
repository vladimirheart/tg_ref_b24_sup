param(
    [switch]$KeepArtifacts,
    [switch]$KeepStackOnFailure,
    [int]$TimeoutSeconds = 300
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Fail([string]$Message) {
    throw $Message
}

function Get-FreeTcpPort {
    $listener = New-Object System.Net.Sockets.TcpListener([System.Net.IPAddress]::Loopback, 0)
    try {
        $listener.Start()
        return ([System.Net.IPEndPoint]$listener.LocalEndpoint).Port
    } finally {
        $listener.Stop()
    }
}

function New-Secret([string]$Prefix) {
    return $Prefix + "-" + [Guid]::NewGuid().ToString("N")
}

function Wait-ContainerHealthy {
    param(
        [string]$Docker,
        [string]$ContainerId,
        [int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        $status = (& $Docker inspect --format "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" $ContainerId 2>$null)
        if ($LASTEXITCODE -eq 0) {
            $status = [string]$status
            if ($status.Trim() -eq "healthy") {
                return
            }
            if ($status.Trim() -in @("exited", "dead")) {
                Fail "Container $ContainerId stopped before becoming healthy."
            }
        }
        Start-Sleep -Seconds 2
    }
    Fail "Timed out waiting for healthy container $ContainerId."
}

function Wait-MigratorCompleted {
    param(
        [string]$Docker,
        [string]$ContainerId,
        [int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    while ((Get-Date) -lt $deadline) {
        $state = (& $Docker inspect --format "{{.State.Status}}|{{.State.ExitCode}}" $ContainerId 2>$null)
        if ($LASTEXITCODE -eq 0 -and $state) {
            $parts = ([string]$state).Trim().Split("|")
            if ($parts[0] -eq "exited") {
                if ([int]$parts[1] -ne 0) {
                    Write-Host "[SMOKE] db-migrate logs (last 200 lines):"
                    & $Docker logs --tail 200 $ContainerId 2>&1 | ForEach-Object { Write-Host $_ }
                    Fail "db-migrate exited with code $($parts[1])."
                }
                return
            }
        }
        Start-Sleep -Seconds 2
    }
    Fail "Timed out waiting for db-migrate completion."
}

function Invoke-ContainerRuntimeInfo {
    param(
        [string]$Docker,
        [string]$ContainerId
    )

    $json = & $Docker exec $ContainerId curl -fsS http://localhost:8080/actuator/info
    if ($LASTEXITCODE -ne 0) {
        Fail "Unable to read /actuator/info from container $ContainerId."
    }
    return ($json | ConvertFrom-Json)
}

function Convert-HttpResponseContentToJson {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Response
    )

    $content = $Response.Content
    if ($null -eq $content) {
        Fail "HTTP response body is null."
    }

    if ($content -is [byte[]]) {
        $text = [System.Text.Encoding]::UTF8.GetString([byte[]]$content)
    } elseif ($content -is [System.Array] -and $content.Count -gt 0 -and $content[0] -is [byte]) {
        $bytes = [byte[]]$content
        $text = [System.Text.Encoding]::UTF8.GetString($bytes)
    } else {
        $text = [string]$content
    }

    if ([string]::IsNullOrWhiteSpace($text)) {
        Fail "HTTP response body is empty."
    }

    try {
        return (ConvertFrom-Json -InputObject $text)
    } catch {
        $contentType = ""
        try {
            $contentType = [string]$Response.Headers["Content-Type"]
        } catch {
            $contentType = "<unavailable>"
        }

        Fail "Unable to parse JSON HTTP response (Content-Type=$contentType): $text"
    }
}
function Wait-HttpRuntimeRole {
    param(
        [string]$Uri,
        [string]$ExpectedRole,
        [int]$Timeout
    )

    $deadline = (Get-Date).AddSeconds($Timeout)
    $lastError = ""
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 5
            $info = Convert-HttpResponseContentToJson -Response $response
            if ($info.iguanaRuntime.role -eq $ExpectedRole) {
                return
            }
            $lastError = "runtime role '$($info.iguanaRuntime.role)'"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 1
    }

    Fail "Timed out waiting for $Uri to report runtime role '$ExpectedRole'. Last error: $lastError"
}
function Assert-NoPublishedPorts {
    param(
        [string]$Docker,
        [string]$ContainerId,
        [string]$Label
    )

    $bindings = (& $Docker inspect --format "{{json .HostConfig.PortBindings}}" $ContainerId).Trim()
    if ($bindings -notin @("{}", "null", "")) {
        Fail "$Label unexpectedly publishes host ports: $bindings"
    }
}

function Get-HttpStatus {
    param([string]$Uri)

    try {
        $response = Invoke-WebRequest -Uri $Uri -UseBasicParsing -TimeoutSec 10
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response -and $_.Exception.Response.StatusCode) {
            return [int]$_.Exception.Response.StatusCode
        }
        throw
    }
}

if (-not $PSScriptRoot) {
    Fail "Unable to resolve script root."
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) {
    Fail "Docker is not installed or not available in PATH."
}
$docker = $dockerCommand.Source

& $docker compose version *> $null
if ($LASTEXITCODE -ne 0) {
    Fail "docker compose is unavailable."
}

$projectName = "iguana-01211-smoke-" + (Get-Date -Format "yyyyMMddHHmmss")
$smokeRootRelative = ".tmp/01-211-docker-smoke/$projectName"
$smokeRoot = Join-Path $repoRoot ($smokeRootRelative -replace "/", [System.IO.Path]::DirectorySeparatorChar)
$configDir = Join-Path $smokeRoot "config\shared"
$attachmentsDir = Join-Path $smokeRoot "attachments"
$logsDir = Join-Path $smokeRoot "logs"
$botDatabasesDir = Join-Path $smokeRoot "bot_databases"
$certsDir = Join-Path $smokeRoot "certs"

foreach ($directory in @($configDir, $attachmentsDir, $logsDir, $botDatabasesDir, $certsDir)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}

Copy-Item -Path (Join-Path $repoRoot "config\shared\*") -Destination $configDir -Recurse -Force

$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$edgeComposeFile = Join-Path $repoRoot "docker-compose.production-edge.yml"

$environmentValues = @{
    "IGUANA_PANEL_IMAGE" = "$projectName-panel:smoke"
    "IGUANA_POSTGRES_DB" = "iguana_smoke"
    "IGUANA_POSTGRES_USER" = "iguana_smoke"
    "IGUANA_POSTGRES_PASSWORD" = (New-Secret "pg")
    "IGUANA_RABBITMQ_USER" = "iguana_smoke"
    "IGUANA_RABBITMQ_PASSWORD" = (New-Secret "rabbit")
    "IGUANA_REDIS_PASSWORD" = (New-Secret "redis")
    "APP_STORAGE_OBJECT_ACCESS_KEY" = (New-Secret "minio")
    "APP_STORAGE_OBJECT_SECRET_KEY" = (New-Secret "minio-secret")
    "APP_STORAGE_OBJECT_BUCKET" = "iguana-smoke"
    "APP_INTERNAL_BOT_API_TOKEN" = (New-Secret "internal")
    "APP_INTERNAL_BOT_API_SIGNATURE_SECRET" = (New-Secret "signature")
    "APP_INTERNAL_BOT_API_REQUIRE_REQUEST_SIGNATURE" = "true"
    "APP_SECURITY_REMEMBER_ME_KEY" = (New-Secret "remember")
    "APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME" = "smoke_admin"
    "APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD" = ("S9!" + [Guid]::NewGuid().ToString("N"))
    "MONITORING_CREDENTIALS_MASTER_KEY" = (New-Secret "monitoring")
    "APP_POSTGRES_BIND_HOST" = "127.0.0.1"
    "APP_POSTGRES_PORT" = [string](Get-FreeTcpPort)
    "APP_RABBITMQ_AMQP_BIND_HOST" = "127.0.0.1"
    "APP_RABBITMQ_AMQP_PORT" = [string](Get-FreeTcpPort)
    "APP_RABBITMQ_HTTP_BIND_HOST" = "127.0.0.1"
    "APP_RABBITMQ_HTTP_PORT" = [string](Get-FreeTcpPort)
    "APP_REDIS_BIND_HOST" = "127.0.0.1"
    "APP_REDIS_PORT" = [string](Get-FreeTcpPort)
    "APP_STORAGE_OBJECT_BIND_HOST" = "127.0.0.1"
    "APP_STORAGE_OBJECT_PORT" = [string](Get-FreeTcpPort)
    "APP_STORAGE_OBJECT_CONSOLE_BIND_HOST" = "127.0.0.1"
    "APP_STORAGE_OBJECT_CONSOLE_PORT" = [string](Get-FreeTcpPort)
    "APP_PANEL_BIND_HOST" = "127.0.0.1"
    "APP_HTTP_PORT" = [string](Get-FreeTcpPort)
    "IGUANA_PUBLIC_HOST" = "localhost"
    "IGUANA_EDGE_HTTP_BIND_HOST" = "127.0.0.1"
    "IGUANA_EDGE_HTTP_PORT" = [string](Get-FreeTcpPort)
    "IGUANA_EDGE_HTTPS_BIND_HOST" = "127.0.0.1"
    "IGUANA_EDGE_HTTPS_PORT" = [string](Get-FreeTcpPort)
    "IGUANA_EDGE_TLS_ENABLED" = "false"
    "IGUANA_SHARED_CONFIG_DIR" = "$smokeRootRelative/config/shared"
    "IGUANA_ATTACHMENTS_DIR" = "$smokeRootRelative/attachments"
    "IGUANA_LOGS_DIR" = "$smokeRootRelative/logs"
    "IGUANA_BOT_DATABASES_DIR" = "$smokeRootRelative/bot_databases"
    "IGUANA_EDGE_CERTS_DIR" = "$smokeRootRelative/certs"
    "IGUANA_LEGACY_SQLITE_AUTO_IMPORT" = "false"
}

$previousEnvironment = @{}
foreach ($name in $environmentValues.Keys) {
    $previousEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    [Environment]::SetEnvironmentVariable($name, [string]$environmentValues[$name], "Process")
}

$baseArgs = @(
    "compose",
    "-p", $projectName,
    "--project-directory", $repoRoot,
    "-f", $composeFile,
    "-f", $edgeComposeFile
)

$success = $false
try {
    Write-Host "[SMOKE] Project: $projectName"
    Write-Host "[SMOKE] Validating compose model..."
    $configArgs = $baseArgs + @("config", "-q")
    & $docker @configArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "docker compose config failed."
    }

    Write-Host "[SMOKE] Building and starting 2 panel-web + 2 ops-worker..."
    $upArgs = $baseArgs + @(
        "up", "-d", "--build", "--remove-orphans",
        "--scale", "panel-web=2",
        "--scale", "ops-worker=2"
    )
    & $docker @upArgs
    if ($LASTEXITCODE -ne 0) {
        Fail "docker compose up failed."
    }

    $migratorIds = @(& $docker @($baseArgs + @("ps", "-a", "-q", "db-migrate")) | Where-Object { $_ })
    if ($migratorIds.Count -ne 1) {
        Fail "Expected exactly one db-migrate container, found $($migratorIds.Count)."
    }
    Wait-MigratorCompleted -Docker $docker -ContainerId $migratorIds[0] -Timeout $TimeoutSeconds
    Write-Host "[SMOKE] db-migrate completed with exit code 0."

    $webIds = @(& $docker @($baseArgs + @("ps", "-q", "panel-web")) | Where-Object { $_ })
    $workerIds = @(& $docker @($baseArgs + @("ps", "-q", "ops-worker")) | Where-Object { $_ })
    if ($webIds.Count -ne 2) {
        Fail "Expected 2 panel-web containers, found $($webIds.Count)."
    }
    if ($workerIds.Count -ne 2) {
        Fail "Expected 2 ops-worker containers, found $($workerIds.Count)."
    }

    foreach ($id in $webIds) {
        Wait-ContainerHealthy -Docker $docker -ContainerId $id -Timeout $TimeoutSeconds
        Assert-NoPublishedPorts -Docker $docker -ContainerId $id -Label "panel-web"
        $info = Invoke-ContainerRuntimeInfo -Docker $docker -ContainerId $id
        if ($info.iguanaRuntime.role -ne "web") {
            Fail "panel-web runtime role mismatch in $id."
        }
        $workloads = @($info.iguanaRuntime.enabledWorkloads)
        if ($workloads -contains "backend-ops-command-dispatcher") {
            Fail "panel-web unexpectedly contains backend ops dispatcher."
        }
        if ($workloads -notcontains "ui-event-stream-heartbeat") {
            Fail "panel-web is missing web SSE heartbeat workload."
        }
    }

    foreach ($id in $workerIds) {
        Wait-ContainerHealthy -Docker $docker -ContainerId $id -Timeout $TimeoutSeconds
        Assert-NoPublishedPorts -Docker $docker -ContainerId $id -Label "ops-worker"
        $info = Invoke-ContainerRuntimeInfo -Docker $docker -ContainerId $id
        if ($info.iguanaRuntime.role -ne "worker") {
            Fail "ops-worker runtime role mismatch in $id."
        }
        $workloads = @($info.iguanaRuntime.enabledWorkloads)
        if ($workloads -notcontains "backend-ops-command-dispatcher") {
            Fail "ops-worker is missing backend ops command dispatcher."
        }
        if ($workloads -contains "ui-event-stream-heartbeat") {
            Fail "ops-worker unexpectedly contains web SSE heartbeat workload."
        }
        if (@($info.iguanaRuntime.singletonWorkloads).Count -ne 0) {
            Fail "ops-worker still exposes SINGLETON workload(s): $(@($info.iguanaRuntime.singletonWorkloads) -join ', ')"
        }
    }
    Write-Host "[SMOKE] Runtime role/workload isolation verified."

    foreach ($id in ($webIds + $workerIds)) {
        $logs = & $docker logs $id 2>&1
        if (($logs -join "`n") -notmatch "Skipping Flyway migration for runtime role") {
            Fail "Web/worker container $id did not log Flyway migration skip."
        }
    }
    Write-Host "[SMOKE] Web/worker migration ownership skip verified."

$edgeProxyIds = @(& $docker @($baseArgs + @("ps", "-q", "nginx")) | Where-Object { $_ })
$directProxyIds = @(& $docker @($baseArgs + @("ps", "-q", "panel-direct")) | Where-Object { $_ })
if ($edgeProxyIds.Count -ne 1) {
    Fail "Expected exactly one edge nginx container, found $($edgeProxyIds.Count)."
}
if ($directProxyIds.Count -ne 1) {
    Fail "Expected exactly one panel-direct container, found $($directProxyIds.Count)."
}

Write-Host "[SMOKE] Waiting for ingress proxies health..."
Wait-ContainerHealthy -Docker $docker -ContainerId $edgeProxyIds[0] -Timeout 60
Wait-ContainerHealthy -Docker $docker -ContainerId $directProxyIds[0] -Timeout 60
Write-Host "[SMOKE] Ingress proxies are healthy."

$edgePort = [int]$environmentValues["IGUANA_EDGE_HTTP_PORT"]
$directPort = [int]$environmentValues["APP_HTTP_PORT"]

Wait-HttpRuntimeRole `
    -Uri "http://127.0.0.1:$edgePort/actuator/info" `
    -ExpectedRole "web" `
    -Timeout 30

Wait-HttpRuntimeRole `
    -Uri "http://127.0.0.1:$directPort/actuator/info" `
    -ExpectedRole "web" `
    -Timeout 30


    if ((Get-HttpStatus -Uri "http://127.0.0.1:$edgePort/internal/api/bot/smoke") -ne 404) {
        Fail "Public edge unexpectedly exposes /internal/api/bot/**."
    }
    if ((Get-HttpStatus -Uri "http://127.0.0.1:$edgePort/actuator/prometheus") -ne 403) {
        Fail "Public edge unexpectedly exposes /actuator/prometheus."
    }
    Write-Host "[SMOKE] Edge routing/security boundaries verified."

    Write-Host "[SMOKE] Stopping one panel-web replica and checking failover..."
    & $docker stop $webIds[0] | Out-Null
    $deadline = (Get-Date).AddSeconds(30)
    $edgeRecovered = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:$edgePort/actuator/info" -UseBasicParsing -TimeoutSec 5
            $info = Convert-HttpResponseContentToJson -Response $response
            if ($info.iguanaRuntime.role -eq "web") {
                $edgeRecovered = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    if (-not $edgeRecovered) {
        Fail "Edge did not continue serving through the remaining panel-web replica."
    }

    foreach ($workerId in $workerIds) {
        Wait-ContainerHealthy -Docker $docker -ContainerId $workerId -Timeout 30
    }

    & $docker start $webIds[0] | Out-Null
    Wait-ContainerHealthy -Docker $docker -ContainerId $webIds[0] -Timeout $TimeoutSeconds
    Write-Host "[SMOKE] panel-web failover verified; workers remained healthy."

    Write-Host "[SMOKE] Restarting one ops-worker while UI remains available..."
    & $docker restart $workerIds[0] | Out-Null
    $response = Invoke-WebRequest -Uri "http://127.0.0.1:$edgePort/actuator/info" -UseBasicParsing -TimeoutSec 10
    if ((Convert-HttpResponseContentToJson -Response $response).iguanaRuntime.role -ne "web") {
        Fail "UI routing failed while ops-worker restarted."
    }
    Wait-ContainerHealthy -Docker $docker -ContainerId $workerIds[0] -Timeout $TimeoutSeconds
    Write-Host "[SMOKE] Independent worker restart verified."

    $success = $true
    Write-Host ""
    Write-Host "01-211 Docker role/scale smoke is GREEN."
    Write-Host "Verified: db-migrate x1, panel-web x2, ops-worker x2, role isolation, no web/worker host ports, nginx->web only, independent restarts."
} catch {
    try {
        $logArgs = $baseArgs + @("logs", "--no-color")
        (& $docker @logArgs 2>&1) | Out-File -FilePath (Join-Path $smokeRoot "docker-compose.log") -Encoding utf8
        $psArgs = $baseArgs + @("ps", "-a")
        (& $docker @psArgs 2>&1) | Out-File -FilePath (Join-Path $smokeRoot "docker-compose-ps.txt") -Encoding utf8
    } catch {
        # Preserve the original smoke failure.
    }
    throw
} finally {
    if ($success -or -not $KeepStackOnFailure) {
        try {
            $downArgs = $baseArgs + @("down", "-v", "--remove-orphans")
            & $docker @downArgs | Out-Null
        } catch {
            Write-Warning "Failed to clean Docker smoke project $projectName."
        }
    } else {
        Write-Warning "Keeping failed smoke stack: $projectName"
    }

    foreach ($name in $environmentValues.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previousEnvironment[$name], "Process")
    }

    if ($success -and -not $KeepArtifacts) {
        Remove-Item -LiteralPath $smokeRoot -Recurse -Force -ErrorAction SilentlyContinue
    } else {
        Write-Host "[SMOKE] Artifacts: $smokeRoot"
    }
}
