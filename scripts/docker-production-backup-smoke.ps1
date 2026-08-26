param([switch]$KeepArtifacts)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

if (-not $PSScriptRoot) { throw "Unable to resolve script root." }
$repoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
$helper = Join-Path $PSScriptRoot "docker-production-backup.ps1"
$smokeId = (Get-Date -Format "yyyyMMddHHmmss") + "-" + [Guid]::NewGuid().ToString("N").Substring(0, 8)
$smokeRoot = Join-Path $repoRoot (".tmp\backup-smoke-" + $smokeId)
$seededBucket = "iguana-backup-smoke-" + $smokeId.ToLowerInvariant()
$emptyBucket = $seededBucket + "-empty"
$baseCompose = Join-Path $repoRoot "docker-compose.production-contour.yml"
$backupCompose = Join-Path $repoRoot "docker-compose.production-backup.yml"
$dotEnvPath = Join-Path $repoRoot ".env"

$dockerCommand = Get-Command docker -ErrorAction Stop
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
    $code = Invoke-ComposeStatus $Arguments
    if ($code -ne 0) { throw "$Description failed with exit code ${code}: $($Arguments -join ' ')" }
}

function Write-SmokeShellScript {
    param([string]$RelativePath, [string[]]$Lines)
    $hostPath = Join-Path $smokeRoot $RelativePath
    $enc = New-Object System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($hostPath, (($Lines -join "`n") + "`n"), $enc)
    $bytes = [System.IO.File]::ReadAllBytes($hostPath)
    if ($bytes -contains 13) { throw "Generated smoke shell script contains CR: $hostPath" }
    $hostPath
}

function Invoke-MinioScript {
    param([string]$RelativePath, [string]$Description)
    $containerPath = "/backup/offhost/" + ($RelativePath -replace "\\", "/")
    $quoted = "'" + $containerPath + "'"
    Invoke-ComposeChecked @("run", "--rm", "--build", "--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh -n $quoted") "$Description sh -n"
    Invoke-ComposeChecked @("run", "--rm", "--entrypoint", "/bin/sh", "minio-backup", "-c", "exec /bin/sh $quoted") $Description
}

function Assert-PackageSet {
    param([int]$ExpectedMinioObjects)
    $postgres = @(Get-ChildItem (Join-Path $smokeRoot "packages\postgres") -Filter "iguana-postgres-*.tar.gz" -File -ErrorAction Stop)
    $minio = @(Get-ChildItem (Join-Path $smokeRoot "packages\minio") -Filter "iguana-minio-*.tar.gz" -File -ErrorAction Stop)
    $files = @(Get-ChildItem (Join-Path $smokeRoot "packages\files") -Filter "iguana-files-*.tar.gz" -File -ErrorAction Stop)
    if ($postgres.Count -lt 1 -or $minio.Count -lt 1 -or $files.Count -lt 1) {
        throw "Expected PostgreSQL, MinIO and file tar.gz packages."
    }
    foreach ($archive in @($postgres[-1], $minio[-1], $files[-1])) {
        if (-not (Test-Path -LiteralPath ($archive.FullName + ".sha256"))) {
            throw "Missing package SHA-256 sidecar: $($archive.FullName)"
        }
    }

    $pgEvidence = Get-Content (Join-Path $smokeRoot "packages\postgres\.iguana-restore-evidence.properties") -Raw
    $minioEvidence = Get-Content (Join-Path $smokeRoot "packages\minio\.iguana-restore-evidence.properties") -Raw
    $filesEvidence = Get-Content (Join-Path $smokeRoot "packages\files\.iguana-restore-evidence.properties") -Raw
    if ($minioEvidence -notmatch ("objects=" + $ExpectedMinioObjects + "(\D|$)")) {
        throw "MinIO restore evidence does not contain objects=$ExpectedMinioObjects"
    }
    if ($pgEvidence -notmatch "status=ok" -or $filesEvidence -notmatch "status=ok") {
        throw "Restore evidence is not GREEN."
    }
}

New-Item -ItemType Directory -Force -Path $smokeRoot | Out-Null
$previous = @{}
foreach ($name in @("IGUANA_BACKUP_DESTINATION_DIR", "IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN", "APP_STORAGE_OBJECT_BUCKET", "IGUANA_BACKUP_RESTORE_COMPONENTS")) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, "Process")
}
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_DESTINATION_DIR", $smokeRoot, "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_EXTERNAL_FAILURE_DOMAIN", "false", "Process")
[Environment]::SetEnvironmentVariable("IGUANA_BACKUP_RESTORE_COMPONENTS", "postgres,minio,shared-config", "Process")

