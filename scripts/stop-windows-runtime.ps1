param(
    [switch]$AppOnly
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # Best effort only.
}

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$ComposeFile = Join-Path $RepoRoot "docker-compose.local-postgres.yml"
$RunDir = Join-Path $RepoRoot "run"

function Write-InfoMessage {
    param([string]$Message)
    Write-Host "[INFO]  $Message"
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK]    $Message" -ForegroundColor Green
}

function Write-WarnMessage {
    param([string]$Message)
    Write-Host "[WARN]  $Message" -ForegroundColor Yellow
}

function Invoke-NativeCommandQuiet {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [string]$Arguments = ""
    )

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $FilePath
    $startInfo.Arguments = $Arguments
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    try {
        if (-not $process.Start()) {
            return [pscustomobject]@{ ExitCode = -1; StdOut = ""; StdErr = "Process could not be started." }
        }

        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()
        $process.WaitForExit()

        return [pscustomobject]@{
            ExitCode = $process.ExitCode
            StdOut = $stdout
            StdErr = $stderr
        }
    } catch {
        return [pscustomobject]@{ ExitCode = -1; StdOut = ""; StdErr = $_.Exception.Message }
    } finally {
        $process.Dispose()
    }
}

function Get-NormalizedCommandLine {
    param([object]$ProcessInfo)

    if ($null -eq $ProcessInfo -or [string]::IsNullOrWhiteSpace($ProcessInfo.CommandLine)) {
        return ""
    }

    return $ProcessInfo.CommandLine.Replace('/', '\').ToLowerInvariant()
}

function Test-IguanaPanelProcess {
    param([object]$ProcessInfo)

    $commandLine = Get-NormalizedCommandLine -ProcessInfo $ProcessInfo
    if ([string]::IsNullOrWhiteSpace($commandLine)) {
        return $false
    }

    $repoMarker = $RepoRoot.Replace('/', '\').ToLowerInvariant()
    if (-not $commandLine.Contains($repoMarker)) {
        return $false
    }

    return (
        $commandLine.Contains("com.example.panel.panelapplication") -or
        $commandLine.Contains("spring-boot:run") -or
        $commandLine.Contains("spring-panel\target\classes")
    )
}

function Get-IguanaPanelProcesses {
    $processes = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue
    if (-not $processes) {
        return @()
    }

    return @(
        $processes |
            Where-Object {
                $_.ProcessId -ne $PID -and (Test-IguanaPanelProcess -ProcessInfo $_)
            } |
            Sort-Object ProcessId -Unique
    )
}

function Stop-ProcessTree {
    param(
        [int]$ProcessId,
        [string]$Description
    )

    if (-not (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)) {
        return
    }

    Write-InfoMessage "Stopping $Description (PID $ProcessId)..."

    $taskkill = Join-Path $env:SystemRoot "System32\taskkill.exe"
    $graceful = Invoke-NativeCommandQuiet -FilePath $taskkill -Arguments "/PID $ProcessId /T"
    Start-Sleep -Milliseconds 800

    if (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) {
        $forced = Invoke-NativeCommandQuiet -FilePath $taskkill -Arguments "/PID $ProcessId /T /F"
        Start-Sleep -Milliseconds 500

        if ((Get-Process -Id $ProcessId -ErrorAction SilentlyContinue) -and $forced.ExitCode -ne 0) {
            $details = $forced.StdErr.Trim()
            if ([string]::IsNullOrWhiteSpace($details)) {
                $details = $forced.StdOut.Trim()
            }
            Write-WarnMessage "Could not stop PID $ProcessId. $details"
        }
    } elseif ($graceful.ExitCode -ne 0) {
        # The process disappeared between discovery and taskkill. That is a
        # successful end state, so a non-zero taskkill code is not fatal.
        Write-InfoMessage "PID $ProcessId exited while shutdown was in progress."
    }
}

function Stop-PanelRuntime {
    $processes = Get-IguanaPanelProcesses

    if ($processes.Count -eq 0) {
        Write-InfoMessage "Spring panel process is not running."
    } else {
        # Maven is normally the parent of the Spring Boot JVM. Stopping every
        # matching process is intentional; stale child/parent processes are
        # cleaned up even after a previous failed startup.
        foreach ($processInfo in $processes) {
            Stop-ProcessTree -ProcessId ([int]$processInfo.ProcessId) -Description "Iguana Spring panel"
        }

        Start-Sleep -Milliseconds 800

        $remaining = Get-IguanaPanelProcesses
        foreach ($processInfo in $remaining) {
            Stop-ProcessTree -ProcessId ([int]$processInfo.ProcessId) -Description "remaining Iguana Spring process"
        }

        Write-Ok "Spring panel stopped"
    }
}

function Stop-OrphanBotProcesses {
    if (-not (Test-Path -LiteralPath $RunDir)) {
        return
    }

    $pidFiles = Get-ChildItem -LiteralPath $RunDir -Filter "bot-*.pid" -File -ErrorAction SilentlyContinue
    foreach ($pidFile in $pidFiles) {
        $rawPid = (Get-Content -LiteralPath $pidFile.FullName -ErrorAction SilentlyContinue | Select-Object -First 1)
        $botPid = 0

        if ([int]::TryParse([string]$rawPid, [ref]$botPid) -and $botPid -gt 0) {
            if (Get-Process -Id $botPid -ErrorAction SilentlyContinue) {
                Stop-ProcessTree -ProcessId $botPid -Description "Iguana bot runtime"
            }
        }

        Remove-Item -LiteralPath $pidFile.FullName -Force -ErrorAction SilentlyContinue
    }
}

function Get-DockerCommandPath {
    $command = Get-Command docker.exe -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $candidates = @(
        (Join-Path $env:ProgramFiles "Docker\Docker\resources\bin\docker.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\resources\bin\docker.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\resources\bin\docker.exe")
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate) -and (Test-Path -LiteralPath $candidate)) {
            return $candidate
        }
    }

    return $null
}

