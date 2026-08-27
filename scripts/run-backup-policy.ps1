param([switch]$Force)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$library = Join-Path $PSScriptRoot "lib\backup-config.ps1"
$helper = Join-Path $PSScriptRoot "docker-production-backup.ps1"
. $library
Import-IguanaBackupSettings -RepoRoot $repoRoot | Out-Null

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
        Monday { "MON" }; Tuesday { "TUE" }; Wednesday { "WED" }; Thursday { "THU" }
        Friday { "FRI" }; Saturday { "SAT" }; Sunday { "SUN" }
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
    $result
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

function Read-State([string]$Path) { Read-FlatFile $Path }

function Write-State([string]$Path, [hashtable]$State) {
    $values = [ordered]@{}
    foreach ($key in @("critical_last_slot", "full_last_slot")) {
        if ($State.ContainsKey($key)) { $values[$key] = $State[$key] }
    }
    Write-FlatFile $Path $values
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
        "critical" { "postgres,minio,shared-config" }
        "full" { "postgres,minio,shared-config,templates,static-js,static-css" }
        "custom" { Get-Env "IGUANA_BACKUP_CUSTOM_COMPONENTS" "postgres,minio,shared-config" }
        default { throw "Unsupported manual backup mode: $Mode" }
    }
}

function Write-RunnerHeartbeat([string]$Path) {
    $scheduleReady = Truthy (Get-Env "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN" "false")
    Write-FlatFile $Path ([ordered]@{
        status = "online"
        last_seen_at = [DateTimeOffset]::UtcNow.ToString("o")
        platform = "windows"
        schedule_ready = $scheduleReady.ToString().ToLowerInvariant()
        runner_version = "manual-backup-v1"
    })
}

function Process-ManualRequest {
    param([string]$RequestPath, [string]$RunningPath, [string]$StatusPath)

    if (Test-Path -LiteralPath $RunningPath -PathType Leaf) {
        $age = [DateTime]::UtcNow - (Get-Item -LiteralPath $RunningPath).LastWriteTimeUtc
        if ($age.TotalHours -gt 2) {
            $stale = Read-FlatFile $RunningPath
            Write-FlatFile $StatusPath ([ordered]@{
                request_id = $stale["request_id"]
                status = "error"
                mode = $stale["mode"]
                finished_at = [DateTimeOffset]::UtcNow.ToString("o")
                message = "Stale manual backup claim detected after runner interruption."
            })
            Remove-Item -LiteralPath $RunningPath -Force -ErrorAction SilentlyContinue
        } else {
            Write-Host "[INFO] Manual backup claim already exists; another runner invocation is processing it."
            return
        }
    }

    if (-not (Test-Path -LiteralPath $RequestPath -PathType Leaf)) { return }
    try { Move-Item -LiteralPath $RequestPath -Destination $RunningPath -ErrorAction Stop }
    catch { Write-Host "[INFO] Manual backup request was claimed by another runner invocation."; return }

    $request = Read-FlatFile $RunningPath
    $requestId = [string]$request["request_id"]
    $mode = ([string]$request["mode"]).Trim().ToLowerInvariant()
    $verifyRestore = Truthy ([string]$request["verify_restore"])
    $allowLocalTest = Truthy ([string]$request["allow_local_test"])
    $startedAt = [DateTimeOffset]::UtcNow.ToString("o")

    try {
        if (@("critical", "full", "custom") -notcontains $mode) { throw "Unsupported manual backup mode in request: $mode" }

        Write-FlatFile $StatusPath ([ordered]@{
            request_id = $requestId; status = "running"; mode = $mode
            verify_restore = $verifyRestore.ToString().ToLowerInvariant()
            allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
            requested_at = [string]$request["requested_at"]
            requested_by = [string]$request["requested_by"]
            started_at = $startedAt
            message = "Manual backup is running on Docker host."
        })

        $action = if ($verifyRestore) { "full" } else { "backup" }
        $arguments = @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $helper, "-Action", $action, "-Mode", $mode)
        if ($verifyRestore) { $arguments += @("-RestoreComponents", (Resolve-ManualRestoreComponents $mode)) }
        if ($allowLocalTest) { $arguments += "-AllowLocalDestination" }

        Write-Host "[MANUAL] request=$requestId mode=$mode restore=$verifyRestore local_test=$allowLocalTest"
        $exitCode = Invoke-BackupHelper $arguments
        $finishedAt = [DateTimeOffset]::UtcNow.ToString("o")
        $finalStatus = if ($exitCode -eq 0) { "success" } else { "error" }
        $message = if ($exitCode -eq 0) { "Manual backup completed successfully." } else { "Manual backup failed with exit code $exitCode." }

        Write-FlatFile $StatusPath ([ordered]@{
            request_id = $requestId; status = $finalStatus; mode = $mode
            verify_restore = $verifyRestore.ToString().ToLowerInvariant()
            allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
            requested_at = [string]$request["requested_at"]
            requested_by = [string]$request["requested_by"]
            started_at = $startedAt; finished_at = $finishedAt; message = $message
        })
        if ($exitCode -eq 0) { Write-Host "[GREEN] Manual backup request completed: $requestId" }
        else { Write-Warning "Manual backup request failed: $requestId; exit=$exitCode" }
    } catch {
        Write-FlatFile $StatusPath ([ordered]@{
            request_id = $requestId; status = "error"; mode = $mode
            verify_restore = $verifyRestore.ToString().ToLowerInvariant()
            allow_local_test = $allowLocalTest.ToString().ToLowerInvariant()
            requested_at = [string]$request["requested_at"]
            requested_by = [string]$request["requested_by"]
            started_at = $startedAt; finished_at = [DateTimeOffset]::UtcNow.ToString("o")
            message = $_.Exception.Message
        })
        Write-Warning "Manual backup request failed: $($_.Exception.Message)"
    } finally {
        Remove-Item -LiteralPath $RunningPath -Force -ErrorAction SilentlyContinue
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
    } elseif ($frequency -ne "daily") { throw "Unsupported ${Prefix} frequency: $frequency" }
    $slot = $Now.ToString("yyyy-MM-dd")
    $key = $Prefix.ToLowerInvariant() + "_last_slot"
    -not ($State.ContainsKey($key) -and $State[$key] -eq $slot)
}

