param(
    [ValidateSet("backup", "restore", "full")]
    [string]$Action = "backup",
    [switch]$ValidateOnly,
    [switch]$AllowLocalDestination
)

$ErrorActionPreference = "Stop"

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root for docker-production-backup.ps1."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Read-DotEnvFile {
    param([string]$Path)
    $result = @{}
    if (-not (Test-Path -LiteralPath $Path)) { return $result }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $idx = $trimmed.IndexOf("=")
        if ($idx -lt 1) { continue }
        $result[$trimmed.Substring(0, $idx).Trim()] = $trimmed.Substring($idx + 1).Trim()
    }
    return $result
}

function Get-SettingValue {
    param([hashtable]$DotEnv, [string]$Name)
    $envValue = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($envValue)) { return $envValue.Trim() }
    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace([string]$DotEnv[$Name])) {
        return [string]$DotEnv[$Name]
    }
    return ""
}

function Resolve-BackupDestination {
    param([string]$RepoRoot, [hashtable]$DotEnv, [bool]$AllowLocal)

    $raw = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_DESTINATION_DIR"
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "IGUANA_BACKUP_DESTINATION_DIR is required. Point it to a mounted NAS/SMB/NFS/off-host filesystem path."
    }

    if ([System.IO.Path]::IsPathRooted($raw)) {
        $resolved = [System.IO.Path]::GetFullPath($raw)
    } else {
        if (-not $AllowLocal) {
            throw "IGUANA_BACKUP_DESTINATION_DIR must be an absolute off-host path for production. Use -AllowLocalDestination only for smoke/testing."
        }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $raw))
    }

    $repoPrefix = $RepoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $AllowLocal -and $resolved.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Backup destination is inside the repository failure domain: $resolved. Use a mounted off-host path."
    }

    if (-not (Test-Path -LiteralPath $resolved)) {
        if ($AllowLocal) {
            New-Item -ItemType Directory -Force -Path $resolved | Out-Null
        } else {
            throw "Backup destination does not exist or is not mounted: $resolved"
        }
    }

    $probe = Join-Path $resolved (".iguana-write-probe-" + [Guid]::NewGuid().ToString("N"))
    try {
        [System.IO.File]::WriteAllText($probe, "probe")
    } finally {
        Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue
    }

    return $resolved
}

function Invoke-Compose {
    param([string]$Docker, [string[]]$BaseArguments, [string[]]$Arguments)
    & $Docker @BaseArguments @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose command failed with exit code ${LASTEXITCODE}: $($Arguments -join ' ')"
    }
}

$repoRoot = Get-RepoRoot
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$backupCompose = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"

foreach ($path in @($baseCompose, $backupCompose)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Compose file is missing: $path" }
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) { throw "Docker is not installed or not available in PATH." }
& $dockerCommand.Source compose version *> $null
if ($LASTEXITCODE -ne 0) { throw "docker compose is unavailable." }

$dotEnv = Read-DotEnvFile -Path $dotEnvPath
$destination = Resolve-BackupDestination -RepoRoot $repoRoot -DotEnv $dotEnv -AllowLocal:$AllowLocalDestination
$previousDestination = [Environment]::GetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $destination)

try {
    $baseArguments = @("compose", "--project-directory", $repoRoot)
    if (Test-Path -LiteralPath $dotEnvPath) { $baseArguments += @("--env-file", $dotEnvPath) }
    $baseArguments += @("-f", $baseCompose, "-f", $backupCompose, "--profile", "backup")

    Write-Host "[INFO] Backup destination: $destination"
    Write-Host "[INFO] Action: $Action"

    Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("config", "-q")
    if ($ValidateOnly) {
        Write-Host "[GREEN] Backup compose validation succeeded."
        exit 0
    }

    if ($Action -eq "backup" -or $Action -eq "full") {
        Write-Host "[BACKUP] PostgreSQL"
        Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("run", "--rm", "postgres-backup")
        Write-Host "[BACKUP] MinIO"
        Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("run", "--rm", "--build", "minio-backup")
    }

    if ($Action -eq "restore" -or $Action -eq "full") {
        Write-Host "[RESTORE] Starting isolated restore targets"
        Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("up", "-d", "postgres-restore-target", "minio-restore-target")
        $restoreCleanupExitCode = 0
        try {
            Write-Host "[RESTORE] PostgreSQL rehearsal"
            Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("run", "--rm", "postgres-restore-rehearsal")
            Write-Host "[RESTORE] MinIO rehearsal"
            Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("run", "--rm", "--build", "minio-restore-rehearsal")
        } finally {
            Write-Host "[RESTORE] Removing isolated restore targets"
            $savedErrorActionPreference = $ErrorActionPreference
            try {
                $ErrorActionPreference = "Continue"
                & $dockerCommand.Source @baseArguments rm -s -f postgres-restore-target minio-restore-target *> $null
                $restoreCleanupExitCode = $LASTEXITCODE
            } finally {
                $ErrorActionPreference = $savedErrorActionPreference
            }
        }
        if ($restoreCleanupExitCode -ne 0) {
            throw "Unable to remove isolated restore targets; docker compose rm exit code: $restoreCleanupExitCode"
        }
    }

    Write-Host "[GREEN] Iguana production backup action completed: $Action"
} finally {
    [Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $previousDestination)
}
