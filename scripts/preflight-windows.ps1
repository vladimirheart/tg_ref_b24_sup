param(
    [switch]$ForceBootstrap,

    [ValidateSet(
        "Run",
        "EnableWslFeatures",
        "InstallWsl",
        "UpdateWsl",
        "EnableHypervisor",
        "EnableLanmanServer"
    )]
    [string]$Action = "Run"
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # Best effort only.
}

$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$BootstrapScript = Join-Path $PSScriptRoot "bootstrap-first-run.ps1"
$ComposeFile = Join-Path $RepoRoot "docker-compose.local-postgres.yml"
$EnvFile = Join-Path $RepoRoot ".env"

$DockerTimeoutSeconds = 300
$ContainerTimeoutSeconds = 300

function Write-Check {
    param([string]$Message)
    Write-Host "[CHECK] $Message" -ForegroundColor Cyan
}

function Write-Ok {
    param([string]$Message)
    Write-Host "[OK]    $Message" -ForegroundColor Green
}

function Write-InfoMessage {
    param([string]$Message)
    Write-Host "[INFO]  $Message"
}

function Write-WarnMessage {
    param([string]$Message)
    Write-Host "[WARN]  $Message" -ForegroundColor Yellow
}

function Write-ErrorMessage {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

function Get-BoolEnvironmentValue {
    param(
        [string]$Name,
        [bool]$Default = $false
    )

    $raw = [Environment]::GetEnvironmentVariable($Name)

    if ([string]::IsNullOrWhiteSpace($raw)) {
        return $Default
    }

    switch ($raw.Trim().ToLowerInvariant()) {
        "1"     { return $true }
        "true"  { return $true }
        "yes"   { return $true }
        "on"    { return $true }
        "0"     { return $false }
        "false" { return $false }
        "no"    { return $false }
        "off"   { return $false }
        default { return $Default }
    }
}

function Confirm-IguanaAction {
    param([string]$Message)

    if (Get-BoolEnvironmentValue -Name "IGUANA_PREFLIGHT_AUTO_APPROVE" -Default $false) {
        Write-InfoMessage "$Message [AUTO-APPROVED]"
        return $true
    }

    while ($true) {
        $answer = Read-Host "$Message [Y/N]"

        switch ($answer.Trim().ToLowerInvariant()) {
            "y"   { return $true }
            "yes" { return $true }
            "n"   { return $false }
            "no"  { return $false }
            default {
                Write-WarnMessage "Please enter Y or N."
            }
        }
    }
}

function Test-IsAdministrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)

    return $principal.IsInRole(
        [Security.Principal.WindowsBuiltInRole]::Administrator
    )
}

function Invoke-ElevatedAction {
    param([string]$ActionName)

    $powershellExe = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"

    if (-not (Test-Path -LiteralPath $powershellExe)) {
        $powershellExe = "powershell.exe"
    }

    $arguments = (
        '-NoLogo -NoProfile -ExecutionPolicy Bypass -File "{0}" -Action "{1}"'
    ) -f $PSCommandPath, $ActionName

    Write-InfoMessage "Administrator privileges are required. Windows UAC will be shown."

    $process = Start-Process `
        -FilePath $powershellExe `
        -Verb RunAs `
        -ArgumentList $arguments `
        -Wait `
        -PassThru

    if ($process.ExitCode -ne 0) {
        throw "Administrative action '$ActionName' failed with exit code $($process.ExitCode)."
    }
}

function Refresh-ProcessEnvironment {
    $machinePath = [Environment]::GetEnvironmentVariable(
        "Path",
        [EnvironmentVariableTarget]::Machine
    )

    $userPath = [Environment]::GetEnvironmentVariable(
        "Path",
        [EnvironmentVariableTarget]::User
    )

    $pathParts = @()

    if (-not [string]::IsNullOrWhiteSpace($machinePath)) {
        $pathParts += $machinePath
    }

    if (-not [string]::IsNullOrWhiteSpace($userPath)) {
        $pathParts += $userPath
    }

    if ($pathParts.Count -gt 0) {
        $env:Path = $pathParts -join ";"
    }

    foreach ($name in @("JAVA_HOME", "JAVA_HOME_17")) {
        $machineValue = [Environment]::GetEnvironmentVariable(
            $name,
            [EnvironmentVariableTarget]::Machine
        )

        $userValue = [Environment]::GetEnvironmentVariable(
            $name,
            [EnvironmentVariableTarget]::User
        )

        if (-not [string]::IsNullOrWhiteSpace($userValue)) {
            [Environment]::SetEnvironmentVariable($name, $userValue, "Process")
        } elseif (-not [string]::IsNullOrWhiteSpace($machineValue)) {
            [Environment]::SetEnvironmentVariable($name, $machineValue, "Process")
        }
    }
}

function Test-PendingWindowsReboot {
    $paths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Component Based Servicing\RebootPending",
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\WindowsUpdate\Auto Update\RebootRequired"
    )

    foreach ($path in $paths) {
        if (Test-Path -LiteralPath $path) {
            return $true
        }
    }

    $sessionManager = "HKLM:\SYSTEM\CurrentControlSet\Control\Session Manager"

    try {
        $pending = Get-ItemProperty `
            -LiteralPath $sessionManager `
            -Name PendingFileRenameOperations `
            -ErrorAction Stop

        if ($null -ne $pending.PendingFileRenameOperations) {
            return $true
        }
    } catch {
        # Property is absent: no pending file rename detected.
    }

    return $false
}

