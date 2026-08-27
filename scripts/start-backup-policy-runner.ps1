param(
    [switch]$DetachFromParent,
    [ValidateRange(1, 60)]
    [int]$IdleSeconds = 5
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$runner = Join-Path $PSScriptRoot "run-backup-policy.ps1"
$library = Join-Path $PSScriptRoot "lib\backup-config.ps1"
$runDir = Join-Path $repoRoot "run"
$logDir = Join-Path $repoRoot "logs"
$pidFile = Join-Path $runDir "backup-policy-runner.pid"
$outLog = Join-Path $logDir "backup-policy-runner.out.log"
$errLog = Join-Path $logDir "backup-policy-runner.err.log"

foreach ($required in @($runner, $library)) {
    if (-not (Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Backup runner dependency is missing: $required"
    }
}

. $library
$shared = Resolve-IguanaSharedConfigDirectory -RepoRoot $repoRoot
$stopFile = Join-Path $shared "backup-policy-runner.stop"

New-Item -ItemType Directory -Force -Path $runDir | Out-Null
New-Item -ItemType Directory -Force -Path $logDir | Out-Null
Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue

function Read-RunnerPid {
    if (-not (Test-Path -LiteralPath $pidFile -PathType Leaf)) { return 0 }
    foreach ($line in Get-Content -LiteralPath $pidFile -Encoding UTF8) {
        if ($line -match '^pid=(\d+)$') {
            return [int]$Matches[1]
        }
    }
    return 0
}

function Get-ManagedRunnerProcess([int]$ProcessId) {
    if ($ProcessId -le 0) { return $null }
    try {
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
    } catch {
        return $null
    }

    if ($null -eq $process) { return $null }
    $commandLine = [string]$process.CommandLine
    if ($commandLine -notlike "*run-backup-policy.ps1*" -or $commandLine -notlike "*-Daemon*") {
        return $null
    }
    return $process
}

$existingPid = Read-RunnerPid
$existing = Get-ManagedRunnerProcess $existingPid
if ($null -ne $existing) {
    Write-Host "[INFO] Backup policy runner daemon already active. pid=$existingPid"
    exit 0
}
Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue

$parentPid = 0
if (-not $DetachFromParent) {
    try {
        $self = Get-CimInstance Win32_Process -Filter "ProcessId = $PID" -ErrorAction Stop
        $parentPid = [int]$self.ParentProcessId
    } catch {
        $parentPid = 0
    }
}

$backupPolicyVariables = @(
    "IGUANA_BACKUP_DESTINATION_DIR",
    "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN",
    "IGUANA_BACKUP_RETENTION_DAYS",
    "IGUANA_MINIO_BACKUP_RETENTION_DAYS",
    "IGUANA_BACKUP_ARCHIVE_FORMAT",
    "IGUANA_BACKUP_MANUAL_MODE",
    "IGUANA_BACKUP_CUSTOM_COMPONENTS",
    "IGUANA_BACKUP_RESTORE_COMPONENTS",
    "IGUANA_BACKUP_CRITICAL_ENABLED",
    "IGUANA_BACKUP_CRITICAL_FREQUENCY",
    "IGUANA_BACKUP_CRITICAL_TIME",
    "IGUANA_BACKUP_CRITICAL_WEEKDAY",
    "IGUANA_BACKUP_FULL_ENABLED",
    "IGUANA_BACKUP_FULL_FREQUENCY",
    "IGUANA_BACKUP_FULL_TIME",
    "IGUANA_BACKUP_FULL_WEEKDAY"
)

$savedPolicyEnvironment = @{}
foreach ($name in $backupPolicyVariables) {
    $savedPolicyEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
    [Environment]::SetEnvironmentVariable($name, $null, "Process")
}

try {
    $powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
    $arguments = "-NoLogo -NoProfile -ExecutionPolicy Bypass -File `"$runner`" -Daemon -IdleSeconds $IdleSeconds"
    if ($parentPid -gt 0) {
        $arguments += " -ParentPid $parentPid"
    }

    $process = Start-Process `
        -FilePath $powershell `
        -ArgumentList $arguments `
        -WindowStyle Hidden `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -PassThru

    Start-Sleep -Milliseconds 300
    if ($process.HasExited) {
        throw "Backup policy runner exited immediately with code $($process.ExitCode). Check $errLog"
    }

    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText(
        $pidFile,
        "pid=$($process.Id)`nstarted_at=$([DateTimeOffset]::UtcNow.ToString('o'))`n",
        $enc
    )

    Write-Host "[GREEN] Hidden backup policy runner started. pid=$($process.Id)"
    Write-Host "[INFO] Logs: $outLog / $errLog"
} finally {
    foreach ($name in $backupPolicyVariables) {
        [Environment]::SetEnvironmentVariable($name, $savedPolicyEnvironment[$name], "Process")
    }
}