$baseArguments = @("compose", "--project-directory", $repoRoot)
if (Test-Path $dotEnvPath) { $baseArguments += @("--env-file", $dotEnvPath) }
$baseArguments += @("-f", $baseCompose, "-f", $backupCompose, "--profile", "backup")

try {
    Write-Host "[SMOKE] Seeded MinIO tar.gz cycle"
    [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", $seededBucket, "Process")
    [void](Write-SmokeShellScript ".seeded-minio.sh" @(
        'set -eu',
        'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null',
        'mc mb --ignore-existing "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null',
        'printf "iguana portable backup smoke\n" > /tmp/smoke.txt',
        'mc cp /tmp/smoke.txt "primary/$APP_STORAGE_OBJECT_BUCKET/smoke.txt" >/dev/null',
        'mc stat "primary/$APP_STORAGE_OBJECT_BUCKET/smoke.txt" >/dev/null'
    ))
    Invoke-MinioScript ".seeded-minio.sh" "Seeded MinIO setup"

    & $helper -Action full -Mode critical -RestoreComponents "postgres,minio,shared-config" -AllowLocalDestination
    if ($LASTEXITCODE -ne 0) { throw "Seeded full backup/restore helper failed: $LASTEXITCODE" }
    Assert-PackageSet 1
    Write-Host "[GREEN] Seeded MinIO tar.gz cycle: objects=1"

    Write-Host "[SMOKE] Empty MinIO tar.gz cycle"
    Remove-Item (Join-Path $smokeRoot "packages\minio") -Recurse -Force
    [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", $emptyBucket, "Process")
    [void](Write-SmokeShellScript ".empty-minio.sh" @(
        'set -eu',
        'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null',
        'mc mb --ignore-existing "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null',
        'count="$(mc ls --recursive --json "primary/$APP_STORAGE_OBJECT_BUCKET" | wc -l | tr -d "[:space:]")"',
        'test "$count" = "0"'
    ))
    Invoke-MinioScript ".empty-minio.sh" "Empty MinIO setup"

    Invoke-ComposeChecked @("run", "--rm", "--build", "minio-backup") "Empty MinIO backup"
    Invoke-ComposeChecked @("up", "-d", "minio-restore-target") "Empty MinIO restore target"
    try {
        Invoke-ComposeChecked @("run", "--rm", "--build", "minio-restore-rehearsal") "Empty MinIO restore rehearsal"
    } finally {
        [void](Invoke-ComposeStatus @("rm", "-s", "-f", "minio-restore-target"))
    }
    $emptyEvidence = Get-Content (Join-Path $smokeRoot "packages\minio\.iguana-restore-evidence.properties") -Raw
    if ($emptyEvidence -notmatch "objects=0(\D|$)") { throw "Empty MinIO restore evidence does not prove objects=0." }
    Write-Host "[GREEN] Empty MinIO tar.gz cycle: objects=0"

    Write-Host "[GREEN] 01-212 portable recovery package smoke passed."
} finally {
    foreach ($bucket in @($seededBucket, $emptyBucket)) {
        try {
            [Environment]::SetEnvironmentVariable("APP_STORAGE_OBJECT_BUCKET", $bucket, "Process")
            [void](Write-SmokeShellScript ".cleanup-minio.sh" @(
                'set +e',
                'mc alias set primary http://minio:9000 "$APP_STORAGE_OBJECT_ACCESS_KEY" "$APP_STORAGE_OBJECT_SECRET_KEY" >/dev/null 2>&1',
                'mc rm --recursive --force "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null 2>&1',
                'mc rb --force "primary/$APP_STORAGE_OBJECT_BUCKET" >/dev/null 2>&1',
                'exit 0'
            ))
            Invoke-MinioScript ".cleanup-minio.sh" "MinIO cleanup $bucket"
        } catch {
            Write-Warning "Unable to clean smoke bucket $bucket : $($_.Exception.Message)"
        }
    }

    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], "Process")
    }
    if (-not $KeepArtifacts -and (Test-Path $smokeRoot)) {
        Remove-Item $smokeRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