function Request-Reboot {
    Write-Host ""
    Write-WarnMessage "Windows must be restarted before Iguana can continue."
    Write-WarnMessage "The application will not be started until after the reboot."
    Write-Host ""

    if (Confirm-IguanaAction "Restart Windows now?") {
        Write-InfoMessage "Windows will restart in 5 seconds."

        Start-Process `
            -FilePath "$env:SystemRoot\System32\shutdown.exe" `
            -ArgumentList '/r /t 5 /c "Iguana environment setup requires a restart"'

        exit 3010
    }

    Write-InfoMessage "Restart Windows manually, then run Iguana again."
    exit 3010
}

function Enable-WindowsFeatureWithDism {
    param([string]$FeatureName)

    & dism.exe `
        /online `
        /enable-feature `
        "/featurename:$FeatureName" `
        /all `
        /norestart

    if ($LASTEXITCODE -notin @(0, 3010)) {
        throw "DISM failed to enable '$FeatureName'. Exit code: $LASTEXITCODE"
    }
}

if ($Action -ne "Run") {
    if (-not (Test-IsAdministrator)) {
        throw "Action '$Action' requires administrator privileges."
    }

    switch ($Action) {
        "EnableWslFeatures" {
            Enable-WindowsFeatureWithDism `
                -FeatureName "Microsoft-Windows-Subsystem-Linux"

            Enable-WindowsFeatureWithDism `
                -FeatureName "VirtualMachinePlatform"

            exit 0
        }

        "InstallWsl" {
            & wsl.exe `
                --install `
                --no-distribution `
                --web-download

            if ($LASTEXITCODE -notin @(0, 3010)) {
                throw "WSL installation failed. Exit code: $LASTEXITCODE"
            }

            exit 0
        }

        "UpdateWsl" {
            & wsl.exe `
                --update `
                --web-download

            if ($LASTEXITCODE -notin @(0, 3010)) {
                throw "WSL update failed. Exit code: $LASTEXITCODE"
            }

            exit 0
        }

        "EnableHypervisor" {
            & "$env:SystemRoot\System32\bcdedit.exe" `
                /set `
                hypervisorlaunchtype `
                auto

            if ($LASTEXITCODE -ne 0) {
                throw "Failed to enable automatic hypervisor startup."
            }

            exit 0
        }

        "EnableLanmanServer" {
            Set-Service -Name LanmanServer -StartupType Automatic

            $service = Get-Service -Name LanmanServer

            if ($service.Status -ne "Running") {
                Start-Service -Name LanmanServer
            }

            exit 0
        }
    }
}

function Test-WindowsEnvironment {
    Write-Check "Windows version and architecture"

    $os = Get-CimInstance Win32_OperatingSystem

    if ([int]$os.ProductType -ne 1) {
        throw "Docker Desktop is not supported by this launcher on Windows Server."
    }

    if ($os.OSArchitecture -notmatch "64") {
        throw "A 64-bit Windows installation is required."
    }

    $build = [int]$os.BuildNumber

    if ($build -lt 22000) {
        if ($build -lt 19045) {
            throw "Docker Desktop requires Windows 10 build 19045 or newer."
        }
    } elseif ($build -lt 22631) {
        throw "Docker Desktop requires Windows 11 build 22631 or newer."
    }

    Write-Ok "$($os.Caption), build $build, $($os.OSArchitecture)"

    Write-Check "System memory"

    $totalGb = [math]::Round(
        ($os.TotalVisibleMemorySize / 1MB),
        2
    )

    $availableGb = [math]::Round(
        ($os.FreePhysicalMemory / 1MB),
        2
    )

    if ($totalGb -lt 8) {
        throw "At least 8 GB of RAM is required. Detected: $totalGb GB."
    }

    Write-Ok "RAM: $totalGb GB total, $availableGb GB available"

    if ($availableGb -lt 2) {
        Write-WarnMessage "Less than 2 GB of RAM is currently available. Docker, PostgreSQL, RabbitMQ and Java may be unstable."
    }

    if (Test-PendingWindowsReboot) {
        Write-WarnMessage "Windows reports a pending reboot from another installation or update."
    }
}

function Get-DotEnvValue {
    param([string]$Name)

    if (-not (Test-Path -LiteralPath $EnvFile)) {
        return $null
    }

    $pattern = "^\s*" + [regex]::Escape($Name) + "\s*=\s*(.*?)\s*$"

    foreach ($line in Get-Content -LiteralPath $EnvFile) {
        if ($line -match "^\s*#") {
            continue
        }

        if ($line -match $pattern) {
            return $Matches[1].Trim().Trim('"').Trim("'")
        }
    }

    return $null
}

function Get-IguanaDatabaseMode {
    $mode = [Environment]::GetEnvironmentVariable(
        "IGUANA_BOOTSTRAP_DB_MODE"
    )

    if ([string]::IsNullOrWhiteSpace($mode)) {
        $mode = [Environment]::GetEnvironmentVariable("APP_DB_MODE")
    }

    if ([string]::IsNullOrWhiteSpace($mode)) {
        $mode = Get-DotEnvValue "IGUANA_BOOTSTRAP_DB_MODE"
    }

    if ([string]::IsNullOrWhiteSpace($mode)) {
        $mode = Get-DotEnvValue "APP_DB_MODE"
    }

    if ([string]::IsNullOrWhiteSpace($mode)) {
        return "postgresql"
    }

    if ($mode.Trim().ToLowerInvariant() -eq "sqlite") {
        return "sqlite"
    }

    return "postgresql"
}

function Test-OptionalFeatureEnabled {
    param([string]$FeatureName)

    $escaped = $FeatureName.Replace("'", "''")

    $feature = Get-CimInstance `
        Win32_OptionalFeature `
        -Filter "Name='$escaped'" `
        -ErrorAction SilentlyContinue

    if (-not $feature) {
        return $false
    }

    return ([int]$feature.InstallState -eq 1)
}

