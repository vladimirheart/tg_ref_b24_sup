param(
    [switch]$KeepArtifacts
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$helper = Join-Path $PSScriptRoot "docker-production-backup.ps1"
$smokeId = (Get-Date -Format "yyyyMMddHHmmss") + "-" + [Guid]::NewGuid().ToString("N").Substring(0, 8)
$smokeRoot = Join-Path $repoRoot (".tmp\backup-smoke-" + $smokeId)
$smokeBucket = "iguana-backup-smoke-" + $smokeId.ToLowerInvariant()
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$backupCompose = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"

$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
if (-not $dockerCommand) { throw "Docker is not available in PATH." }
$docker = $dockerCommand.Source

function Invoke-ComposeStatus {
    param([string[]]$Arguments)
    $saved = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        & $docker @baseArguments @Arguments | Out-Host
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }
}

function Invoke-ComposeChecked {
    param([string[]]$Arguments, [string]$Description)
    $code = Invoke-ComposeStatus -Arguments $Arguments
    if ($code -ne 0) {
        throw "$Description failed with exit code ${code}: $($Arguments -join ' ')"
    }
}

function Write-SmokeShellScript {
    param([string]$RelativePath, [string[]]$Lines)
    $hostPath = Join-Path $smokeRoot $RelativePath
    $content = ($Lines -join "`n") + "`n"
    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($hostPath, $content, $enc)
    $bytes = [System.IO.File]::ReadAllBytes($hostPath)
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        throw "Generated smoke shell script has UTF-8 BOM: $hostPath"
    }
    if ($bytes -contains 13) {
        throw "Generated smoke shell script contains CR bytes: $hostPath"
    }
    return $hostPath
}

function Invoke-MinioShellChecked {
    param([string]$RelativePath, [string]$Description, [switch]$Build)
    $containerPath = "/backup/offhost/" + ($RelativePath -replace '\\', '/')
    $quotedPath = "'" + $containerPath + "'"
    $syntax = @("run", "--rm")
    if ($Build) { $syntax += "--build" }
    $syntax += @("--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh -n $quotedPath")
    Invoke-ComposeChecked -Arguments $syntax -Description "$Description sh -n"
    Write-Host "[GREEN] sh -n runtime smoke: $RelativePath"

    $run = @("run", "--rm", "--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh $quotedPath")
    Invoke-ComposeChecked -Arguments $run -Description $Description
}

New-Item -ItemType Directory -Force -Path $smokeRoot | Out-Null
$previousDestination = [Environment]::GetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", "Process")
$previousBucket = [Environment]::GetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $smokeRoot, "Process")
[Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", $smokeBucket, "Process")

$baseArguments = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath) { $baseArguments += @("--env-file", $dotEnvPath) }
$baseArguments += @("-f", $baseCompose, "-f", $backupCompose, "--profile", "backup")

