param(
    [ValidateSet("backup", "restore", "full")]
    [string]$Action = "backup",
    [string]$Mode = "",
    [string]$Components = "",
    [string]$RestoreComponents = "",
    [switch]$ValidateOnly,
    [switch]$AllowLocalDestination
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Get-RepoRoot {
    if (-not $PSScriptRoot) { throw "Unable to resolve script root for docker-production-backup.ps1." }
    [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
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
    $result
}

function Get-SettingValue {
    param([hashtable]$DotEnv, [string]$Name)
    $envValue = [Environment]::GetEnvironmentVariable($Name, "Process")
    if (-not [string]::IsNullOrWhiteSpace($envValue)) { return $envValue.Trim() }
    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace([string]$DotEnv[$Name])) {
        return [string]$DotEnv[$Name]
    }
    ""
}

function Test-TruthySetting {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return $false }
    switch ($Value.Trim().ToLowerInvariant()) {
        "1" { $true }
        "true" { $true }
        "yes" { $true }
        "on" { $true }
        default { $false }
    }
}

function Normalize-Components {
    param([string]$Raw, [string]$Label)
    $allowed = @("postgres", "minio", "shared-config", "templates", "static-js", "static-css")
    $result = New-Object System.Collections.Generic.List[string]
    foreach ($item in ($Raw -split ",")) {
        $value = $item.Trim().ToLowerInvariant()
        if ([string]::IsNullOrWhiteSpace($value)) { continue }
        if ($allowed -notcontains $value) { throw "$Label contains unsupported component: $value" }
        if (-not $result.Contains($value)) { $result.Add($value) }
    }
    if ($result.Count -lt 1) { throw "$Label must contain at least one component." }
    ,$result.ToArray()
}

function Resolve-BackupMode {
    param([hashtable]$DotEnv, [string]$ExplicitMode)
    $value = $ExplicitMode
    if ([string]::IsNullOrWhiteSpace($value)) {
        $value = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_MANUAL_MODE"
    }
    if ([string]::IsNullOrWhiteSpace($value)) { $value = "critical" }
    $value = $value.Trim().ToLowerInvariant()
    if (@("critical", "full", "custom") -notcontains $value) {
        throw "Backup mode must be critical, full or custom."
    }
    $value
}

function Resolve-BackupComponents {
    param([hashtable]$DotEnv, [string]$ModeValue, [string]$ExplicitComponents)
    if ($ModeValue -eq "critical") {
        return @("postgres", "minio", "shared-config")
    }
    if ($ModeValue -eq "full") {
        return @("postgres", "minio", "shared-config", "templates", "static-js", "static-css")
    }
    $raw = $ExplicitComponents
    if ([string]::IsNullOrWhiteSpace($raw)) {
        $raw = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_CUSTOM_COMPONENTS"
    }
    Normalize-Components -Raw $raw -Label "Custom backup components"
}

function Resolve-RestoreComponents {
    param([hashtable]$DotEnv, [string]$ExplicitComponents)
    $raw = $ExplicitComponents
    if ([string]::IsNullOrWhiteSpace($raw)) {
        $raw = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_RESTORE_COMPONENTS"
    }
    if ([string]::IsNullOrWhiteSpace($raw)) { $raw = "postgres,minio,shared-config" }
    Normalize-Components -Raw $raw -Label "Restore components"
}

function Resolve-BackupDestination {
    param([string]$RepoRoot, [hashtable]$DotEnv, [bool]$AllowLocal)

    $raw = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_DESTINATION_DIR"
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "Backup destination is not configured. Use Settings -> Backup & recovery or a process environment override."
    }
    if (-not $AllowLocal) {
        $ack = Get-SettingValue -DotEnv $DotEnv -Name "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN"
        if (-not (Test-TruthySetting $ack)) {
            throw "Production backup requires external failure-domain acknowledgement in Settings -> Backup & recovery."
        }
    }

    if ([System.IO.Path]::IsPathRooted($raw)) {
        $resolved = [System.IO.Path]::GetFullPath($raw)
    } else {
        if (-not $AllowLocal) { throw "Backup destination must be an absolute off-host path for production." }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $raw))
    }

    $repoPrefix = $RepoRoot.TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    if (-not $AllowLocal -and $resolved.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Backup destination is inside the repository failure domain: $resolved"
    }

    if (-not (Test-Path -LiteralPath $resolved)) {
        if ($AllowLocal) { New-Item -ItemType Directory -Force -Path $resolved | Out-Null }
        else { throw "Backup destination does not exist or is not mounted: $resolved" }
    }

    $probe = Join-Path $resolved (".iguana-write-probe-" + [Guid]::NewGuid().ToString("N"))
    try { [System.IO.File]::WriteAllText($probe, "probe") }
    finally { Remove-Item -LiteralPath $probe -Force -ErrorAction SilentlyContinue }
    $resolved
}

function Invoke-Compose {
    param([string]$Docker, [string[]]$BaseArguments, [string[]]$Arguments)
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $Docker @BaseArguments @Arguments | Out-Host
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
    if ($code -ne 0) {
        throw "docker compose command failed with exit code ${code}: $($Arguments -join ' ')"
    }
}

function Contains-Component {
    param([string[]]$Items, [string]$Value)
    $Items -contains $Value
}