function Stop-DockerInfrastructure {
    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        Write-WarnMessage "Compose file was not found: $ComposeFile"
        return
    }

    $docker = Get-DockerCommandPath
    if (-not $docker) {
        Write-InfoMessage "Docker CLI is not installed; no Docker infrastructure was stopped."
        return
    }

    $dockerInfo = Invoke-NativeCommandQuiet -FilePath $docker -Arguments "info"
    if ($dockerInfo.ExitCode -ne 0) {
        Write-InfoMessage "Docker Engine is not running; PostgreSQL/RabbitMQ are already unavailable."
        return
    }

    Write-InfoMessage "Stopping PostgreSQL and RabbitMQ containers (volumes are preserved)..."

    $arguments = 'compose --project-directory "{0}" -f "{1}" stop postgres rabbitmq' -f $RepoRoot, $ComposeFile
    $result = Invoke-NativeCommandQuiet -FilePath $docker -Arguments $arguments

    if ($result.ExitCode -ne 0) {
        $details = $result.StdErr.Trim()
        if ([string]::IsNullOrWhiteSpace($details)) {
            $details = $result.StdOut.Trim()
        }
        throw "docker compose stop failed. $details"
    }

    Write-Ok "PostgreSQL and RabbitMQ stopped; data volumes were preserved"
}

try {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " Iguana Windows stop"
    Write-Host "============================================================"
    Write-Host ""

    Stop-PanelRuntime
    Stop-OrphanBotProcesses

    if ($AppOnly) {
        Write-InfoMessage "App-only mode: PostgreSQL and RabbitMQ were left running."
    } else {
        Stop-DockerInfrastructure
    }

    Write-Host ""
    Write-Ok "Iguana stop completed"
    Write-Host ""
    exit 0
} catch {
    Write-Host ""
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    exit 1
}
