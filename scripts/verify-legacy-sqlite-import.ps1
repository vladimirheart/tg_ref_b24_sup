param(
    [string]$StagingDirectory = "",
    [string]$ComposeFile = ""
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Resolve-RepoPath {
    param([string]$Value, [string]$DefaultValue, [string]$RepoRoot)

    $candidate = if ([string]::IsNullOrWhiteSpace($Value)) { $DefaultValue } else { $Value }
    if ([System.IO.Path]::IsPathRooted($candidate)) {
        return [System.IO.Path]::GetFullPath($candidate)
    }
    return [System.IO.Path]::GetFullPath((Join-Path $RepoRoot $candidate))
}

function Read-DotEnv {
    param([string]$Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed) -or $trimmed.StartsWith("#")) { continue }
        $separator = $trimmed.IndexOf("=")
        if ($separator -gt 0) {
            $values[$trimmed.Substring(0, $separator).Trim()] = $trimmed.Substring($separator + 1).Trim()
        }
    }
    return $values
}

function Invoke-PostgresScalar {
    param([string]$Query)

    $output = & $docker @composeArgs exec -T postgres psql -U $postgresUser -d $postgresDatabase -At -v ON_ERROR_STOP=1 -c $Query
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL verification query failed." }
    return ([string]($output | Select-Object -Last 1)).Trim()
}

$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$envFile = Join-Path $repoRoot ".env"
if (-not (Test-Path -LiteralPath $envFile -PathType Leaf)) { throw "Missing .env: $envFile" }
$dotEnv = Read-DotEnv -Path $envFile
$stageRoot = Resolve-RepoPath -Value $StagingDirectory -DefaultValue ".tmp/legacy-sqlite-import" -RepoRoot $repoRoot
$composePath = Resolve-RepoPath -Value $ComposeFile -DefaultValue "docker-compose.production-contour.yml" -RepoRoot $repoRoot
$sqlite3 = Get-Command sqlite3 -ErrorAction SilentlyContinue
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $sqlite3) { throw "sqlite3 CLI is required for source verification." }
if (-not $dockerCommand) { throw "docker is required for PostgreSQL verification." }
if (-not (Test-Path -LiteralPath (Join-Path $stageRoot "manifest.json") -PathType Leaf)) {
    throw "Staging manifest is missing. Run stage-legacy-sqlite-import.ps1 first: $stageRoot"
}

$docker = $dockerCommand.Source
$postgresUser = if ($dotEnv.ContainsKey("IGUANA_POSTGRES_USER")) { $dotEnv["IGUANA_POSTGRES_USER"] } else { "iguana" }
$postgresDatabase = if ($dotEnv.ContainsKey("IGUANA_POSTGRES_DB")) { $dotEnv["IGUANA_POSTGRES_DB"] } else { "iguana" }
$composeArgs = @("compose", "--project-directory", $repoRoot, "--env-file", $envFile, "-f", $composePath)

$criticalTables = @("messages", "chat_history", "notifications", "web_form_sessions", "chat_attachment_metadata")
$source = Join-Path $stageRoot "panel_runtime.db"
if (-not (Test-Path -LiteralPath $source -PathType Leaf)) { throw "Staged panel_runtime.db is missing: $source" }

$mismatches = @()
foreach ($table in $criticalTables) {
    $exists = ([string](& $sqlite3.Source $source "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='$table';")).Trim()
    if ($exists -ne "1") { continue }
    $sourceCount = [long](([string](& $sqlite3.Source $source "SELECT COUNT(*) FROM [$table];")).Trim())
    $targetCount = [long](Invoke-PostgresScalar -Query "SELECT COUNT(*) FROM `"$table`";")
    Write-Host "[RESULT] table=$table source_rows=$sourceCount postgresql_rows=$targetCount"
    if ($targetCount -lt $sourceCount) {
        $mismatches += "$table source=$sourceCount postgresql=$targetCount"
    }
}

$recoveryCount = [int](Invoke-PostgresScalar -Query "SELECT COUNT(*) FROM legacy_sqlite_recovery WHERE source_path = '/opt/iguana/legacy-sqlite/panel_runtime.db';")
Write-Host "[RESULT] critical_recovery_ledger_rows=$recoveryCount"
if ($recoveryCount -lt 1) { $mismatches += "critical recovery ledger is empty" }

$changedShardCount = 0
$stagedShardDirectory = Join-Path $stageRoot "bot_databases"
if (Test-Path -LiteralPath $stagedShardDirectory -PathType Container) {
    foreach ($shard in Get-ChildItem -LiteralPath $stagedShardDirectory -Filter "bot-*.db" -File) {
        $containerPath = "/opt/iguana/bot_databases/$($shard.Name)"
        $marker = Invoke-PostgresScalar -Query "SELECT source_size || '|' || COALESCE(source_modified_at::text, '') FROM legacy_bot_shard_imports WHERE source_path = '$containerPath' LIMIT 1;"
        if ([string]::IsNullOrWhiteSpace($marker)) { continue }

        $markerParts = $marker -split "\|", 2
        $markerSize = [long]$markerParts[0]
        $markerModifiedAt = if ($markerParts.Count -gt 1 -and -not [string]::IsNullOrWhiteSpace($markerParts[1])) {
            [DateTimeOffset]::Parse($markerParts[1]).ToUnixTimeMilliseconds()
        } else {
            $null
        }
        $currentModifiedAt = [DateTimeOffset]$shard.LastWriteTimeUtc
        $changed = $markerSize -ne $shard.Length -or (
            $null -ne $markerModifiedAt -and $markerModifiedAt -ne $currentModifiedAt.ToUnixTimeMilliseconds()
        )
        if ($changed) {
            $changedShardCount++
            Write-Warning "Legacy bot shard changed after its prior import: $($shard.Name). Automatic re-import remains blocked (size=$($shard.Length), modified_utc=$($shard.LastWriteTimeUtc.ToString('o')))."
        }
    }
}
Write-Host "[RESULT] changed_bot_shard_markers=$changedShardCount"
Write-Host "[INFO] Review db-migrate warnings for changed bot shards; they remain intentionally excluded from automatic re-import."

if ($mismatches.Count -gt 0) {
    throw "Legacy SQLite verification failed: $($mismatches -join '; ')"
}

Write-Host "[GREEN] Critical legacy SQLite table counts are covered by PostgreSQL and recovery evidence exists."
