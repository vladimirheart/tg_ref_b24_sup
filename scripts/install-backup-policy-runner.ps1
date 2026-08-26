param(
    [string]$TaskName = "Iguana Backup Policy Runner",
    [int]$EveryMinutes = 5
)

$ErrorActionPreference = "Stop"
if ($EveryMinutes -lt 1 -or $EveryMinutes -gt 60) { throw "EveryMinutes must be 1..60." }
if (-not $PSScriptRoot) { throw "Unable to resolve script root." }

$runner = Join-Path $PSScriptRoot "run-backup-policy.ps1"
if (-not (Test-Path -LiteralPath $runner)) { throw "Runner not found: $runner" }

$powershell = (Get-Command powershell.exe -ErrorAction Stop).Source
$action = New-ScheduledTaskAction `
    -Execute $powershell `
    -Argument ('-NoProfile -ExecutionPolicy Bypass -File "' + $runner + '"')
$trigger = New-ScheduledTaskTrigger `
    -Once `
    -At ((Get-Date).AddMinutes(1)) `
    -RepetitionInterval (New-TimeSpan -Minutes $EveryMinutes) `
    -RepetitionDuration (New-TimeSpan -Days 3650)

Register-ScheduledTask `
    -TaskName $TaskName `
    -Action $action `
    -Trigger $trigger `
    -Description "Reads Iguana admin backup policy every few minutes and runs due critical/full plans." `
    -RunLevel Highest `
    -Force | Out-Null

Write-Host "[GREEN] Windows Task Scheduler runner installed: $TaskName"
Write-Host "[INFO] Policy remains in Settings -> Backup & recovery; no task recreation is needed when schedule values change."
