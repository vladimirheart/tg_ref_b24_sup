param(
    [switch]$ValidateOnly
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

try {
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
} catch {
    # Best effort only.
}

function Get-RepoRoot {
    if (-not $PSScriptRoot) {
        throw "Unable to resolve script root."
    }
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $values
    }
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $trimmed = $line.Trim()
        if ($trimmed.StartsWith("#")) { continue }
        $separatorIndex = $trimmed.IndexOf("=")
        if ($separatorIndex -lt 1) { continue }
        $values[$trimmed.Substring(0, $separatorIndex).Trim()] = $trimmed.Substring($separatorIndex + 1).Trim()
    }
    return $values
}

function Get-SettingValue {
    param(
        [hashtable]$DotEnv,
        [string]$Name,
        [string]$DefaultValue = ""
    )

    $fromEnvironment = [Environment]::GetEnvironmentVariable($Name)
    if (-not [string]::IsNullOrWhiteSpace($fromEnvironment)) {
        return $fromEnvironment.Trim()
    }
    if ($DotEnv.ContainsKey($Name) -and -not [string]::IsNullOrWhiteSpace([string]$DotEnv[$Name])) {
        return ([string]$DotEnv[$Name]).Trim()
    }
    return $DefaultValue
}

function Ensure-DockerAvailable {
    $dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
    if (-not $dockerCommand) {
        throw "Docker is not installed or not available in PATH."
    }
    & $dockerCommand.Source compose version *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose is unavailable."
    }
    return $dockerCommand.Source
}