function Test-WslRuntimeInstalled {
    if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
        return $false
    }

    # Do not use localized output as the installation test.
    # A successful `wsl --status` is the primary indicator that the WSL
    # runtime is installed and operational enough for Docker Desktop.
    try {
        & wsl.exe --status *> $null
        return ($LASTEXITCODE -eq 0)
    } catch {
        return $false
    }
}

function Get-WslVersion {
    if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) {
        return $null
    }

    try {
        $output = (& wsl.exe --version 2>&1 | Out-String)
        $exitCode = $LASTEXITCODE
    } catch {
        return $null
    }

    if ($exitCode -ne 0 -or [string]::IsNullOrWhiteSpace($output)) {
        return $null
    }

    # Prefer the line that contains WSL, but do not depend on English text.
    # The ASCII token "WSL" is present in localized output such as
    # "Версия WSL: 2.7.11.0".
    $lines = $output -split "\r?\n"
    $candidateLine = $lines |
        Where-Object { $_ -match "(?i)\bWSL\b" } |
        Select-Object -First 1

    if (-not $candidateLine) {
        $candidateLine = $output
    }

    if ($candidateLine -match "(\d+\.\d+\.\d+(?:\.\d+)?)") {
        try {
            return [version]$Matches[1]
        } catch {
            return $null
        }
    }

    return $null
}