$shared = Resolve-IguanaSharedConfigDirectory -RepoRoot $repoRoot
New-Item -ItemType Directory -Force -Path $shared | Out-Null
$statePath = Join-Path $shared "backup-scheduler.state"
$requestPath = Join-Path $shared "backup-manual-request.properties"
$runningPath = Join-Path $shared "backup-manual-request.running"
$statusPath = Join-Path $shared "backup-manual-status.properties"
$runnerStatusPath = Join-Path $shared "backup-policy-runner.status"

Write-RunnerHeartbeat $runnerStatusPath
$mutex = New-Object System.Threading.Mutex($false, "IguanaBackupPolicyRunner")
$acquired = $false
try {
    $acquired = $mutex.WaitOne(0)
    if (-not $acquired) { Write-Host "[INFO] Backup policy runner is already active."; exit 0 }

    Process-ManualRequest -RequestPath $requestPath -RunningPath $runningPath -StatusPath $statusPath

    if (-not (Truthy (Get-Env "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN" "false"))) {
        Write-Host "[INFO] Scheduled backup plans skipped: external failure domain is not acknowledged."
        exit 0
    }

    $state = Read-State $statePath
    $now = Get-Date
    if (Is-Due "CRITICAL" $now $state) {
        Write-Host "[SCHEDULE] Running critical backup"
        $code = Invoke-BackupHelper @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $helper, "-Action", "backup", "-Mode", "critical")
        if ($code -ne 0) { throw "Critical scheduled backup failed: exit $code" }
        $state["critical_last_slot"] = $now.ToString("yyyy-MM-dd")
        Write-State $statePath $state
    }
    if (Is-Due "FULL" $now $state) {
        Write-Host "[SCHEDULE] Running full backup + isolated restore rehearsal"
        $code = Invoke-BackupHelper @(
            "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", $helper,
            "-Action", "full", "-Mode", "full",
            "-RestoreComponents", "postgres,minio,shared-config,templates,static-js,static-css"
        )
        if ($code -ne 0) { throw "Full scheduled backup/rehearsal failed: exit $code" }
        $state["full_last_slot"] = $now.ToString("yyyy-MM-dd")
        Write-State $statePath $state
    }
} finally {
    try { Write-RunnerHeartbeat $runnerStatusPath } catch { }
    if ($acquired) { $mutex.ReleaseMutex() | Out-Null }
    $mutex.Dispose()
}
