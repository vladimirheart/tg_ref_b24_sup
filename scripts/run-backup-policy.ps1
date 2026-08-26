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

function Read-State([string]$Path) {
    $state = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $state }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $idx = $line.IndexOf("=")
        if ($idx -gt 0) { $state[$line.Substring(0, $idx)] = $line.Substring($idx + 1) }
    }
    $state
}

function Write-State([string]$Path, [hashtable]$State) {
    $tmp = "$Path.tmp.$PID"
    $content = @()
    foreach ($key in @("critical_last_slot", "full_last_slot")) {
        if ($State.ContainsKey($key)) { $content += "$key=$($State[$key])" }
    }
    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($tmp, (($content -join "`n") + "`n"), $enc)
    Move-Item -LiteralPath $tmp -Destination $Path -Force
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
    -not ($State.ContainsKey($key) -and $State[$key] -eq $slot)
}

$shared = Resolve-IguanaSharedConfigDirectory -RepoRoot $repoRoot
New-Item -ItemType Directory -Force -Path $shared | Out-Null
$statePath = Join-Path $shared "backup-scheduler.state"
$mutex = New-Object System.Threading.Mutex($false, "IguanaBackupPolicyRunner")
$acquired = $false
try {
    $acquired = $mutex.WaitOne(0)
    if (-not $acquired) { Write-Host "[INFO] Backup policy runner is already active."; exit 0 }

    $state = Read-State $statePath
    $now = Get-Date

    if (Is-Due "CRITICAL" $now $state) {
        Write-Host "[SCHEDULE] Running critical backup"
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $helper -Action backup -Mode critical
        if ($LASTEXITCODE -ne 0) { throw "Critical scheduled backup failed: exit $LASTEXITCODE" }
        $state["critical_last_slot"] = $now.ToString("yyyy-MM-dd")
        Write-State $statePath $state
    }

    if (Is-Due "FULL" $now $state) {
        Write-Host "[SCHEDULE] Running full backup + isolated restore rehearsal"
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $helper -Action full -Mode full
        if ($LASTEXITCODE -ne 0) { throw "Full scheduled backup/rehearsal failed: exit $LASTEXITCODE" }
        $state["full_last_slot"] = $now.ToString("yyyy-MM-dd")
        Write-State $statePath $state
    }
} finally {
    if ($acquired) { $mutex.ReleaseMutex() | Out-Null }
    $mutex.Dispose()
}