function Ensure-Wsl {
    Write-Check "WSL 2 Windows features"

    $wslFeature = Test-OptionalFeatureEnabled `
        "Microsoft-Windows-Subsystem-Linux"

    $vmFeature = Test-OptionalFeatureEnabled `
        "VirtualMachinePlatform"

    if (-not $wslFeature -or -not $vmFeature) {
        Write-Host ""
        Write-WarnMessage "Docker Desktop requires WSL 2 Windows features."
        Write-WarnMessage "The following Windows components will be enabled:"
        Write-WarnMessage " - Windows Subsystem for Linux"
        Write-WarnMessage " - Virtual Machine Platform"
        Write-WarnMessage "A Windows restart will be required."
        Write-Host ""

        if (-not (Confirm-IguanaAction "Enable the required Windows features now?")) {
            Write-ErrorMessage "Environment setup was cancelled."
            exit 20
        }

        Invoke-ElevatedAction "EnableWslFeatures"
        Request-Reboot
    }

    Write-Ok "Required WSL Windows features are enabled"

    Write-Check "WSL runtime"

    $runtimeInstalled = Test-WslRuntimeInstalled

    if (-not $runtimeInstalled) {
        Write-Host ""
        Write-WarnMessage "The WSL runtime is not installed or cannot be started."
        Write-WarnMessage "WSL will be installed without a user Linux distribution."
        Write-WarnMessage "A Windows restart may be required."
        Write-Host ""

        if (-not (Confirm-IguanaAction "Install WSL now?")) {
            Write-ErrorMessage "WSL installation was cancelled."
            exit 20
        }

        Invoke-ElevatedAction "InstallWsl"

        # Re-check before requesting a reboot. If WSL is already usable,
        # do not force an unnecessary reinstall/reboot cycle.
        $runtimeInstalled = Test-WslRuntimeInstalled

        if (-not $runtimeInstalled) {
            Request-Reboot
        }
    }

    Write-Ok "WSL runtime is installed"

    $version = Get-WslVersion

    if ($version) {
        Write-Ok "WSL version: $version"

        if ($version -lt [version]"2.1.5.0") {
            Write-Host ""
            Write-WarnMessage "WSL $version is too old for current Docker Desktop."
            Write-WarnMessage "WSL 2.1.5 or newer is required."
            Write-Host ""

            if (-not (Confirm-IguanaAction "Update WSL now?")) {
                Write-ErrorMessage "WSL update was cancelled."
                exit 20
            }

            Invoke-ElevatedAction "UpdateWsl"

            $updatedVersion = Get-WslVersion

            if (-not $updatedVersion) {
                Write-WarnMessage "WSL was updated, but its version could not be detected."
            } elseif ($updatedVersion -lt [version]"2.1.5.0") {
                Request-Reboot
            } else {
                $version = $updatedVersion
                Write-Ok "WSL updated to $version"
            }
        }
    } else {
        # Important: failure to parse localized `wsl --version` output must
        # never be interpreted as "WSL is not installed".
        Write-WarnMessage "WSL is installed, but its version could not be parsed."
        Write-WarnMessage "Skipping automatic WSL update check."
    }

    & wsl.exe --set-default-version 2 *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set WSL 2 as the default WSL version."
    }

    Write-Ok "WSL default version is 2"
}

