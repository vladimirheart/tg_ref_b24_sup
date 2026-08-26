param(
    [switch]$SkipDockerValidation,
    [switch]$SkipMavenValidation
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Assert-PowerShellParses {
    param([string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required PowerShell file is missing: $Path"
    }

    $tokens = $null
    $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile($Path, [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count -gt 0) {
        $message = ($errors | ForEach-Object { $_.Message }) -join "; "
        throw "PowerShell parser failed for ${Path}: $message"
    }
}

function Invoke-NativeQuietProbe {
    param(
        [string]$CommandPath,
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        # Windows PowerShell can surface stderr from native programs as
        # NativeCommandError when ErrorActionPreference=Stop. A probe must not
        # terminate validation just because a broken WSL shim writes to stderr.
        $ErrorActionPreference = "Continue"
        $null = & $CommandPath @Arguments 2>&1
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
}

function Assert-Contains {
    param(
        [string]$Path,
        [string]$Needle,
        [string]$Label
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file is missing: $Path"
    }
    $content = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    if (-not $content.Contains($Needle)) {
        throw "$Label is missing from ${Path}: $Needle"
    }
}

$repoRoot = [System.IO.Path]::GetFullPath((Get-Location).Path)
$requiredFiles = @(
    "docker-compose.production-contour.yml",
    "docker-compose.production-observability.yml",
    "scripts/docker-production-up.ps1",
    "scripts/docker-production-down.ps1",
    "scripts/docker-production-observability-smoke.ps1",
    "observability/prometheus/prometheus.yml",
    "observability/prometheus/rules/iguana-alerts.yml",
    "observability/alertmanager/alertmanager.yml",
    "observability/loki/loki.yml",
    "observability/alloy/config.alloy",
    "observability/grafana/provisioning/datasources/datasources.yml",
    "observability/grafana/provisioning/dashboards/dashboards.yml",
    "observability/grafana/dashboards/iguana-runtime-overview.json",
    "observability/grafana/dashboards/iguana-infrastructure.json",
    "observability/grafana/dashboards/iguana-logs.json",
    "ai-context/tasks/task-details/01-194.md",
    "ai-context/tasks/task-details/01-211.md",
    "ai-context/tasks/task-details/01-212.md",
    "spring-panel/src/test/java/com/example/panel/runtime/ProductionObservabilityContourSourceContractTest.java"
)

foreach ($relative in $requiredFiles) {
    $path = Join-Path $repoRoot $relative
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Run this script from the tg_ref_b24_sup repository root, or re-check the partial apply. Missing: $relative"
    }
}

$git = Get-Command git -ErrorAction SilentlyContinue
if (-not $git) {
    throw "git is not available in PATH."
}

Write-Host "[PRECHECK] Current HEAD"
& $git.Source rev-parse HEAD
if ($LASTEXITCODE -ne 0) {
    throw "git rev-parse HEAD failed."
}

Write-Host "[PRECHECK] Current git status --short"
& $git.Source status --short
if ($LASTEXITCODE -ne 0) {
    throw "git status --short failed."
}

Write-Host "[INFO] Resume validator is read-only for repository files."
Write-Host "[INFO] It never runs reset/checkout/clean/add/commit/push."

Write-Host "[VERIFY] Applied observability/task markers"
Assert-Contains -Path (Join-Path $repoRoot "scripts/docker-production-up.ps1") -Needle "Observability" -Label "Observability switch"
Assert-Contains -Path (Join-Path $repoRoot "scripts/docker-production-down.ps1") -Needle "Observability" -Label "Observability switch"
Assert-Contains -Path (Join-Path $repoRoot "ai-context/tasks/task-list.md") -Needle "[01-212]" -Label "01-212 task-list entry"
Assert-Contains -Path (Join-Path $repoRoot "ai-context/tasks/task-list.md") -Needle "[01-194]" -Label "01-194 task-list entry"

Write-Host "[VERIFY] PowerShell parser"
Assert-PowerShellParses -Path (Join-Path $repoRoot "scripts/docker-production-up.ps1")
Assert-PowerShellParses -Path (Join-Path $repoRoot "scripts/docker-production-down.ps1")
Assert-PowerShellParses -Path (Join-Path $repoRoot "scripts/docker-production-observability-smoke.ps1")
Assert-PowerShellParses -Path $PSCommandPath

Write-Host "[VERIFY] Grafana dashboard JSON"
foreach ($dashboard in Get-ChildItem -LiteralPath (Join-Path $repoRoot "observability/grafana/dashboards") -Filter "*.json" -File) {
    $raw = [System.IO.File]::ReadAllText($dashboard.FullName, [System.Text.Encoding]::UTF8)
    $null = ConvertFrom-Json -InputObject $raw
}

$bash = Get-Command bash -ErrorAction SilentlyContinue
$bashRunnable = $false
if ($bash) {
    $bashRunnable = Invoke-NativeQuietProbe -CommandPath $bash.Source -Arguments @("--version")
}
if ($bashRunnable) {
    Write-Host "[VERIFY] Bash syntax"
    & $bash.Source -n (Join-Path $repoRoot "scripts/docker-production-up.sh")
    if ($LASTEXITCODE -ne 0) {
        throw "bash -n failed for docker-production-up.sh"
    }
    & $bash.Source -n (Join-Path $repoRoot "scripts/docker-production-down.sh")
    if ($LASTEXITCODE -ne 0) {
        throw "bash -n failed for docker-production-down.sh"
    }
} else {
    Write-Warning "Bash is absent or the Windows/WSL bash shim is not runnable; .sh syntax verification skipped."
}

if (-not $SkipDockerValidation) {
    $docker = Get-Command docker -ErrorAction SilentlyContinue
    if ($docker) {
        Write-Host "[VERIFY] Docker Compose merged observability config"
        $composeArgs = @("compose", "--project-directory", $repoRoot)
        $dotEnvPath = Join-Path $repoRoot ".env"
        if (Test-Path -LiteralPath $dotEnvPath) {
            $composeArgs += @("--env-file", $dotEnvPath)
        }
        $composeArgs += @(
            "-f", (Join-Path $repoRoot "docker-compose.production-contour.yml"),
            "-f", (Join-Path $repoRoot "docker-compose.production-observability.yml"),
            "config", "-q"
        )
        & $docker.Source @composeArgs
        if ($LASTEXITCODE -ne 0) {
            throw "docker compose observability config validation failed."
        }
    } else {
        Write-Warning "Docker is not available; merged Compose validation skipped."
    }
}

if (-not $SkipMavenValidation) {
    Write-Host "[VERIFY] spring-panel test-compile + observability source contract"
    Push-Location (Join-Path $repoRoot "spring-panel")
    try {
        $mvn = Get-Command mvn -ErrorAction SilentlyContinue
        if ($mvn) {
            & $mvn.Source -q -DskipTests test-compile
            if ($LASTEXITCODE -ne 0) {
                throw "spring-panel test-compile failed."
            }
            & $mvn.Source -q '-Dtest=ProductionObservabilityContourSourceContractTest' test
            if ($LASTEXITCODE -ne 0) {
                throw "ProductionObservabilityContourSourceContractTest failed."
            }
        } elseif (Test-Path -LiteralPath ".\mvnw.cmd") {
            & .\mvnw.cmd -q -DskipTests test-compile
            if ($LASTEXITCODE -ne 0) {
                throw "spring-panel test-compile failed."
            }
            & .\mvnw.cmd -q '-Dtest=ProductionObservabilityContourSourceContractTest' test
            if ($LASTEXITCODE -ne 0) {
                throw "ProductionObservabilityContourSourceContractTest failed."
            }
        } else {
            Write-Warning "Maven is unavailable; Java source-contract verification skipped."
        }
    } finally {
        Pop-Location
    }
}

Write-Host "[VERIFY] git diff --check"
& $git.Source diff --check
if ($LASTEXITCODE -ne 0) {
    throw "git diff --check failed."
}

Write-Host "[RESULT] 01-194 Phase 2 static implementation is GREEN."
Write-Host "[RESULT] 01-194 remains YELLOW until real Docker observability smoke is GREEN."
Write-Host "[RESULT] 01-212 is registered as ORANGE; 01-198 remains unchanged in scope."
Write-Host "[RESULT] No stage/commit/push was performed."
Write-Host "[POSTCHECK] HEAD"
& $git.Source rev-parse HEAD
Write-Host "[POSTCHECK] git status --short"
& $git.Source status --short
