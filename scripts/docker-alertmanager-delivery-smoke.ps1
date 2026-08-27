param(
    [ValidateRange(30, 600)]
    [int]$TimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$observabilityCompose = Join-Path $repoRoot "docker-compose.production-observability.yml"
$dotEnvPath = Join-Path $repoRoot ".env"
$rulePath = Join-Path $repoRoot "observability\prometheus\rules\zz-iguana-alertmanager-delivery-smoke.yml"

function Read-DotEnv([string]$Path) {
    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $values }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -gt 0) {
            $values[$trimmed.Substring(0, $idx).Trim()] = $trimmed.Substring($idx + 1).Trim()
        }
    }
    return $values
}

function Write-LfNoBom([string]$Path, [string]$Content) {
    $normalized = ($Content -replace "`r`n", "`n") -replace "`r", "`n"
    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($Path, $normalized, $enc)
}

function Wait-Until([string]$Label, [scriptblock]$Probe) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            $value = & $Probe
            if ($null -ne $value -and $value -ne $false -and [string]$value -ne "") {
                return $value
            }
        } catch {
            # Retry transient API/container startup failures until timeout.
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    throw "Timeout waiting for $Label after $TimeoutSeconds seconds."
}

$docker = (Get-Command docker -ErrorAction Stop).Source
$envValues = Read-DotEnv $dotEnvPath
$prometheusPort = if ($envValues.ContainsKey("IGUANA_PROMETHEUS_PORT")) { $envValues["IGUANA_PROMETHEUS_PORT"] } else { "9090" }
$alertmanagerPort = if ($envValues.ContainsKey("IGUANA_ALERTMANAGER_PORT")) { $envValues["IGUANA_ALERTMANAGER_PORT"] } else { "9093" }
$dbUser = if ($envValues.ContainsKey("IGUANA_POSTGRES_USER")) { $envValues["IGUANA_POSTGRES_USER"] } else { "iguana" }
$dbName = if ($envValues.ContainsKey("IGUANA_POSTGRES_DB")) { $envValues["IGUANA_POSTGRES_DB"] } else { "iguana" }

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $baseCompose, "-f", $observabilityCompose)