function Ensure-Hypervisor {
    Write-Check "Hardware virtualization / Windows hypervisor"

    $computerSystem = Get-CimInstance Win32_ComputerSystem

    if ($computerSystem.HypervisorPresent) {
        Write-Ok "Windows hypervisor is running"
        return
    }

    $cpu = Get-CimInstance Win32_Processor |
        Select-Object -First 1

    if (-not $cpu.VirtualizationFirmwareEnabled) {
        throw @"
Hardware virtualization is disabled in BIOS/UEFI.

Enable Intel Virtualization Technology / VT-x or AMD SVM / AMD-V,
save BIOS settings, reboot Windows, and run Iguana again.
"@
    }

    $bcdOutput = (
        & "$env:SystemRoot\System32\bcdedit.exe" `
            /enum `
            "{current}" `
            2>&1 |
        Out-String
    )

    if ($bcdOutput -match "(?im)hypervisorlaunchtype\s+Off") {
        Write-Host ""
        Write-WarnMessage "Windows hypervisor startup is disabled in the boot configuration."
        Write-WarnMessage "The setting hypervisorlaunchtype will be changed to Auto."
        Write-WarnMessage "A Windows restart will be required."
        Write-Host ""

        if (-not (Confirm-IguanaAction "Enable automatic hypervisor startup now?")) {
            Write-ErrorMessage "Hypervisor configuration was cancelled."
            exit 20
        }

        Invoke-ElevatedAction "EnableHypervisor"
        Request-Reboot
    }

    Write-WarnMessage "Hardware virtualization is available, but the Windows hypervisor is not currently running."
    Request-Reboot
}

function Ensure-LanmanServer {
    Write-Check "Windows Server service (LanmanServer)"

    $service = Get-CimInstance `
        Win32_Service `
        -Filter "Name='LanmanServer'" `
        -ErrorAction SilentlyContinue

    if (-not $service) {
        throw "The Windows LanmanServer service was not found."
    }

    if ($service.StartMode -eq "Auto" -and $service.State -eq "Running") {
        Write-Ok "LanmanServer is Automatic and Running"
        return
    }

    Write-Host ""
    Write-WarnMessage "Docker Desktop requires the Windows Server service (LanmanServer) to be enabled."
    Write-WarnMessage "LanmanServer will be set to Automatic and started."
    Write-Host ""

    if (-not (Confirm-IguanaAction "Configure LanmanServer now?")) {
        Write-ErrorMessage "LanmanServer configuration was cancelled."
        exit 20
    }

    Invoke-ElevatedAction "EnableLanmanServer"

    $service = Get-CimInstance `
        Win32_Service `
        -Filter "Name='LanmanServer'"

    if ($service.StartMode -ne "Auto" -or $service.State -ne "Running") {
        throw "LanmanServer could not be configured correctly."
    }

    Write-Ok "LanmanServer is Automatic and Running"
}

function Get-JavaVersionOutput {
    param([string]$JavaExe)

    $startInfo = New-Object System.Diagnostics.ProcessStartInfo
    $startInfo.FileName = $JavaExe
    $startInfo.Arguments = "-version"
    $startInfo.UseShellExecute = $false
    $startInfo.CreateNoWindow = $true
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $startInfo

    try {
        if (-not $process.Start()) {
            return $null
        }

        $stdout = $process.StandardOutput.ReadToEnd()
        $stderr = $process.StandardError.ReadToEnd()

        $process.WaitForExit()

        if ($process.ExitCode -ne 0) {
            return $null
        }

        return (($stdout + [Environment]::NewLine + $stderr).Trim())
    } catch {
        return $null
    } finally {
        $process.Dispose()
    }
}

function Get-JavaRuntime {
    $candidates = New-Object System.Collections.Generic.List[string]

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME_17)) {
        $candidates.Add(
            (Join-Path $env:JAVA_HOME_17 "bin\java.exe")
        )
    }

    $microsoftRoot = Join-Path $env:ProgramFiles "Microsoft"

    if (Test-Path -LiteralPath $microsoftRoot) {
        Get-ChildItem `
            -LiteralPath $microsoftRoot `
            -Directory `
            -Filter "jdk-17*" `
            -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        ForEach-Object {
            $candidates.Add(
                (Join-Path $_.FullName "bin\java.exe")
            )
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add(
            (Join-Path $env:JAVA_HOME "bin\java.exe")
        )
    }

    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue

    if ($javaCommand) {
        $candidates.Add($javaCommand.Source)
    }

    foreach ($candidate in ($candidates | Select-Object -Unique)) {
        if (-not (Test-Path -LiteralPath $candidate)) {
            continue
        }

        $output = Get-JavaVersionOutput -JavaExe $candidate

        if ([string]::IsNullOrWhiteSpace($output)) {
            continue
        }

        if ($output -match 'version\s+"(?<version>\d+(?:\.\d+)*)') {
            $versionText = $Matches["version"]
            $major = [int](($versionText -split "\.")[0])

            return [pscustomobject]@{
                Exe     = $candidate
                Version = $versionText
                Major   = $major
            }
        }
    }

    return $null
}