$repoRoot = Get-RepoRoot
$backupConfigLibrary = Join-Path $PSScriptRoot "lib\backup-config.ps1"
if (-not (Test-Path -LiteralPath $backupConfigLibrary)) { throw "Backup config library is missing: $backupConfigLibrary" }
. $backupConfigLibrary
Import-IguanaBackupSettings -RepoRoot $repoRoot | Out-Null

$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$backupCompose = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"
foreach ($path in @($baseCompose, $backupCompose)) {
    if (-not (Test-Path -LiteralPath $path)) { throw "Compose file is missing: $path" }
}

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) { throw "Docker is not installed or not available in PATH." }

$dotEnv = Read-DotEnvFile $dotEnvPath
$destination = Resolve-BackupDestination -RepoRoot $repoRoot -DotEnv $dotEnv -AllowLocal:$AllowLocalDestination
$modeValue = Resolve-BackupMode -DotEnv $dotEnv -ExplicitMode $Mode
$backupComponents = Resolve-BackupComponents -DotEnv $dotEnv -ModeValue $modeValue -ExplicitComponents $Components
$restoreItems = Resolve-RestoreComponents -DotEnv $dotEnv -ExplicitComponents $RestoreComponents
$fileComponents = @("shared-config", "templates", "static-js", "static-css")
$selectedFileBackup = @($backupComponents | Where-Object { $fileComponents -contains $_ })
$selectedFileRestore = @($restoreItems | Where-Object { $fileComponents -contains $_ })

$previous = @{}
foreach ($name in @("IGUANA_BACKUP_DESTINATION_DIR", "IGUANA_BACKUP_MODE", "IGUANA_BACKUP_COMPONENTS", "IGUANA_BACKUP_RESTORE_COMPONENTS")) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $destination, "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_MODE", $modeValue, "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_COMPONENTS", ($backupComponents -join ","), "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_RESTORE_COMPONENTS", ($restoreItems -join ","), "Process")

try {
    $baseArguments = @("compose", "--project-directory", $repoRoot)
    if (Test-Path -LiteralPath $dotEnvPath) { $baseArguments += @("--env-file", $dotEnvPath) }
    $baseArguments += @("-f", $baseCompose, "-f", $backupCompose, "--profile", "backup")

    Write-Host "[INFO] Backup destination: $destination"
    Write-Host "[INFO] Action: $Action"
    Write-Host "[INFO] Backup mode: $modeValue"
    Write-Host "[INFO] Backup components: $($backupComponents -join ',')"
    Write-Host "[INFO] Restore components: $($restoreItems -join ',')"

    Invoke-Compose -Docker $dockerCommand.Source -BaseArguments $baseArguments -Arguments @("config", "-q")
    if ($ValidateOnly) {
        Write-Host "[GREEN] Backup compose/policy validation succeeded."
        exit 0
    }

    if ($Action -eq "backup" -or $Action -eq "full") {
        if (Contains-Component $backupComponents "postgres") {
            Write-Host "[BACKUP] PostgreSQL tar.gz"
            Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "postgres-backup")
        }
        if (Contains-Component $backupComponents "minio") {
            Write-Host "[BACKUP] MinIO tar.gz"
            Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "--build", "minio-backup")
        }
        if ($selectedFileBackup.Count -gt 0) {
            [Environment]::SetEnvironmentVariable("IGUANA_BACKUP_COMPONENTS", ($selectedFileBackup -join ","), "Process")
            Write-Host "[BACKUP] Files tar.gz: $($selectedFileBackup -join ',')"
            Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "files-backup")
        }
    }

    if ($Action -eq "restore" -or $Action -eq "full") {
        $needsPostgresTarget = Contains-Component $restoreItems "postgres"
        $needsMinioTarget = Contains-Component $restoreItems "minio"
        $targets = @()
        if ($needsPostgresTarget) { $targets += "postgres-restore-target" }
        if ($needsMinioTarget) { $targets += "minio-restore-target" }

        if ($targets.Count -gt 0) {
            Write-Host "[RESTORE] Starting isolated targets: $($targets -join ',')"
            Invoke-Compose $dockerCommand.Source $baseArguments (@("up", "-d") + $targets)
        }

        $cleanupExit = 0
        try {
            if ($needsPostgresTarget) {
                Write-Host "[RESTORE] PostgreSQL tar.gz rehearsal"
                Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "postgres-restore-rehearsal")
            }
            if ($needsMinioTarget) {
                Write-Host "[RESTORE] MinIO tar.gz rehearsal"
                Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "--build", "minio-restore-rehearsal")
            }
            if ($selectedFileRestore.Count -gt 0) {
                [Environment]::SetEnvironmentVariable("IGUANA_BACKUP_RESTORE_COMPONENTS", ($selectedFileRestore -join ","), "Process")
                Write-Host "[RESTORE] File tar.gz rehearsal: $($selectedFileRestore -join ',')"
                Invoke-Compose $dockerCommand.Source $baseArguments @("run", "--rm", "files-restore-rehearsal")
            }
        } finally {
            if ($targets.Count -gt 0) {
                $saved = $ErrorActionPreference
                try {
                    $ErrorActionPreference = "Continue"
                    & $dockerCommand.Source @baseArguments rm -s -f @targets *> $null
                    $cleanupExit = $LASTEXITCODE
                } finally {
                    $ErrorActionPreference = $saved
                }
            }
        }
        if ($cleanupExit -ne 0) { throw "Unable to remove isolated restore targets; exit code: $cleanupExit" }
    }

    Write-Host "[GREEN] Iguana production backup action completed: $Action; mode=$modeValue"
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
    }
}
