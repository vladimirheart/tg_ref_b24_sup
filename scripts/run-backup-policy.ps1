param(
    [switch]$Force,
    [switch]$Daemon,
    [ValidateRange(1, 60)]
    [int]$IdleSeconds = 5,
    [int]$ParentPid = 0
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$library = Join-Path $PSScriptRoot "lib\backup-config.ps1"
$helper = Join-Path $PSScriptRoot "docker-production-backup.ps1"

. $library

function Truthy([string]$Value) {
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    @("1", "true", "yes", "on") -contains $Value.Trim().ToLowerInvariant()
}

function Get-Env([string]$Name, [string]$Fallback = "") {
    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) { return $Fallback }
    $value.Trim()
}

function Weekday-Code([DayOfWeek]$Day) {
    switch ($Day) {
        Monday { "MON" }
        Tuesday { "TUE" }
        Wednesday { "WED" }
        Thursday { "THU" }
        Friday { "FRI" }
        Saturday { "SAT" }
        Sunday { "SUN" }
    }
}

function Read-FlatFile([string]$Path) {
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { return $result }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or $trimmed.StartsWith("!")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -gt 0) {
            $result[$trimmed.Substring(0, $idx).Trim()] = $trimmed.Substring($idx + 1).Trim()
        }
    }

    return $result
}

function Write-FlatFile([string]$Path, [System.Collections.IDictionary]$Values) {
    $tmp = "$Path.tmp.$PID"
    $lines = New-Object System.Collections.Generic.List[string]

    foreach ($key in $Values.Keys) {
        $value = [string]$Values[$key]
        $value = $value.Replace("`r", " ").Replace("`n", " ").Replace("=", "_")
        [void]$lines.Add("$key=$value")
    }

    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($tmp, (($lines -join "`n") + "`n"), $enc)
    Move-Item -LiteralPath $tmp -Destination $Path -Force
}

function Read-State([string]$Path) {
    return Read-FlatFile $Path
}

function Write-State([string]$Path, [hashtable]$State) {
    $values = [ordered]@{}
    foreach ($key in @("critical_last_slot", "full_last_slot")) {
        if ($State.ContainsKey($key)) {
            $values[$key] = $State[$key]
        }
    }
    Write-FlatFile $Path $values
}