function Ensure-Java {
    Write-Check "JDK 17+"

    $java = Get-JavaRuntime

    if ($java -and $java.Major -ge 17) {
        Write-Ok "Java $($java.Version)"

        if ($java.Major -ne 17) {
            Write-WarnMessage "Iguana is primarily tested on JDK 17."
        }

        return
    }

    Write-Host ""
    Write-WarnMessage "JDK 17 or newer was not found."
    Write-WarnMessage "Microsoft Build of OpenJDK 17 will be installed with WinGet."
    Write-Host ""

    if (-not (Confirm-IguanaAction "Install JDK 17 now?")) {
        Write-ErrorMessage "JDK installation was cancelled."
        exit 20
    }

    $winget = Get-Command winget.exe -ErrorAction SilentlyContinue

    if (-not $winget) {
        throw "WinGet was not found. JDK 17 cannot be installed automatically."
    }

    & $winget.Source `
        install `
        --exact `
        --id Microsoft.OpenJDK.17 `
        --accept-source-agreements `
        --accept-package-agreements `
        --silent `
        --disable-interactivity

    $wingetExitCode = $LASTEXITCODE

    if ($wingetExitCode -eq 3010) {
        Request-Reboot
    }

    if ($wingetExitCode -ne 0) {
        throw "WinGet failed to install Microsoft OpenJDK 17. Exit code: $wingetExitCode"
    }

    Refresh-ProcessEnvironment

    $java = Get-JavaRuntime

    if (-not $java -or $java.Major -lt 17) {
        throw "JDK installation completed, but Java 17+ still cannot be found."
    }

    Write-Ok "JDK $($java.Version) installed"
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

    if (${env:ProgramFiles(x86)}) {
        $candidates += (
            Join-Path ${env:ProgramFiles(x86)} "Docker\Docker\resources\bin\docker.exe"
        )
    }

    foreach ($candidate in $candidates) {
        if (
            -not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate)
        ) {
            return $candidate
        }
    }

    return $null
}

function Get-DockerDesktopPath {
    $candidates = @(
        (Join-Path $env:ProgramFiles "Docker\Docker\Docker Desktop.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\DockerDesktop\Docker Desktop.exe"),
        (Join-Path $env:LOCALAPPDATA "Programs\Docker\Docker\Docker Desktop.exe")
    )

    if (${env:ProgramFiles(x86)}) {
        $candidates += (
            Join-Path ${env:ProgramFiles(x86)} "Docker\Docker\Docker Desktop.exe"
        )
    }

    foreach ($candidate in $candidates) {
        if (
            -not [string]::IsNullOrWhiteSpace($candidate) -and
            (Test-Path -LiteralPath $candidate)
        ) {
            return $candidate
        }
    }

    return $null
}

function Test-DockerEngine {
    param([string]$Docker)

    & $Docker info *> $null
    return ($LASTEXITCODE -eq 0)
}

function Wait-DockerEngine {
    param(
        [string]$Docker,
        [int]$TimeoutSeconds
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        if (Test-DockerEngine -Docker $Docker) {
            return $true
        }

        Start-Sleep -Seconds 3
    }

    return $false
}

function Ensure-Docker {
    Write-Check "Docker Desktop"

    $docker = Get-DockerCommandPath
    $desktop = Get-DockerDesktopPath

    if (-not $docker -or -not $desktop) {
        Write-Host ""
        Write-WarnMessage "Docker Desktop is not installed."
        Write-WarnMessage "Docker Desktop will be installed with WinGet."
        Write-WarnMessage "The WSL 2 backend will be used."
        Write-Host ""

        if (-not (Confirm-IguanaAction "Install Docker Desktop now?")) {
            Write-ErrorMessage "Docker Desktop installation was cancelled."
            exit 20
        }

        $winget = Get-Command winget.exe -ErrorAction SilentlyContinue

        if (-not $winget) {
            throw "WinGet was not found. Docker Desktop cannot be installed automatically."
        }

        & $winget.Source `
            install `
            --exact `
            --id Docker.DockerDesktop `
            --accept-source-agreements `
            --accept-package-agreements `
            --silent `
            --disable-interactivity

        $wingetExitCode = $LASTEXITCODE

        if ($wingetExitCode -eq 3010) {
            Request-Reboot
        }

        if ($wingetExitCode -ne 0) {
            throw "WinGet failed to install Docker Desktop. Exit code: $wingetExitCode"
        }

        Refresh-ProcessEnvironment

        $docker = Get-DockerCommandPath
        $desktop = Get-DockerDesktopPath

        if (-not $docker -or -not $desktop) {
            throw "Docker Desktop installation finished, but docker.exe or Docker Desktop.exe cannot be found."
        }

        Write-Ok "Docker Desktop installed"
    } else {
        Write-Ok "Docker Desktop is installed"
    }

    Write-Check "Docker Engine"

    if (Test-DockerEngine -Docker $docker) {
        Write-Ok "Docker Engine is already running"
    } else {
        Write-InfoMessage "Docker Desktop is installed, but Docker Engine is stopped."
        Write-InfoMessage "Starting Docker Desktop..."

        $desktopStartSucceeded = $false

        try {
            & $docker desktop start --timeout $DockerTimeoutSeconds

            if ($LASTEXITCODE -eq 0) {
                $desktopStartSucceeded = $true
            }
        } catch {
            $desktopStartSucceeded = $false
        }

        if (-not $desktopStartSucceeded) {
            Write-WarnMessage "Docker Desktop CLI start failed or is unavailable. Falling back to Docker Desktop.exe."

            Start-Process `
                -FilePath $desktop `
                -WindowStyle Hidden |
            Out-Null
        }

        if (-not (Wait-DockerEngine `
            -Docker $docker `
            -TimeoutSeconds $DockerTimeoutSeconds)) {

            throw @"
Docker Desktop was started, but Docker Engine did not become available within $DockerTimeoutSeconds seconds.

Run these commands for diagnostics:
    docker desktop status
    docker desktop diagnose
    docker desktop logs --since 10m
"@
        }

        Write-Ok "Docker Engine is running"
    }

    Write-Check "Docker Compose"

    & $docker compose version *> $null

    if ($LASTEXITCODE -ne 0) {
        throw "Docker Compose is not available."
    }

    Write-Ok "Docker Compose is available"

    return $docker
}