try {
    Write-Host "[SMOKE] Creating isolated temporary MinIO source bucket: $smokeBucket"
    [void](Write-SmokeShellScript -RelativePath ".smoke-seed.sh" -Lines @(
        'set -eu',
        'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null',
        'mc mb --ignore-existing "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null',
        'printf "iguana backup smoke\n" > /tmp/iguana-backup-smoke.txt',
        'mc cp /tmp/iguana-backup-smoke.txt "primary/$APP_STORAGE_OBJECT_BUCKET/smoke.txt" >/dev/null',
        'mc stat "primary/$APP_STORAGE_OBJECT_BUCKET/smoke.txt" >/dev/null',
        'count="$(mc ls --recursive --json "primary/$APP_STORAGE_OBJECT_BUCKET" | wc -l | tr -d "[:space:]")"',
        'echo "[SMOKE] seed source objects=$count"',
        'test "$count" = "1"'
    ))
    Invoke-MinioShellChecked -RelativePath ".smoke-seed.sh" -Description "MinIO smoke seed" -Build

    [void](Write-SmokeShellScript -RelativePath ".smoke-fresh-verify.sh" -Lines @(
        'set -eu',
        'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null',
        'mc stat "primary/$APP_STORAGE_OBJECT_BUCKET/smoke.txt" >/dev/null',
        'count="$(mc ls --recursive --json "primary/$APP_STORAGE_OBJECT_BUCKET" | wc -l | tr -d "[:space:]")"',
        'echo "[SMOKE] fresh-container source objects before backup=$count"',
        'test "$count" = "1"'
    ))
    Invoke-MinioShellChecked -RelativePath ".smoke-fresh-verify.sh" -Description "MinIO fresh-container source verification"

    Write-Host "[SMOKE] Preflight exact production minio-backup service"
    Invoke-ComposeChecked -Arguments @("run", "--rm", "--build", "minio-backup") -Description "Production minio-backup preflight"

    $preflightManifestDir = Join-Path $smokeRoot "minio\manifests"
    $preflightManifests = @(Get-ChildItem -LiteralPath $preflightManifestDir -Filter "*.manifest.json" -File -ErrorAction SilentlyContinue)
    if ($preflightManifests.Count -ne 1) {
        throw "Production minio-backup preflight expected exactly one manifest, got $($preflightManifests.Count)."
    }
    $preflightManifestPath = ($preflightManifests | Select-Object -First 1).FullName
    $preflightManifest = Get-Content -LiteralPath $preflightManifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([int]$preflightManifest.source_object_count -ne 1) {
        throw "Production minio-backup preflight source_object_count expected 1, got $($preflightManifest.source_object_count)."
    }
    if ([int]$preflightManifest.local_file_count -ne 1) {
        throw "Production minio-backup preflight local_file_count expected 1, got $($preflightManifest.local_file_count)."
    }
    if ([string]$preflightManifest.bucket -ne $smokeBucket) {
        throw "Production minio-backup preflight manifest bucket mismatch: $($preflightManifest.bucket)"
    }
    $preflightSnapshot = Join-Path $smokeRoot ("minio\snapshots\" + [string]$preflightManifest.snapshot + "\objects\smoke.txt")
    if (-not (Test-Path -LiteralPath $preflightSnapshot)) {
        throw "Production minio-backup preflight snapshot is missing smoke.txt: $preflightSnapshot"
    }
    Write-Host "[GREEN] Exact production minio-backup preflight passed: source=1 local=1"

    Remove-Item -LiteralPath (Join-Path $smokeRoot "minio") -Recurse -Force
    Write-Host "[SMOKE] Preflight MinIO artifacts removed before full backup/restore smoke"

    & $helper -Action full -AllowLocalDestination

    $postgresDumps = @(Get-ChildItem -LiteralPath (Join-Path $smokeRoot "postgres") -Filter "iguana-postgres-*.dump" -File)
    $postgresEvidence = Join-Path $smokeRoot "postgres\.iguana-restore-evidence.properties"
    $minioManifests = @(Get-ChildItem -LiteralPath (Join-Path $smokeRoot "minio\manifests") -Filter "*.manifest.json" -File)
    $minioEvidence = Join-Path $smokeRoot "minio\manifests\.iguana-restore-evidence.properties"

    if ($postgresDumps.Count -lt 1) { throw "PostgreSQL smoke did not create a dump." }
    if (-not (Test-Path -LiteralPath $postgresEvidence)) { throw "PostgreSQL restore evidence is missing." }
    if ($minioManifests.Count -lt 1) { throw "MinIO smoke did not create a manifest." }
    if (-not (Test-Path -LiteralPath $minioEvidence)) { throw "MinIO restore evidence is missing." }

    $minioManifest = Get-Content -LiteralPath ($minioManifests | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1).FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    if ([int]$minioManifest.source_object_count -ne 1) {
        throw "MinIO smoke expected source_object_count=1, got $($minioManifest.source_object_count)."
    }
    if ([int]$minioManifest.local_file_count -ne 1) {
        throw "MinIO smoke expected local_file_count=1, got $($minioManifest.local_file_count)."
    }
    if ([string]$minioManifest.bucket -ne $smokeBucket) {
        throw "MinIO smoke manifest bucket mismatch: $($minioManifest.bucket)"
    }

    Write-Host "[GREEN] 01-212 backup/restore smoke passed."
    Write-Host "[GREEN] PostgreSQL dump + checksum + isolated restore evidence present."
    Write-Host "[GREEN] MinIO seeded source=1, snapshot local=1, isolated restore evidence present."
    Write-Host "[INFO] Smoke destination: $smokeRoot"
} finally {
    try {
        [void](Write-SmokeShellScript -RelativePath ".smoke-cleanup.sh" -Lines @(
            'set +e',
            'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null 2>&1',
            'mc rm --recursive --force "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null 2>&1',
            'mc rb --force "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null 2>&1',
            'exit 0'
        ))
        $cleanupContainerPath = "/backup/offhost/.smoke-cleanup.sh"
        $cleanupQuotedPath = "'" + $cleanupContainerPath + "'"
        $cleanupSyntax = Invoke-ComposeStatus -Arguments @("run", "--rm", "--build", "--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh -n $cleanupQuotedPath")
        if ($cleanupSyntax -eq 0) {
            $cleanupCode = Invoke-ComposeStatus -Arguments @("run", "--rm", "--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh $cleanupQuotedPath")
            if ($cleanupCode -ne 0) {
                Write-Warning "Unable to clean temporary MinIO smoke bucket $smokeBucket. docker compose exit code: $cleanupCode"
            }
        } else {
            Write-Warning "Smoke cleanup script failed sh -n; temporary bucket may remain: $smokeBucket"
        }
    } catch {
        Write-Warning "Unable to clean temporary MinIO smoke bucket $smokeBucket : $($_.Exception.Message)"
    }

    [Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $previousDestination, "Process")
    [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", $previousBucket, "Process")
    if (-not $KeepArtifacts -and (Test-Path -LiteralPath $smokeRoot)) {
        Remove-Item -LiteralPath $smokeRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
