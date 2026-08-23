param(
    [Parameter(Mandatory = $true)]
    [ValidateRange(1, 65535)]
    [int]$Port,

    [Parameter(Mandatory = $true)]
    [string]$RepoRoot,

    [int]$WaitSeconds = 8
)

$ErrorActionPreference = 'Stop'

function Get-ListeningPids {
    param([int]$TargetPort)

    $result = New-Object System.Collections.Generic.HashSet[int]
    $pattern = ':' + $TargetPort + '\s+.*LISTENING\s+(\d+)\s*$'

    foreach ($line in @(& netstat.exe -ano -p tcp 2>$null)) {
        $text = [string]$line
        $match = [regex]::Match($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            [void]$result.Add([int]$match.Groups[1].Value)
        }
    }

    return @($result)
}

function Get-ProcessCommandLine {
    param([int]$ProcessId)

    try {
        $record = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction Stop
        return [string]$record.CommandLine
    }
    catch {
        return ''
    }
}

function Test-IguanaPanelProcess {
    param(
        [int]$ProcessId,
        [string]$ExpectedRepoRoot
    )

    $commandLine = Get-ProcessCommandLine -ProcessId $ProcessId
    if (-not $commandLine) {
        return $false
    }

    $repo = [System.IO.Path]::GetFullPath($ExpectedRepoRoot).TrimEnd('\', '/').ToLowerInvariant()
    $normalizedCommand = $commandLine.Replace('/', '\').ToLowerInvariant()

    $belongsToRepo = $normalizedCommand.Contains($repo.Replace('/', '\'))
    if (-not $belongsToRepo) {
        return $false
    }

    return $normalizedCommand.Contains('\spring-panel\') -and (
        $normalizedCommand.Contains('com.example.panel.panelapplication') -or
        $normalizedCommand.Contains('\spring-panel\target\classes') -or
        $normalizedCommand.Contains('spring-boot:run')
    )
}

$pids = @(Get-ListeningPids -TargetPort $Port)
if ($pids.Count -eq 0) {
    exit 0
}

$ownedPids = New-Object System.Collections.Generic.List[int]
$foreignPids = New-Object System.Collections.Generic.List[int]

foreach ($pidValue in $pids) {
    if (Test-IguanaPanelProcess -ProcessId $pidValue -ExpectedRepoRoot $RepoRoot) {
        $ownedPids.Add($pidValue)
    } else {
        $foreignPids.Add($pidValue)
    }
}

if ($ownedPids.Count -eq 0) {
    if ($foreignPids.Count -gt 0) {
        Write-Host "[WARN] Port $Port is still occupied by non-Iguana PID(s): $($foreignPids -join ', '). Leaving them untouched."
    }
    exit 0
}

Write-Host "[WARN] Port $Port is still held by Iguana panel PID(s): $($ownedPids -join ', '). Cleaning up the orphaned process tree."

foreach ($pidValue in $ownedPids) {
    try {
        & taskkill.exe /PID $pidValue /T /F | Out-Null
    }
    catch {
        Write-Host "[WARN] Unable to terminate orphaned panel PID ${pidValue}: $($_.Exception.Message)"
    }
}

$deadline = [DateTime]::UtcNow.AddSeconds([Math]::Max(1, $WaitSeconds))
do {
    Start-Sleep -Milliseconds 250
    $remaining = @(Get-ListeningPids -TargetPort $Port)
    if ($remaining.Count -eq 0) {
        Write-Host "[INFO] Port $Port released."
        exit 0
    }
} while ([DateTime]::UtcNow -lt $deadline)

$remaining = @(Get-ListeningPids -TargetPort $Port)
Write-Host "[WARN] Port $Port is still occupied after cleanup. PID(s): $($remaining -join ', ')."
exit 0
