param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile
)

$ErrorActionPreference = "Stop"

function Write-Utf8NoBomFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($Path, $Content, $encoding)
}

function New-RandomHexToken {
    param(
        [int]$BytesLength = 32
    )

    $bytes = New-Object byte[] $BytesLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return ([System.BitConverter]::ToString($bytes)).Replace("-", "").ToLowerInvariant()
}

function New-RandomBase64Token {
    param(
        [int]$BytesLength = 32
    )

    $bytes = New-Object byte[] $BytesLength
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    return [Convert]::ToBase64String($bytes)
}

function Test-ValidMonitoringMasterKeyPayload {
    param(
        [string]$EncodedValue
    )

    if ([string]::IsNullOrWhiteSpace($EncodedValue)) {
        return $false
    }

    try {
        $decoded = [Convert]::FromBase64String($EncodedValue.Trim())
        return $decoded.Length -in @(16, 24, 32)
    } catch {
        return $false
    }
}

function Resolve-MonitoringMasterKeyValue {
    param(
        [string]$EnvFilePath
    )

    $repoRoot = Split-Path -Parent $EnvFilePath
    $legacyKeyPath = Join-Path $repoRoot "config\shared\monitoring-credentials.key"
    if (Test-Path -LiteralPath $legacyKeyPath) {
        $legacyEncoded = [System.IO.File]::ReadAllText($legacyKeyPath, [System.Text.Encoding]::UTF8).Trim()
        if (-not [string]::IsNullOrWhiteSpace($legacyEncoded)) {
            if (-not (Test-ValidMonitoringMasterKeyPayload -EncodedValue $legacyEncoded)) {
                throw "Legacy monitoring credentials key is not valid Base64 AES material: $legacyKeyPath"
            }
            return "base64:$legacyEncoded"
        }
    }

    return "base64:$(New-RandomBase64Token)"
}

function Read-DotEnvState {
    param(
        [string]$Path
    )

    $lines = New-Object 'System.Collections.Generic.List[string]'
    $settings = @{}
    $indices = @{}

    if (-not (Test-Path -LiteralPath $Path)) {
        return [pscustomobject]@{
            Lines = $lines
            Settings = $settings
            Indices = $indices
        }
    }

    $index = 0
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $lines.Add($line)

        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $trimmed = $line.Trim()
            if (-not $trimmed.StartsWith("#")) {
                $separatorIndex = $trimmed.IndexOf("=")
                if ($separatorIndex -gt 0) {
                    $name = $trimmed.Substring(0, $separatorIndex).Trim()
                    $value = $trimmed.Substring($separatorIndex + 1)
                    if (-not $settings.ContainsKey($name)) {
                        $settings[$name] = $value
                        $indices[$name] = $index
                    }
                }
            }
        }

        $index++
    }

    return [pscustomobject]@{
        Lines = $lines
        Settings = $settings
        Indices = $indices
    }
}

function Get-SettingValue {
    param(
        [hashtable]$Settings,
        [string]$Name
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        return $fromEnvironment.Trim()
    }

    if ($Settings.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace($Settings[$Name])) {
        return ([string]$Settings[$Name]).Trim()
    }

    return ""
}

function Test-TruthySetting {
    param(
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $false
    }

    switch ($Value.Trim().ToLowerInvariant()) {
        "1" { return $true }
        "true" { return $true }
        "yes" { return $true }
        "on" { return $true }
        default { return $false }
    }
}

function Test-LocalBootstrapContour {
    param(
        [hashtable]$Settings
    )

    $bootstrapMode = (Get-SettingValue -Settings $Settings -Name "IGUANA_BOOTSTRAP_DB_MODE").ToLowerInvariant()
    $databaseMode = (Get-SettingValue -Settings $Settings -Name "APP_DB_MODE").ToLowerInvariant()
    $coordinationMode = (Get-SettingValue -Settings $Settings -Name "APP_COORDINATION_MODE").ToLowerInvariant()
    $objectStorageMode = (Get-SettingValue -Settings $Settings -Name "APP_STORAGE_OBJECT_MODE").ToLowerInvariant()
    $coordinationRequired = Test-TruthySetting -Value (Get-SettingValue -Settings $Settings -Name "APP_COORDINATION_REQUIRED_FOR_POSTGRESQL")
    $storageRequired = Test-TruthySetting -Value (Get-SettingValue -Settings $Settings -Name "APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL")

    return $bootstrapMode -eq "postgresql" `
        -and $databaseMode -eq "postgresql" `
        -and $coordinationMode -ne "redis" `
        -and $objectStorageMode -ne "s3" `
        -and -not $coordinationRequired `
        -and -not $storageRequired
}

function Test-NeedsGeneratedSecret {
    param(
        [string]$Value,
        [string[]]$DisallowedValues
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $true
    }

    $normalized = $Value.Trim()
    foreach ($candidate in $DisallowedValues) {
        if ($normalized -eq $candidate) {
            return $true
        }
    }

    return $false
}

$state = Read-DotEnvState -Path $EnvFile

if (-not (Test-LocalBootstrapContour -Settings $state.Settings)) {
    exit 0
}

$secretRules = @(
    @{
        Name = "APP_INTERNAL_BOT_API_TOKEN"
        Disallowed = @("change-me", "iguana-internal-bot-token")
        Generator = { New-RandomHexToken }
    },
    @{
        Name = "APP_SECURITY_REMEMBER_ME_KEY"
        Disallowed = @("change-me", "iguana-panel-remember-me")
        Generator = { New-RandomHexToken }
    },
    @{
        Name = "MONITORING_CREDENTIALS_MASTER_KEY"
        Disallowed = @("change-me", "iguana-monitoring-key")
        Generator = { Resolve-MonitoringMasterKeyValue -EnvFilePath $EnvFile }
    }
)

$pendingAdds = New-Object 'System.Collections.Generic.List[string]'
$mutations = New-Object 'System.Collections.Generic.List[string]'

foreach ($rule in $secretRules) {
    $name = [string]$rule.Name
    $environmentValue = [Environment]::GetEnvironmentVariable($name)
    if (-not [string]::IsNullOrWhiteSpace($environmentValue)) {
        continue
    }

    $effectiveValue = Get-SettingValue -Settings $state.Settings -Name $name
    if (-not (Test-NeedsGeneratedSecret -Value $effectiveValue -DisallowedValues $rule.Disallowed)) {
        continue
    }

    $newValue = & $rule.Generator
    $newLine = "$name=$newValue"

    if ($state.Indices.ContainsKey($name)) {
        $index = [int]$state.Indices[$name]
        $state.Lines[$index] = $newLine
        $mutations.Add("updated $name")
    } else {
        $pendingAdds.Add($newLine)
        $mutations.Add("added $name")
    }

    $state.Settings[$name] = $newValue
}

if ($mutations.Count -eq 0) {
    exit 0
}

if ($pendingAdds.Count -gt 0) {
    if ($state.Lines.Count -gt 0 -and -not [string]::IsNullOrWhiteSpace($state.Lines[$state.Lines.Count - 1])) {
        $state.Lines.Add("")
    }
    $state.Lines.Add("# Local bootstrap generated secrets")
    foreach ($line in $pendingAdds) {
        $state.Lines.Add($line)
    }
}

$content = ($state.Lines -join [Environment]::NewLine) + [Environment]::NewLine
Write-Utf8NoBomFile -Path $EnvFile -Content $content
Write-Host ("[INFO] Local bootstrap secret contract updated in {0}: {1}." -f $EnvFile, ($mutations -join ", "))
