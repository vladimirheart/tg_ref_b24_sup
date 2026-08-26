param(
    [int]$TimeoutSeconds = 120
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Read-DotEnvFile {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) { continue }
        $result[$trimmed.Substring(0, $idx).Trim()] = $trimmed.Substring($idx + 1).Trim()
    }
    return $result
}

function Get-SettingValue {
    param([hashtable]$DotEnv, [string]$Name, [string]$DefaultValue)
    $envValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) { return $envValue.Trim() }
    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($DotEnv[$Name])) {
        return [string]$DotEnv[$Name]
    }
    return $DefaultValue
}

function Convert-HttpResponseContentToJson {
    param($Response)
    $content = $Response.Content
    if ($content -is [byte[]]) {
        $text = [System.Text.Encoding]::UTF8.GetString([byte[]]$content)
    } else {
        $text = [string]$content
    }
    return ConvertFrom-Json -InputObject $text
}

function Wait-Http200 {
    param([string]$Url, [int]$Timeout)
    $deadline = (Get-Date).AddSeconds($Timeout)
    $lastError = $null
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) { return $response }
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for HTTP 200 from $Url. Last error: $lastError"
}

function Assert-PrometheusJobHealthy {
    param($Targets, [string]$Job, [int]$Minimum = 1)
    $matching = @($Targets | Where-Object { $_.labels.job -eq $Job })
    if ($matching.Count -lt $Minimum) {
        throw "Prometheus job '$Job' has $($matching.Count) active targets; expected at least $Minimum."
    }
    $down = @($matching | Where-Object { $_.health -ne "up" })
    if ($down.Count -gt 0) {
        $details = ($down | ForEach-Object { "$($_.scrapeUrl)=$($_.health)" }) -join ", "
        throw "Prometheus job '$Job' has unhealthy targets: $details"
    }
}

$repoRoot = Get-RepoRoot
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$observabilityCompose = Join-Path $repoRoot "docker-compose.production-observability.yml"
$dotEnvPath = Join-Path $repoRoot ".env"
$dotEnv = Read-DotEnvFile -Path $dotEnvPath

$docker = Get-Command docker -ErrorAction SilentlyContinue
if (-not $docker) { throw "Docker is not available in PATH." }

$composeArgs = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath) { $composeArgs += @("--env-file", $dotEnvPath) }
$composeArgs += @("-f", $baseCompose, "-f", $observabilityCompose, "config", "-q")
& $docker.Source @composeArgs
if ($LASTEXITCODE -ne 0) { throw "docker compose observability config validation failed." }

$promPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_PROMETHEUS_PORT" -DefaultValue "9090"
$alertPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_ALERTMANAGER_PORT" -DefaultValue "9093"
$lokiPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_LOKI_PORT" -DefaultValue "3100"
$alloyPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_ALLOY_PORT" -DefaultValue "12345"
$grafanaPort = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_GRAFANA_PORT" -DefaultValue "3000"

$promBase = "http://127.0.0.1:$promPort"
$alertBase = "http://127.0.0.1:$alertPort"
$lokiBase = "http://127.0.0.1:$lokiPort"
$alloyBase = "http://127.0.0.1:$alloyPort"
$grafanaBase = "http://127.0.0.1:$grafanaPort"

Write-Host "[SMOKE] Waiting for observability endpoints..."
Wait-Http200 -Url "$promBase/-/ready" -Timeout $TimeoutSeconds | Out-Null
Wait-Http200 -Url "$alertBase/-/ready" -Timeout $TimeoutSeconds | Out-Null
Wait-Http200 -Url "$lokiBase/ready" -Timeout $TimeoutSeconds | Out-Null
Wait-Http200 -Url "$alloyBase/-/ready" -Timeout $TimeoutSeconds | Out-Null
$grafanaHealthResponse = Wait-Http200 -Url "$grafanaBase/api/health" -Timeout $TimeoutSeconds
$grafanaHealth = Convert-HttpResponseContentToJson -Response $grafanaHealthResponse
if ([string]$grafanaHealth.database -ne "ok") {
    throw "Grafana database health is not ok: $($grafanaHealthResponse.Content)"
}

$targetsResponse = Wait-Http200 -Url "$promBase/api/v1/targets?state=active" -Timeout $TimeoutSeconds
$targetsJson = Convert-HttpResponseContentToJson -Response $targetsResponse
if ($targetsJson.status -ne "success") { throw "Prometheus targets API did not return success." }
$targets = @($targetsJson.data.activeTargets)

Assert-PrometheusJobHealthy -Targets $targets -Job "iguana-panel-web" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "iguana-ops-worker" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "postgres" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "redis" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "rabbitmq" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "minio" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "alertmanager" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "loki" -Minimum 1
Assert-PrometheusJobHealthy -Targets $targets -Job "alloy" -Minimum 1

$rulesResponse = Wait-Http200 -Url "$promBase/api/v1/rules" -Timeout $TimeoutSeconds
$rulesJson = Convert-HttpResponseContentToJson -Response $rulesResponse
if ($rulesJson.status -ne "success") { throw "Prometheus rules API did not return success." }
$ruleNames = @($rulesJson.data.groups | ForEach-Object { $_.rules } | ForEach-Object { $_.name })
foreach ($requiredRule in @("IguanaPanelWebReplicaDown", "IguanaOpsWorkerReplicaDown", "IguanaDlqBacklog")) {
    if ($ruleNames -notcontains $requiredRule) {
        throw "Required Prometheus rule is missing: $requiredRule"
    }
}

Write-Host "01-194 observability smoke is GREEN."
Write-Host "Verified: Prometheus/Grafana/Alertmanager/Loki/Alloy readiness, role-aware targets, infra targets and alert rules."
