param(
    [ValidateRange(0, 3600)]
    [int]$WaitSeconds = 5,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$library = Join-Path $PSScriptRoot "lib\backup-config.ps1"
$pidFile = Join-Path $repoRoot "run\backup-policy-runner.pid"

. $library
$shared = Resolve-IguanaSharedConfigDirectory -RepoRoot $repoRoot
$stopFile = Join-Path $shared "backup-policy-runner.stop"
$statusFile = Join-Path $shared "backup-policy-runner.status"

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

function Write-OfflineStatus([string]$Message) {
    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText(
        $statusFile,
        "status=offline`nlast_seen_at=$([DateTimeOffset]::UtcNow.ToString('o'))`nplatform=windows`nmode=daemon`nschedule_ready=false`nmessage=$Message`n",
        $enc
    )
}

$runnerPid = Read-RunnerPid
$process = Get-ManagedRunnerProcess $runnerPid

if ($null -eq $process) {
    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
    Write-OfflineStatus "Panel lifecycle runner is not active."
    Write-Host "[INFO] Backup policy runner is not active."
    exit 0
}

$enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
[System.IO.File]::WriteAllText($stopFile, "stop`n", $enc)

$deadline = (Get-Date).AddSeconds($WaitSeconds)
while ((Get-Date) -lt $deadline) {
    if ($null -eq (Get-Process -Id $runnerPid -ErrorAction SilentlyContinue)) {
        break
    }
    Start-Sleep -Milliseconds 250
}

$stillRunning = $null -ne (Get-ManagedRunnerProcess $runnerPid)
if ($stillRunning -and $Force) {
    Stop-Process -Id $runnerPid -Force -ErrorAction SilentlyContinue
    Start-Sleep -Milliseconds 200
    $stillRunning = $null -ne (Get-ManagedRunnerProcess $runnerPid)
}

if ($stillRunning) {
    Write-Warning "Backup policy runner is finishing an active backup and will stop after the current cycle. pid=$runnerPid"
    exit 0
}

Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
Remove-Item -LiteralPath $stopFile -Force -ErrorAction SilentlyContinue
Write-OfflineStatus "Panel lifecycle runner stopped."
Write-Host "[GREEN] Backup policy runner stopped."