function Invoke-IguanaBootstrap {
    $runBootstrap = $false

    if (-not (Test-Path -LiteralPath $EnvFile)) {
        $runBootstrap = $true
    }

    if (
        Get-BoolEnvironmentValue `
            -Name "IGUANA_RUN_BOOTSTRAP" `
            -Default $false
    ) {
        $runBootstrap = $true
    }

    if ($ForceBootstrap) {
        $runBootstrap = $true
    }

    if (-not $runBootstrap) {
        Write-Ok ".env already exists"
        return
    }

    if (-not (Test-Path -LiteralPath $BootstrapScript)) {
        throw "Bootstrap script was not found: $BootstrapScript"
    }

    Write-InfoMessage "Running first-run bootstrap..."

    $oldDockerInstall = [Environment]::GetEnvironmentVariable(
        "IGUANA_BOOTSTRAP_INSTALL_DOCKER"
    )

    $oldBootstrapMode = [Environment]::GetEnvironmentVariable(
        "IGUANA_BOOTSTRAP_DB_MODE"
    )

    try {
        # Software installation belongs to preflight and must never happen
        # silently inside bootstrap.
        $env:IGUANA_BOOTSTRAP_INSTALL_DOCKER = "false"

        if ([string]::IsNullOrWhiteSpace($oldBootstrapMode)) {
            $env:IGUANA_BOOTSTRAP_DB_MODE = Get-IguanaDatabaseMode
        }

        $powershellExe = "$env:SystemRoot\System32\WindowsPowerShell\v1.0\powershell.exe"

        if (-not (Test-Path -LiteralPath $powershellExe)) {
            $powershellExe = "powershell.exe"
        }

        $bootstrapArgs = @(
            "-NoLogo",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            $BootstrapScript
        )

        if ($ForceBootstrap) {
            $bootstrapArgs += "-Force"
        }

        & $powershellExe @bootstrapArgs
        $bootstrapExitCode = $LASTEXITCODE

        if ($bootstrapExitCode -ne 0) {
            throw "First-run bootstrap failed with exit code $bootstrapExitCode."
        }
    } finally {
        if ($null -eq $oldDockerInstall) {
            Remove-Item `
                Env:\IGUANA_BOOTSTRAP_INSTALL_DOCKER `
                -ErrorAction SilentlyContinue
        } else {
            $env:IGUANA_BOOTSTRAP_INSTALL_DOCKER = $oldDockerInstall
        }

        if ($null -eq $oldBootstrapMode) {
            Remove-Item `
                Env:\IGUANA_BOOTSTRAP_DB_MODE `
                -ErrorAction SilentlyContinue
        } else {
            $env:IGUANA_BOOTSTRAP_DB_MODE = $oldBootstrapMode
        }
    }

    if (-not (Test-Path -LiteralPath $EnvFile)) {
        throw "Bootstrap finished, but .env was not created."
    }

    Write-Ok "First-run bootstrap completed"
}

