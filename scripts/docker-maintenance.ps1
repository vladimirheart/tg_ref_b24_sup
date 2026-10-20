param(
    [switch]$Clean,
    [switch]$IfDue,
    [string]$ProjectName = "",
    [ValidateRange(1, 87600)]
    [int]$RetentionHours = 168,
    [ValidateRange(1, 87600)]
    [int]$IntervalHours = 168,
    [string]$StateDirectory = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if ($IfDue -and -not $Clean) { throw "-IfDue requires -Clean." }
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$dockerCommand = Get-Command docker -ErrorAction Stop

function Invoke-MaintenanceDocker {
    param([string[]]$DockerArguments)

    $previousPreference = $ErrorActionPreference
    try {
        # Native stderr (for example a Compose warning) is not an exit code.
        $ErrorActionPreference = "Continue"
        $lines = @(& $dockerCommand @DockerArguments)
        $commandExitCode = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    if ($commandExitCode -ne 0) {
        throw "Docker maintenance command failed (exit code $commandExitCode)."
    }
    return $lines
}

$daemonLines = @(Invoke-MaintenanceDocker -DockerArguments @("info", "--format", "{{.ID}}"))
if ($daemonLines.Count -ne 1 -or [string]::IsNullOrWhiteSpace([string]$daemonLines[0])) {
    throw "Unable to resolve Docker daemon identity."
}
$daemonId = ([string]$daemonLines[0]).Trim()

if ([string]::IsNullOrWhiteSpace($ProjectName)) {
    $composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
    $environmentLines = @(Invoke-MaintenanceDocker -DockerArguments @(
        "compose", "--project-directory", $repoRoot, "-f", $composeFile,
        "config", "--environment"
    ))
    $nameLines = @($environmentLines | Where-Object { "$_" -cmatch "^COMPOSE_PROJECT_NAME=" })
    if ($nameLines.Count -ne 1) { throw "Unable to resolve Compose project name." }
    $ProjectName = ([string]$nameLines[0]).Substring("COMPOSE_PROJECT_NAME=".Length)
}
if ($ProjectName -cnotmatch "^[a-z0-9][a-z0-9_-]*$") {
    throw "Invalid Compose project name."
}

$projectFilter = "label=io.iguana.project=$ProjectName"
function Show-MaintenanceReport {
    Write-Host "[INFO] Docker storage (whole daemon):"
    Invoke-MaintenanceDocker -DockerArguments @("system", "df") | ForEach-Object { Write-Host $_ }
    Write-Host "[INFO] Images labelled for project '$ProjectName':"
    Invoke-MaintenanceDocker -DockerArguments @("image", "ls", "--filter", $projectFilter) | ForEach-Object { Write-Host $_ }
}

if (-not $Clean) {
    Show-MaintenanceReport
    Write-Host "[INFO] Report only. Use -Clean to remove dangling project images older than $RetentionHours hours."
    return
}

if ([string]::IsNullOrWhiteSpace($StateDirectory)) {
    $StateDirectory = Join-Path $repoRoot "run/docker-maintenance"
}
$StateDirectory = [System.IO.Path]::GetFullPath($StateDirectory)
$daemonKey = $daemonId -creplace "[^A-Za-z0-9_.-]", "-"
$stampFile = Join-Path $StateDirectory "$daemonKey-$ProjectName.last-clean"
$lockDirectory = "$stampFile.lock"
New-Item -ItemType Directory -Path $StateDirectory -Force | Out-Null
try {
    New-Item -ItemType Directory -Path $lockDirectory -ErrorAction Stop | Out-Null
} catch {
    if (Test-Path -LiteralPath $lockDirectory -PathType Container) {
        Write-Warning "Docker maintenance is already running (or a stale lock exists): $lockDirectory"
        return
    }
    throw
}

try {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    if ($IfDue -and (Test-Path -LiteralPath $stampFile -PathType Leaf)) {
        $lastCleanText = [System.IO.File]::ReadAllText($stampFile).Trim()
        $lastClean = 0L
        if ($lastCleanText -notmatch "^[0-9]{1,11}$" -or
            -not [long]::TryParse($lastCleanText, [ref]$lastClean) -or $lastClean -gt ($now + 60)) {
            throw "Invalid maintenance timestamp: $stampFile"
        }
        if (($now - $lastClean) -lt ([long]$IntervalHours * 3600)) {
            Write-Host "[INFO] Project image cleanup is not due (interval: $IntervalHours hours)."
            return
        }
    }

    Show-MaintenanceReport
    Write-Host "[INFO] Removing dangling project images created more than $RetentionHours hours ago; tagged images are preserved."
    # Intentionally no --all: named releases and rollback tags must survive.
    Invoke-MaintenanceDocker -DockerArguments @(
        "image", "prune", "--force", "--filter", $projectFilter,
        "--filter", ("until={0}h" -f $RetentionHours)
    ) | ForEach-Object { Write-Host $_ }
    [System.IO.File]::WriteAllText($stampFile, ($now.ToString() + [Environment]::NewLine), [System.Text.Encoding]::ASCII)
    Show-MaintenanceReport
} finally {
    Remove-Item -LiteralPath $lockDirectory -ErrorAction Stop
}