function Invoke-Compose([string[]]$Arguments) {
    & $docker @composePrefix @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

function Query-Postgres([string]$Sql) {
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $docker @composePrefix exec -T postgres psql -U $dbUser -d $dbName -Atc $Sql 2>&1
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
    if ($code -ne 0) {
        throw "PostgreSQL query failed: $($output -join ' ')"
    }
    return (($output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Reload-Prometheus {
    Invoke-WebRequest `
        -Method Post `
        -Uri "http://127.0.0.1:$prometheusPort/-/reload" `
        -UseBasicParsing |
        Out-Null
}

$running = & $docker @composePrefix ps --status running --services
if ($LASTEXITCODE -ne 0) { throw "Unable to inspect production observability contour." }
$runningServices = @($running | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })

foreach ($required in @("postgres", "panel-web", "ops-worker", "prometheus", "alertmanager")) {
    if ($runningServices -notcontains $required) {
        throw "Required service is not running: $required. Start the production contour with observability first."
    }
}

$smokeId = [Guid]::NewGuid().ToString("N")
$alertName = "IguanaAlertmanagerDeliverySmoke"
$originalRuleExists = Test-Path -LiteralPath $rulePath

if ($originalRuleExists) {
    throw "Smoke rule path already exists; remove stale test file first: $rulePath"
}

function Build-Rule([bool]$Firing) {
    $expr = if ($Firing) { "vector(1)" } else { "vector(0) == 1" }
    return @"
groups:
  - name: iguana-alertmanager-delivery-smoke
    interval: 2s
    rules:
      - alert: $alertName
        expr: $expr
        labels:
          severity: high
          service: alertmanager-e2e
          iguana_smoke: "true"
          smoke_id: "$smokeId"
        annotations:
          summary: Iguana Alertmanager delivery E2E smoke
          description: Synthetic alert for task 01-213.
"@
}

try {
    Write-Host "[SMOKE] Creating synthetic firing Prometheus rule: smoke_id=$smokeId"
    Write-LfNoBom $rulePath (Build-Rule $true)
    Reload-Prometheus

    [void](Wait-Until "Prometheus firing smoke alert" {
        $response = Invoke-RestMethod -Uri "http://127.0.0.1:$prometheusPort/api/v1/alerts"
        $match = @($response.data.alerts | Where-Object {
            $_.labels.smoke_id -eq $smokeId -and $_.state -eq "firing"
        })
        if ($match.Count -gt 0) { return "firing" }
        return $null
    })
    Write-Host "[GREEN] Prometheus firing alert observed."

    $fingerprint = Wait-Until "Alertmanager smoke alert fingerprint" {
        $alerts = @(Invoke-RestMethod -Uri "http://127.0.0.1:$alertmanagerPort/api/v2/alerts")
        $match = @($alerts | Where-Object { $_.labels.smoke_id -eq $smokeId })
        if ($match.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace([string]$match[0].fingerprint)) {
            return [string]$match[0].fingerprint
        }
        return $null
    }
    Write-Host "[GREEN] Alertmanager accepted smoke alert. fingerprint=$fingerprint"

    $incidentId = Wait-Until "Iguana firing signal incident" {
        $sql = "SELECT id FROM incidents WHERE signal_type='alertmanager' AND signal_key='$fingerprint' AND status IN ('open','investigating','acknowledged') ORDER BY id DESC LIMIT 1;"
        $value = Query-Postgres $sql
        if ($value -match '^\d+$') { return $value }
        return $null
    }
    Write-Host "[GREEN] Iguana signal incident created. id=$incidentId"

    [void](Wait-Until "delivered firing incident route outbox" {
        $sql = "SELECT event_id FROM incident_route_delivery_outbox WHERE incident_id=$incidentId AND event_type='incident_signal_updated' AND status='delivered' ORDER BY created_at DESC, event_id DESC LIMIT 1;"
        $value = Query-Postgres $sql
        if ($value -match '^\d+$') { return $value }
        return $null
    })
    Write-Host "[GREEN] Firing delivery reached the durable incident outbox and was delivered."

    Write-Host "[SMOKE] Flipping synthetic rule to resolved."
    Write-LfNoBom $rulePath (Build-Rule $false)
    Reload-Prometheus

    [void](Wait-Until "resolved Iguana signal incident" {
        $sql = "SELECT status FROM incidents WHERE id=$incidentId;"
        $value = Query-Postgres $sql
        if ($value -eq "resolved") { return $value }
        return $null
    })
    Write-Host "[GREEN] Iguana signal incident resolved."

    [void](Wait-Until "delivered resolved incident route outbox" {
        $sql = "SELECT event_id FROM incident_route_delivery_outbox WHERE incident_id=$incidentId AND event_type='incident_signal_resolved' AND status='delivered' ORDER BY created_at DESC, event_id DESC LIMIT 1;"
        $value = Query-Postgres $sql
        if ($value -match '^\d+$') { return $value }
        return $null
    })
    Write-Host "[GREEN] Resolved delivery reached the durable incident outbox and was delivered."

    Write-Host "[RESULT] ALERTMANAGER -> IGUANA FIRING/RESOLVED E2E GREEN"
    Write-Host "[RESULT] smoke_id=$smokeId incident_id=$incidentId fingerprint=$fingerprint"
} finally {
    if (Test-Path -LiteralPath $rulePath -PathType Leaf) {
        Remove-Item -LiteralPath $rulePath -Force
        try {
            Reload-Prometheus
            Write-Host "[CLEANUP] Smoke rule removed and Prometheus reloaded."
        } catch {
            Write-Warning "Smoke rule was removed but Prometheus reload failed during cleanup: $($_.Exception.Message)"
        }
    }
}
