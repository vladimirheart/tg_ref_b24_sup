function Get-IguanaDotEnvValue {
    param([string]$Path, [string]$Name)

    if (-not (Test-Path -LiteralPath $Path)) {
        return ""
    }
    $result = ""
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) { continue }
        if ($trimmed.Substring(0, $separatorIndex).Trim() -ne $Name) { continue }
        $result = $trimmed.Substring($separatorIndex + 1).Trim().Trim('"').Trim("'")
    }
    return $result
}

function Resolve-IguanaSharedConfigDirectory {
    param([string]$RepoRoot)

    $configured = [Environment]::GetEnvironmentVariable("IGUANA_SHARED_CONFIG_DIR", "Process")
    if ([string]::IsNullOrWhiteSpace($configured)) {
        $configured = Get-IguanaDotEnvValue -Path (Join-Path $RepoRoot ".env") -Name "IGUANA_SHARED_CONFIG_DIR"
    }
    if ([string]::IsNullOrWhiteSpace($configured)) {
        $configured = "config/shared"
    }
    if ([System.IO.Path]::IsPathRooted($configured)) {
        return [System.IO.Path]::GetFullPath($configured)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $configured))
}

function Import-IguanaBackupSettings {
    param([string]$RepoRoot)

    $sharedConfigDirectory = Resolve-IguanaSharedConfigDirectory -RepoRoot $RepoRoot
    $backupSettingsPath = Join-Path $sharedConfigDirectory "backup.properties"
    if (-not (Test-Path -LiteralPath $backupSettingsPath -PathType Leaf)) {
        return $null
    }

    $allowed = @(
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

    foreach ($line in Get-Content -LiteralPath $backupSettingsPath -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#") -or $trimmed.StartsWith("!")) { continue }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) { continue }

        $name = $trimmed.Substring(0, $separatorIndex).Trim()
        if ($allowed -notcontains $name) { continue }

        $existing = [Environment]::GetEnvironmentVariable($name, "Process")
        if (-not [string]::IsNullOrWhiteSpace($existing)) { continue }

        $value = $trimmed.Substring($separatorIndex + 1).Trim()
        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }

    Write-Host "[INFO] Backup policy loaded from $backupSettingsPath"
    return $backupSettingsPath
}
