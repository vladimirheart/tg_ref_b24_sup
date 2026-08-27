param(
    [string]$TaskName = "Iguana Backup Policy Runner"
)

$ErrorActionPreference = "Stop"

Write-Host "[INFO] Scheduled Task runner is deprecated."
Write-Host "[INFO] Backup runner now starts hidden with the panel lifecycle."

$cmd = Get-Command Get-ScheduledTask -ErrorAction SilentlyContinue
if (-not $cmd) {
    Write-Host "[INFO] Task Scheduler cmdlets are unavailable; nothing to migrate."
    exit 0
}

$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($null -eq $task) {
    Write-Host "[INFO] Legacy Scheduled Task is not installed."
    exit 0
}

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false
Write-Host "[GREEN] Removed legacy Scheduled Task: $TaskName"
Write-Host "[INFO] No periodic OS scheduler is required anymore."
