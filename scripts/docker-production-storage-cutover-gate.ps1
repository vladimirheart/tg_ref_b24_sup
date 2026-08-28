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

function Invoke-NativeCapture {
    param(
        [string]$Executable,
        [string[]]$Arguments
    )

    $saved = $ErrorActionPreference
    $code = -1
    $output = @()
    try {
        $ErrorActionPreference = "Continue"
        $output = @(& $Executable @Arguments 2>&1)
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $saved
    }

    return [pscustomobject]@{
        ExitCode = $code
        Output = @($output | ForEach-Object { [string]$_ })
    }
}

function Invoke-DockerCapture {
    param(
        [string]$Docker,
        [string[]]$Arguments
    )

    return Invoke-NativeCapture -Executable $Docker -Arguments $Arguments
}

function Assert-DockerSuccess {
    param(
        [string]$Docker,
        [string[]]$Arguments,
        [string]$ErrorMessage
    )

    $result = Invoke-DockerCapture -Docker $Docker -Arguments $Arguments
    if ($result.ExitCode -ne 0) {
        throw "${ErrorMessage}: $($result.Output -join ' ')"
    }
    return $result
}

function Normalize-StorageKey {
    param([string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    $normalized = $Value.Trim().Replace("\", "/")
    while ($normalized.StartsWith("/")) {
        $normalized = $normalized.Substring(1)
    }
    return $normalized.Trim()
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

function Get-ResultInteger {
    param(
        [string[]]$Lines,
        [string]$Name
    )

    $pattern = '\[RESULT\]\s+' + [regex]::Escape($Name) + '=(\d+)'
    foreach ($line in @($Lines)) {
        $match = [regex]::Match([string]$line, $pattern)
        if ($match.Success) {
            return [int]$match.Groups[1].Value
        }
    }
    throw "Upstream storage audit did not emit [RESULT] $Name."
}

function Get-MissingAttachmentIds {
    param([string[]]$Lines)

    $ids = @()
    foreach ($line in @($Lines)) {
        $match = [regex]::Match([string]$line, '\[WARN\]\s+attachment metadata_id=(\d+)')
        if ($match.Success) {
            $ids += [long]$match.Groups[1].Value
        }
    }
    return @($ids | Sort-Object -Unique)
}

function Assert-ExactIdSet {
    param(
        [long[]]$Expected,
        [long[]]$Actual,
        [string]$Label
    )

    $expectedSorted = @($Expected | Sort-Object -Unique)
    $actualSorted = @($Actual | Sort-Object -Unique)
    $diff = @(Compare-Object -ReferenceObject $expectedSorted -DifferenceObject $actualSorted)
    if ($diff.Count -gt 0) {
        $expectedText = ($expectedSorted | ForEach-Object { [string]$_ }) -join ","
        $actualText = ($actualSorted | ForEach-Object { [string]$_ }) -join ","
        throw "$Label mismatch. expected=[$expectedText] actual=[$actualText]"
    }
}

$repoRoot = Get-RepoRoot
$docker = Ensure-DockerAvailable
$dotEnvPath = Join-Path $repoRoot ".env"
$composeFile = Join-Path $repoRoot "docker-compose.production-contour.yml"
$upstreamAudit = Join-Path $repoRoot "scripts/docker-production-storage-cutover-audit.ps1"
$manifestPath = Join-Path $repoRoot "ai-context/storage-known-unrecoverable-dialog-attachments.json"

foreach ($requiredFile in @($composeFile, $upstreamAudit, $manifestPath)) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Required cutover gate file is missing: $requiredFile"
    }
}

try {
    $manifest = (Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8) | ConvertFrom-Json
} catch {
    throw "Unable to parse known-unrecoverable manifest: $($_.Exception.Message)"
}

$manifestEntries = @($manifest.entries)
if ($manifestEntries.Count -eq 0) {
    throw "Known-unrecoverable manifest contains no entries."
}

$manifestById = @{}
$manifestByKey = @{}
foreach ($entry in $manifestEntries) {
    $id = [long]$entry.metadata_id
    $storageKey = Normalize-StorageKey -Value ([string]$entry.storage_key)
    if ($id -le 0 -or [string]::IsNullOrWhiteSpace($storageKey)) {
        throw "Known-unrecoverable manifest contains an invalid entry."
    }
    $idKey = [string]$id
    $storageKeyLookup = $storageKey.ToLowerInvariant()
    if ($manifestById.ContainsKey($idKey)) {
        throw "Duplicate metadata_id in known-unrecoverable manifest: $id"
    }
    if ($manifestByKey.ContainsKey($storageKeyLookup)) {
        throw "Duplicate storage_key in known-unrecoverable manifest: $storageKey"
    }
    $manifestById[$idKey] = [pscustomobject]@{
        Id = $id
        StorageKey = $storageKey
    }
    $manifestByKey[$storageKeyLookup] = $id
}

$composePrefix = @("compose", "--project-directory", $repoRoot)
if (Test-Path -LiteralPath $dotEnvPath -PathType Leaf) {
    $composePrefix += @("--env-file", $dotEnvPath)
}
$composePrefix += @("-f", $composeFile)

Assert-DockerSuccess `
    -Docker $docker `
    -Arguments ($composePrefix + @("config", "-q")) `
    -ErrorMessage "Production Compose model is invalid" | Out-Null

$powershellExe = Join-Path $PSHOME "powershell.exe"
if (-not (Test-Path -LiteralPath $powershellExe -PathType Leaf)) {
    $powershellCommand = Get-Command powershell.exe -ErrorAction SilentlyContinue
    if (-not $powershellCommand) {
        throw "powershell.exe is unavailable for the upstream storage audit."
    }
    $powershellExe = $powershellCommand.Source
}

if ($ValidateOnly) {
    $upstreamValidation = Invoke-NativeCapture `
        -Executable $powershellExe `
        -Arguments @(
            "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", $upstreamAudit,
            "-ValidateOnly"
        )
    foreach ($line in $upstreamValidation.Output) {
        if ($line -match '\[(GREEN|RESULT|WARN)\]') {
            Write-Host "[UPSTREAM] $line"
        }
    }
    if ($upstreamValidation.ExitCode -ne 0) {
        throw "Upstream storage audit ValidateOnly failed with exit code $($upstreamValidation.ExitCode)."
    }
    Write-Host "[GREEN] Storage cutover gate parsed successfully and the production Compose model is valid."
    Write-Host "[RESULT] known_unrecoverable_manifest_entries=$($manifestEntries.Count)"
    Write-Host "[RESULT] validation is read-only and no runtime data was accessed."
    return
}

$upstreamResult = Invoke-NativeCapture `
    -Executable $powershellExe `
    -Arguments @(
        "-NoLogo", "-NoProfile", "-ExecutionPolicy", "Bypass",
        "-File", $upstreamAudit
    )

foreach ($line in $upstreamResult.Output) {
    if ($line -match '\[(RESULT|WARN|GREEN)\]') {
        Write-Host "[UPSTREAM] $line"
    }
}

$rawMissingDialogObjects = Get-ResultInteger -Lines $upstreamResult.Output -Name "missing_s3_dialog_objects"
$missingMetadataRows = Get-ResultInteger -Lines $upstreamResult.Output -Name "missing_metadata_rows"
$missingPanelAvatars = Get-ResultInteger -Lines $upstreamResult.Output -Name "missing_s3_panel_avatars"
$invalidPanelAvatarRefs = Get-ResultInteger -Lines $upstreamResult.Output -Name "invalid_panel_avatar_refs"
$missingAttachmentIds = @(Get-MissingAttachmentIds -Lines $upstreamResult.Output)
$manifestIds = @($manifestEntries | ForEach-Object { [long]$_.metadata_id })

if ($rawMissingDialogObjects -gt 20) {
    throw "Upstream storage audit reports $rawMissingDialogObjects missing dialog objects but emits at most 20 detail rows; refusing to apply a partial known-loss exception."
}
if ($missingAttachmentIds.Count -ne $rawMissingDialogObjects) {
    throw "Upstream storage audit detail count does not match missing_s3_dialog_objects. details=$($missingAttachmentIds.Count) result=$rawMissingDialogObjects"
}
Assert-ExactIdSet -Expected $manifestIds -Actual $missingAttachmentIds -Label "Known-unrecoverable S3 attachment set"

if ($missingMetadataRows -ne 0) {
    throw "Storage cutover remains blocked: missing_metadata_rows=$missingMetadataRows"
}
if ($missingPanelAvatars -ne 0) {
    throw "Storage cutover remains blocked: missing_s3_panel_avatars=$missingPanelAvatars"
}
if ($invalidPanelAvatarRefs -ne 0) {
    throw "Storage cutover remains blocked: invalid_panel_avatar_refs=$invalidPanelAvatarRefs"
}

$dbUserResult = Assert-DockerSuccess `
    -Docker $docker `
    -Arguments ($composePrefix + @("exec", "-T", "postgres", "printenv", "POSTGRES_USER")) `
    -ErrorMessage "Unable to resolve POSTGRES_USER from postgres"
$dbUser = (($dbUserResult.Output | ForEach-Object { [string]$_ }) -join "").Trim()

$dbNameResult = Assert-DockerSuccess `
    -Docker $docker `
    -Arguments ($composePrefix + @("exec", "-T", "postgres", "printenv", "POSTGRES_DB")) `
    -ErrorMessage "Unable to resolve POSTGRES_DB from postgres"
$dbName = (($dbNameResult.Output | ForEach-Object { [string]$_ }) -join "").Trim()

$sql = "SELECT id || chr(9) || encode(convert_to(storage_key, 'UTF8'), 'hex') FROM chat_attachment_metadata WHERE storage_key IS NOT NULL AND btrim(storage_key) <> '' AND COALESCE(lower(storage_provider), '') <> 'external_url' AND COALESCE(lower(availability_status), '') = 'missing' ORDER BY id"
$dbMissingResult = Assert-DockerSuccess `
    -Docker $docker `
    -Arguments ($composePrefix + @("exec", "-T", "postgres", "psql", "-U", $dbUser, "-d", $dbName, "-Atc", $sql)) `
    -ErrorMessage "Unable to read missing attachment metadata"

$dbMissingById = @{}
foreach ($lineObject in $dbMissingResult.Output) {
    $line = ([string]$lineObject).Trim()
    if ([string]::IsNullOrWhiteSpace($line)) {
        continue
    }
    $parts = $line -split "`t", 2
    if ($parts.Count -ne 2) {
        throw "Malformed PostgreSQL missing-metadata row: $line"
    }
    $id = [long]$parts[0]
    $storageKey = Normalize-StorageKey -Value (ConvertFrom-HexUtf8 -Hex $parts[1].Trim())
    $dbMissingById[[string]$id] = $storageKey
}

if ($dbMissingById.Count -ne $manifestEntries.Count) {
    throw "PostgreSQL missing attachment set count differs from reviewed manifest. db=$($dbMissingById.Count) manifest=$($manifestEntries.Count)"
}

foreach ($manifestEntry in $manifestById.Values) {
    $idKey = [string]$manifestEntry.Id
    if (-not $dbMissingById.ContainsKey($idKey)) {
        throw "Reviewed known-unrecoverable metadata_id=$($manifestEntry.Id) is no longer availability_status=missing. Review and update the manifest before cutover."
    }
    $dbStorageKey = Normalize-StorageKey -Value ([string]$dbMissingById[$idKey])
    if ($dbStorageKey -cne $manifestEntry.StorageKey) {
        throw "Reviewed known-unrecoverable storage_key changed for metadata_id=$($manifestEntry.Id). manifest='$($manifestEntry.StorageKey)' db='$dbStorageKey'"
    }
}

$unexpectedMissingDialogObjects = $rawMissingDialogObjects - $manifestEntries.Count
if ($unexpectedMissingDialogObjects -ne 0) {
    throw "Storage cutover remains blocked: unexpected_missing_s3_dialog_objects=$unexpectedMissingDialogObjects"
}

Write-Host "[RESULT] STORAGE CUTOVER GATE"
Write-Host "[RESULT] raw_missing_s3_dialog_objects=$rawMissingDialogObjects"
Write-Host "[RESULT] known_unrecoverable_dialog_objects=$($manifestEntries.Count)"
Write-Host "[RESULT] unexpected_missing_s3_dialog_objects=$unexpectedMissingDialogObjects"
Write-Host "[RESULT] missing_metadata_rows=$missingMetadataRows"
Write-Host "[RESULT] missing_s3_panel_avatars=$missingPanelAvatars"
Write-Host "[RESULT] invalid_panel_avatar_refs=$invalidPanelAvatarRefs"
Write-Host "[RESULT] known_unrecoverable_manifest_db_rows=$($dbMissingById.Count)"
Write-Host "[RESULT] gate is read-only and did not modify database rows, MinIO objects, or local files."
Write-Host "[GREEN] STORAGE CUTOVER GATE PASSED: all unexpected dialog mappings and panel avatar gaps are zero; $($manifestEntries.Count) reviewed historical attachments remain explicitly known-unrecoverable."