function Invoke-ComposeCapture {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$Arguments
    )

    $savedPreference = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Docker @ComposePrefix @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $savedPreference
    }
    return [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Assert-ComposeSuccess {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $result = Invoke-ComposeCapture -Docker $Docker -ComposePrefix $ComposePrefix -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Query-Postgres {
    param(
        [string]$Docker,
        [string[]]$ComposePrefix,
        [string]$User,
        [string]$Database,
        [string]$Sql
    )

    $result = Assert-ComposeSuccess `
        -Docker $Docker `
        -ComposePrefix $ComposePrefix `
        -Arguments @("exec", "-T", "postgres", "psql", "-U", $User, "-d", $Database, "-Atc", $Sql) `
        -ErrorMessage "PostgreSQL query failed"
    return (($result.Output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function ConvertFrom-HexUtf8 {
    param([string]$Hex)

    if ([string]::IsNullOrEmpty($Hex)) {
        return ""
    }
    if (($Hex.Length % 2) -ne 0 -or $Hex -notmatch '^[0-9A-Fa-f]+$') {
        throw "Invalid UTF-8 hex payload received from PostgreSQL."
    }
    $bytes = New-Object byte[] ($Hex.Length / 2)
    for ($i = 0; $i -lt $Hex.Length; $i += 2) {
        $bytes[$i / 2] = [Convert]::ToByte($Hex.Substring($i, 2), 16)
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes)
}

function Normalize-StorageKey {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) { return "" }
    $normalized = $Value.Trim().Replace("\", "/")
    while ($normalized.StartsWith("/")) {
        $normalized = $normalized.Substring(1)
    }
    return $normalized.Trim()
}

function Test-StorageKeySafe {
    param([string]$Value)

    $normalized = Normalize-StorageKey -Value $Value
    if ([string]::IsNullOrWhiteSpace($normalized)) { return $false }
    foreach ($segment in ($normalized -split "/")) {
        if ($segment -eq "." -or $segment -eq "..") { return $false }
    }
    return $true
}

function Get-LegacyAttachmentRoot {
    param([string]$LegacyPath)

    if ([string]::IsNullOrWhiteSpace($LegacyPath)) { return "" }
    if (-not [System.IO.Path]::IsPathRooted($LegacyPath)) { return "" }

    $normalized = $LegacyPath.Replace("/", "\")
    $lower = $normalized.ToLowerInvariant()
    foreach ($marker in @("\java-bot\attachments\", "\attachments\")) {
        $index = $lower.IndexOf($marker)
        if ($index -ge 0) {
            $endExclusive = $index + $marker.Length - 1
            if ($endExclusive -gt 0 -and $endExclusive -le $normalized.Length) {
                return $normalized.Substring(0, $endExclusive)
            }
        }
    }
    return ""
}

function Add-ExistingRoot {
    param(
        [hashtable]$RootMap,
        [string]$Path
    )

    if ([string]::IsNullOrWhiteSpace($Path)) { return }
    try {
        $full = [System.IO.Path]::GetFullPath($Path)
    } catch {
        return
    }
    if (Test-Path -LiteralPath $full -PathType Container) {
        $RootMap[$full] = $full
    }
}

function Add-Candidate {
    param(
        [hashtable]$CandidateMap,
        [string]$Path,
        [string]$Kind
    )

    if ([string]::IsNullOrWhiteSpace($Path)) { return }
    try {
        $full = [System.IO.Path]::GetFullPath($Path)
    } catch {
        return
    }
    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) { return }
    if (-not $CandidateMap.ContainsKey($full)) {
        $CandidateMap[$full] = [pscustomobject]@{
            Path = $full
            Kind = $Kind
        }
    }
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$dotEnv = Read-DotEnv -Path $dotEnvPath
$dbUser = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_USER" -DefaultValue "iguana"
$dbName = Get-SettingValue -DotEnv $dotEnv -Name "IGUANA_POSTGRES_DB" -DefaultValue "iguana"

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $composeFile)

Assert-ComposeSuccess `
    -Docker $docker `
    -ComposePrefix $composePrefix `
    -Arguments @("config", "-q") `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

if ($ValidateOnly) {
    Write-Host "[GREEN] PowerShell parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] local-source audit is read-only and no runtime data was accessed."
    return
}

$oldIgnoreOrphans = [Environment]::GetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "Process")
try {
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", "true", "Process")

    $running = Assert-ComposeSuccess `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -Arguments @("ps", "--status", "running", "--services") `
        -ErrorMessage "Unable to inspect production contour services"
    $runningServices = @($running.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
    if ($runningServices -notcontains "postgres") {
        throw "Required service is not running: postgres"
    }

    $rowsRaw = Query-Postgres `
        -Docker $docker `
        -ComposePrefix $composePrefix `
        -User $dbUser `
        -Database $dbName `
        -Sql "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') || chr(9) || encode(convert_to(COALESCE(legacy_attachment_ref, ''), 'UTF8'), 'hex') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' AND COALESCE(lower(availability_status), '') = 'missing' ORDER BY id"

    $rows = @()
    if (-not [string]::IsNullOrWhiteSpace($rowsRaw)) {
        foreach ($line in ($rowsRaw -split "`n")) {
            if ([string]::IsNullOrWhiteSpace($line)) { continue }
            $parts = $line -split "`t", 3
            if ($parts.Count -ne 3) {
                throw "Malformed PostgreSQL row: $line"
            }
            $storageKey = Normalize-StorageKey -Value (ConvertFrom-HexUtf8 -Hex $parts[1])
            if (-not (Test-StorageKeySafe -Value $storageKey)) {
                throw "Unsafe storage key for metadata_id=$($parts[0])"
            }
            $rows += [pscustomobject]@{
                Id = [long]$parts[0]
                StorageKey = $storageKey
                LegacyRef = ConvertFrom-HexUtf8 -Hex $parts[2]
            }
        }
    }

    $rootMap = @{}
    Add-ExistingRoot -RootMap $rootMap -Path (Join-Path $repoRoot "attachments")
    Add-ExistingRoot -RootMap $rootMap -Path (Join-Path $repoRoot "java-bot/attachments")

    foreach ($row in $rows) {
        $legacyRoot = Get-LegacyAttachmentRoot -LegacyPath ([string]$row.LegacyRef)
        Add-ExistingRoot -RootMap $rootMap -Path $legacyRoot
    }

    $fileNameIndex = @{}
    foreach ($root in @($rootMap.Values | Sort-Object -Unique)) {
        Write-Host "[INFO] Indexing local attachment root: $root"
        foreach ($file in @(Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction SilentlyContinue)) {
            $key = $file.Name.ToLowerInvariant()
            if (-not $fileNameIndex.ContainsKey($key)) {
                $fileNameIndex[$key] = New-Object System.Collections.ArrayList
            }
            [void]$fileNameIndex[$key].Add($file.FullName)
        }
    }

    $uniqueCount = 0
    $ambiguousCount = 0
    $missingCount = 0

    foreach ($row in $rows) {
        $candidateMap = @{}
        $legacyRef = [string]$row.LegacyRef
        if (-not [string]::IsNullOrWhiteSpace($legacyRef) -and [System.IO.Path]::IsPathRooted($legacyRef)) {
            Add-Candidate -CandidateMap $candidateMap -Path $legacyRef -Kind "absolute_legacy_ref"
        }

        $relativeStoragePath = $row.StorageKey.Replace("/", [string][System.IO.Path]::DirectorySeparatorChar)
        foreach ($root in @($rootMap.Values | Sort-Object -Unique)) {
            Add-Candidate `
                -CandidateMap $candidateMap `
                -Path (Join-Path $root $relativeStoragePath) `
                -Kind "storage_key_under_root"
        }

        $leaf = ""
        if (-not [string]::IsNullOrWhiteSpace($legacyRef)) {
            try {
                $leaf = [System.IO.Path]::GetFileName($legacyRef.Replace("/", "\"))
            } catch {
                $leaf = ""
            }
        }
        if ([string]::IsNullOrWhiteSpace($leaf)) {
            try {
                $leaf = [System.IO.Path]::GetFileName($row.StorageKey.Replace("/", "\"))
            } catch {
                $leaf = ""
            }
        }

        if (-not [string]::IsNullOrWhiteSpace($leaf)) {
            $indexKey = $leaf.ToLowerInvariant()
            if ($fileNameIndex.ContainsKey($indexKey)) {
                foreach ($path in @($fileNameIndex[$indexKey])) {
                    Add-Candidate -CandidateMap $candidateMap -Path ([string]$path) -Kind "basename_match"
                }
            }
        }

        $candidates = @($candidateMap.Values | Sort-Object Path)
        if ($candidates.Count -eq 1) {
            $uniqueCount++
            Write-Host "[FOUND] metadata_id=$($row.Id) kind=$($candidates[0].Kind) path=$($candidates[0].Path)"
        } elseif ($candidates.Count -gt 1) {
            $ambiguousCount++
            Write-Host "[AMBIGUOUS] metadata_id=$($row.Id) candidate_count=$($candidates.Count) storage_key=$($row.StorageKey)"
            foreach ($candidate in ($candidates | Select-Object -First 10)) {
                Write-Host "[AMBIGUOUS] metadata_id=$($row.Id) kind=$($candidate.Kind) path=$($candidate.Path)"
            }
        } else {
            $missingCount++
            Write-Host "[MISSING] metadata_id=$($row.Id) storage_key=$($row.StorageKey) legacy_ref=$legacyRef"
        }
    }

    Write-Host "[RESULT] STORAGE LOCAL SOURCE AUDIT"
    Write-Host "[RESULT] missing_metadata_rows_considered=$($rows.Count)"
    Write-Host "[RESULT] discovered_attachment_roots=$($rootMap.Count)"
    Write-Host "[RESULT] rows_with_unique_local_candidate=$uniqueCount"
    Write-Host "[RESULT] rows_with_ambiguous_local_candidates=$ambiguousCount"
    Write-Host "[RESULT] rows_with_no_local_candidate=$missingCount"
    Write-Host "[RESULT] no database rows, MinIO objects, or local files were modified."
} finally {
    [Environment]::SetEnvironmentVariable("COMPOSE_IGNORE_ORPHANS", $oldIgnoreOrphans, "Process")
}