$policyVariableNames = @(
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

$shared = Resolve-IguanaSharedConfigDirectory -RepoRoot $repoRoot
New-Item -ItemType Directory -Force -Path $shared | Out-Null

$policyPath = Join-Path $shared "backup.properties"
$statePath = Join-Path $shared "backup-scheduler.state"
$requestPath = Join-Path $shared "backup-manual-request.properties"
$runningPath = Join-Path $shared "backup-manual-request.running"
$statusPath = Join-Path $shared "backup-manual-status.properties"
$runnerStatusPath = Join-Path $shared "backup-policy-runner.status"
$stopPath = Join-Path $shared "backup-policy-runner.stop"

function Refresh-BackupPolicyEnvironment {
    foreach ($name in $policyVariableNames) {
        [Environment]::SetEnvironmentVariable($name, $null, "Process")
    }

    if (Test-Path -LiteralPath $policyPath -PathType Leaf) {
        $policy = Read-FlatFile $policyPath
        foreach ($name in $policyVariableNames) {
            if ($policy.ContainsKey($name) -and -not [string]::IsNullOrWhiteSpace([string]$policy[$name])) {
                [Environment]::SetEnvironmentVariable($name, [string]$policy[$name], "Process")
            }
        }
        return
    }

    Import-IguanaBackupSettings -RepoRoot $repoRoot | Out-Null
}

function Invoke-BackupHelper([string[]]$Arguments) {
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & powershell.exe @Arguments 2>&1 | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
}

function Resolve-ManualRestoreComponents([string]$Mode) {
    switch ($Mode) {
        "critical" { return "postgres,minio,shared-config" }
        "full" { return "postgres,minio,shared-config,templates,static-js,static-css" }
        "custom" { return (Get-Env "IGUANA_BACKUP_CUSTOM_COMPONENTS" "postgres,minio,shared-config") }
        default { throw "Unsupported manual backup mode: $Mode" }
    }
}

function Write-RunnerHeartbeat {
    param(
        [ValidateSet("online", "offline")]
        [string]$Status = "online",
        [string]$Message = ""
    )

    $scheduleReady = Truthy (Get-Env "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN" "false")
    Write-FlatFile $runnerStatusPath ([ordered]@{
        status = $Status
        last_seen_at = [DateTimeOffset]::UtcNow.ToString("o")
        platform = "windows"
        mode = $(if ($Daemon) { "daemon" } else { "oneshot" })
        schedule_ready = $scheduleReady.ToString().ToLowerInvariant()
        runner_version = "panel-lifecycle-v1"
        process_id = $PID
        message = $Message
    })
}

function Process-ManualRequest {
    if (Test-Path -LiteralPath $runningPath -PathType Leaf) {
        $age = [DateTime]::UtcNow - (Get-Item -LiteralPath $runningPath).LastWriteTimeUtc
        if ($age.TotalHours -gt 2) {
            $stale = Read-FlatFile $runningPath
            Write-FlatFile $statusPath ([ordered]@{
                request_id = $stale["request_id"]
                status = "error"
                mode = $stale["mode"]
                finished_at = [DateTimeOffset]::UtcNow.ToString("o")
                message = "Stale manual backup claim detected after runner interruption."
            })
            Remove-Item -LiteralPath $runningPath -Force -ErrorAction SilentlyContinue
        } else {
            return
        }
    }

    if (-not (Test-Path -LiteralPath $requestPath -PathType Leaf)) { return }

    try {
        Move-Item -LiteralPath $requestPath -Destination $runningPath -ErrorAction Stop
    } catch {
        return
    }

    $request = Read-FlatFile $runningPath
    $requestId = [string]$request["request_id"]
    $mode = ([string]$request["mode"]).Trim().ToLowerInvariant()
    $verifyRestore = Truthy ([string]$request["verify_restore"])
    $allowLocalTest = Truthy ([string]$request["allow_local_test"])
    $startedAt = [DateTimeOffset]::UtcNow.ToString("o")

    try {
        if (@("critical", "full", "custom") -notcontains $mode) {
            throw "Unsupported manual backup mode in request: $mode"
        }

        Write-FlatFile $statusPath ([ordered]@{
            request_id = $requestId
            status = "running"
            mode = $mode
            verify_restore = $verifyRestore.ToString().ToLowerInvariant()
            allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
            requested_at = [string]$request["requested_at"]
            requested_by = [string]$request["requested_by"]
            started_at = $startedAt
            message = "Manual backup is running on Docker host."
        })

        $action = if ($verifyRestore) { "full" } else { "backup" }
        $arguments = @(
            "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", $helper,
            "-Action", $action,
            "-Mode", $mode
        )

        if ($verifyRestore) {
            $arguments += @("-RestoreComponents", (Resolve-ManualRestoreComponents $mode))
        }
        if ($allowLocalTest) {
            $arguments += "-AllowLocalDestination"
        }

        Write-Host "[MANUAL] request=$requestId mode=$mode restore=$verifyRestore local_test=$allowLocalTest"
        $exitCode = Invoke-BackupHelper $arguments
        $finishedAt = [DateTimeOffset]::UtcNow.ToString("o")

        if ($exitCode -eq 0) {
            Write-FlatFile $statusPath ([ordered]@{
                request_id = $requestId
                status = "success"
                mode = $mode
                verify_restore = $verifyRestore.ToString().ToLowerInvariant()
                allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
                requested_at = [string]$request["requested_at"]
                requested_by = [string]$request["requested_by"]
                started_at = $startedAt
                finished_at = $finishedAt
                message = "Manual backup completed successfully."
            })
            Write-Host "[GREEN] Manual backup request completed: $requestId"
        } else {
            Write-FlatFile $statusPath ([ordered]@{
                request_id = $requestId
                status = "error"
                mode = $mode
                verify_restore = $verifyRestore.ToString().ToLowerInvariant()
                allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
                requested_at = [string]$request["requested_at"]
                requested_by = [string]$request["requested_by"]
                started_at = $startedAt
                finished_at = $finishedAt
                message = "Manual backup failed with exit code $exitCode."
            })
            Write-Warning "Manual backup request failed: $requestId; exit=$exitCode"
        }
    } catch {
        Write-FlatFile $statusPath ([ordered]@{
            request_id = $requestId
            status = "error"
            mode = $mode
            verify_restore = $verifyRestore.ToString().ToLowerInvariant()
            allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
            requested_at = [string]$request["requested_at"]
            requested_by = [string]$request["requested_by"]
            started_at = $startedAt
            finished_at = [DateTimeOffset]::UtcNow.ToString("o")
            message = $_.Exception.Message
        })
        Write-Warning "Manual backup request failed: $($_.Exception.Message)"
    } finally {
        Remove-Item -LiteralPath $runningPath -Force -ErrorAction SilentlyContinue
    }
}

function Is-Due([string]$Prefix, [datetime]$Now, [hashtable]$State) {
    if ($Force) { return $true }
    if (-not (Truthy (Get-Env "IGUANA_BACKUP_${Prefix}_ENABLED" "false"))) { return $false }

    $frequency = (Get-Env "IGUANA_BACKUP_${Prefix}_FREQUENCY" "daily").ToLowerInvariant()
    $timeRaw = Get-Env "IGUANA_BACKUP_${Prefix}_TIME" "02:00"
    $parts = $timeRaw.Split(":")
    if ($parts.Count -ne 2) { throw "Invalid ${Prefix} schedule time: $timeRaw" }

    $scheduled = $Now.Date.AddHours([int]$parts[0]).AddMinutes([int]$parts[1])
    if ($Now -lt $scheduled) { return $false }

    if ($frequency -eq "weekly") {
        $weekday = Get-Env "IGUANA_BACKUP_${Prefix}_WEEKDAY" "SUN"
        if ((Weekday-Code $Now.DayOfWeek) -ne $weekday) { return $false }
    } elseif ($frequency -ne "daily") {
        throw "Unsupported ${Prefix} frequency: $frequency"
    }

    $slot = $Now.ToString("yyyy-MM-dd")
    $key = $Prefix.ToLowerInvariant() + "_last_slot"
    return -not ($State.ContainsKey($key) -and $State[$key] -eq $slot)
}

function Invoke-ScheduledPlan {
    param(
        [string]$Prefix,
        [string]$Mode,
        [string]$Action,
        [string]$RestoreComponents,
        [hashtable]$State,
        [datetime]$Now
    )

    $slotKey = $Prefix.ToLowerInvariant() + "_last_slot"
    $State[$slotKey] = $Now.ToString("yyyy-MM-dd")
    Write-State $statePath $State

    $arguments = @(
        "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", $helper,
        "-Action", $Action,
        "-Mode", $Mode
    )
    if (-not [string]::IsNullOrWhiteSpace($RestoreComponents)) {
        $arguments += @("-RestoreComponents", $RestoreComponents)
    }

    Write-Host "[SCHEDULE] Running $($Prefix.ToLowerInvariant()) backup plan"
    $code = Invoke-BackupHelper $arguments
    if ($code -ne 0) {
        Write-Warning "$Prefix scheduled backup failed with exit code $code. This schedule slot will not be retried automatically."
    }
}

function Invoke-PolicyCycle {
    Refresh-BackupPolicyEnvironment
    Write-RunnerHeartbeat -Status "online"

    Process-ManualRequest

    if (-not (Truthy (Get-Env "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN" "false"))) {
        return
    }

    $state = Read-State $statePath
    $now = Get-Date

    if (Is-Due "CRITICAL" $now $state) {
        Invoke-ScheduledPlan `
            -Prefix "CRITICAL" `
            -Mode "critical" `
            -Action "backup" `
            -RestoreComponents "" `
            -State $state `
            -Now $now
    }

    if (Is-Due "FULL" $now $state) {
        Invoke-ScheduledPlan `
            -Prefix "FULL" `
            -Mode "full" `
            -Action "full" `
            -RestoreComponents "postgres,minio,shared-config,templates,static-js,static-css" `
            -State $state `
            -Now $now
    }
}

function Test-ParentAlive([int]$ProcessId) {
    if ($ProcessId -le 0) { return $true }
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

$mutex = New-Object System.Threading.Mutex($false, "IguanaBackupPolicyRunner")
$acquired = $false

try {
    $acquired = $mutex.WaitOne(0)
    if (-not $acquired) {
        Write-Host "[INFO] Backup policy runner is already active."
        exit 0
    }

    Remove-Item -LiteralPath $stopPath -Force -ErrorAction SilentlyContinue

    if ($Daemon) {
        Write-Host "[INFO] Backup policy runner daemon started. pid=$PID idle=${IdleSeconds}s parent=$ParentPid"
        while ($true) {
            if (Test-Path -LiteralPath $stopPath -PathType Leaf) {
                Write-Host "[INFO] Backup policy runner stop signal received."
                break
            }

            if (-not (Test-ParentAlive $ParentPid)) {
                Write-Host "[INFO] Panel launcher parent process exited. Stopping backup policy runner."
                break
            }

            try {
                Invoke-PolicyCycle
            } catch {
                Write-Warning "Backup policy cycle failed but daemon will stay alive: $($_.Exception.Message)"
                try {
                    Write-RunnerHeartbeat -Status "online" -Message $_.Exception.Message
                } catch {
                    # Keep daemon alive even if heartbeat write fails transiently.
                }
            }

            Start-Sleep -Seconds $IdleSeconds
        }
    } else {
        Invoke-PolicyCycle
    }
} finally {
    try {
        Refresh-BackupPolicyEnvironment
        Write-RunnerHeartbeat -Status "offline" -Message "Panel lifecycle runner stopped."
    } catch {
        # Best-effort shutdown state only.
    }

    Remove-Item -LiteralPath $stopPath -Force -ErrorAction SilentlyContinue

    if ($acquired) {
        $mutex.ReleaseMutex() | Out-Null
    }
    $mutex.Dispose()
}