function Get-ContainerHealth {
    param(
        [string]$Docker,
        [string]$ContainerName
    )

    $status = (
        & $Docker `
            inspect `
            --format `
            "{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}" `
            $ContainerName `
            2>$null |
        Select-Object -First 1
    )

    if ([string]::IsNullOrWhiteSpace($status)) {
        return "missing"
    }

    return $status.Trim()
}

function Wait-IguanaContainers {
    param([string]$Docker)

    $containers = @(
        "iguana-postgres",
        "iguana-rabbitmq"
    )

    $deadline = (Get-Date).AddSeconds(
        $ContainerTimeoutSeconds
    )

    while ((Get-Date) -lt $deadline) {
        $allHealthy = $true

        foreach ($container in $containers) {
            $status = Get-ContainerHealth `
                -Docker $Docker `
                -ContainerName $container

            switch ($status) {
                "healthy" {
                    # Ready.
                }

                "starting" {
                    $allHealthy = $false
                }

                "running" {
                    $allHealthy = $false
                }

                "missing" {
                    $allHealthy = $false
                }

                "unhealthy" {
                    Write-ErrorMessage "$container is unhealthy."
                    & $Docker logs --tail 80 $container
                    throw "$container failed its healthcheck."
                }

                "exited" {
                    Write-ErrorMessage "$container exited."
                    & $Docker logs --tail 80 $container
                    throw "$container exited before becoming healthy."
                }

                "dead" {
                    throw "$container is in the dead state."
                }

                default {
                    $allHealthy = $false
                }
            }
        }

        if ($allHealthy) {
            return
        }

        Start-Sleep -Seconds 3
    }

    Write-ErrorMessage "Containers did not become healthy within $ContainerTimeoutSeconds seconds."

    & $Docker compose `
        --project-directory $RepoRoot `
        -f $ComposeFile `
        ps

    throw "Timed out waiting for PostgreSQL and RabbitMQ."
}

function Ensure-IguanaContainers {
    param([string]$Docker)

    if (-not (Test-Path -LiteralPath $ComposeFile)) {
        throw "Docker Compose file was not found: $ComposeFile"
    }

    Write-Check "PostgreSQL and RabbitMQ"

    & $Docker compose `
        --project-directory $RepoRoot `
        -f $ComposeFile `
        up `
        -d `
        postgres `
        rabbitmq

    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed to start PostgreSQL and RabbitMQ."
    }

    Write-InfoMessage "Waiting for container healthchecks..."

    Wait-IguanaContainers -Docker $Docker

    Write-Ok "PostgreSQL is healthy"
    Write-Ok "RabbitMQ is healthy"
}

try {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host " Iguana Windows preflight"
    Write-Host "============================================================"
    Write-Host ""

    Test-WindowsEnvironment

    $dbMode = Get-IguanaDatabaseMode
    Write-InfoMessage "Database mode: $dbMode"

    $docker = $null

    if ($dbMode -eq "postgresql") {
        Ensure-Wsl
        Ensure-Hypervisor
        Ensure-LanmanServer
        $docker = Ensure-Docker
    }

    Ensure-Java

    Invoke-IguanaBootstrap

    # Bootstrap may have created a new .env.
    $dbMode = Get-IguanaDatabaseMode
    Write-InfoMessage "Effective database mode: $dbMode"

    if ($dbMode -eq "postgresql") {
        if (-not $docker) {
            Ensure-Wsl
            Ensure-Hypervisor
            Ensure-LanmanServer
            $docker = Ensure-Docker
        }

        Ensure-IguanaContainers -Docker $docker
    } else {
        Write-WarnMessage "Explicit SQLite compatibility mode is active."
    }

    Write-Host ""
    Write-Ok "Iguana runtime environment is ready."
    Write-Host ""

    exit 0
} catch {
    Write-Host ""
    Write-ErrorMessage $_.Exception.Message
    Write-Host ""
    exit 1
}
