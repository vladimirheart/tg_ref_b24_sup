$ErrorActionPreference = 'Stop'

$repoRoot = (Get-Location).Path
$scssRoot = Join-Path $repoRoot 'spring-panel\src\main\resources\scss'
$entryPath = Join-Path $scssRoot 'settings.scss'
$partialsDir = Join-Path $scssRoot 'settings'

$entryMarker = '/* Settings SCSS consolidation v14.1 */'
$legacyEntryMarker = '/* Settings SCSS consolidation v14 */'

$calmMarkers = @(
    '/* Settings Calm UI pass v2 */',
    '/* Settings Calm UI hierarchy pass v3 */'
)

$workspaceMarkers = @(
    '/* Settings workspace stability v6.1 */',
    '/* Settings workspace navigation v6 */',
    '/* Settings workspace expansion v7 */'
)

$taskDialogMarkers = @(
    '/* Settings compact task dialogs v11 */',
    '/* Settings child task dialogs v12 */'
)

function Read-Utf8Text([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Required file not found: $Path"
    }
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8Text([string]$Path, [string]$Text) {
    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $encoding = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Find-UniqueMarker([string]$Text, [string]$Marker) {
    $first = $Text.IndexOf($Marker, [System.StringComparison]::Ordinal)
    if ($first -lt 0) {
        return -1
    }
    $second = $Text.IndexOf($Marker, $first + $Marker.Length, [System.StringComparison]::Ordinal)
    if ($second -ge 0) {
        throw "Marker is not unique: $Marker"
    }
    return $first
}

function Find-PreferredMarker([string]$Text, [string[]]$Candidates, [string]$Label) {
    foreach ($candidate in $Candidates) {
        $index = Find-UniqueMarker $Text $candidate
        if ($index -ge 0) {
            return [pscustomobject]@{
                Marker = $candidate
                Index = $index
            }
        }
    }
    throw "No supported marker found for ${Label}. Tried: $($Candidates -join ', ')"
}

$source = Read-Utf8Text $entryPath

if ($source.Contains($entryMarker) -or $source.Contains($legacyEntryMarker)) {
    $expected = @(
        '_foundation.scss',
        '_calm.scss',
        '_workspace.scss',
        '_task-dialogs.scss'
    ) | ForEach-Object { Join-Path $partialsDir $_ }

    $missing = @($expected | Where-Object { -not (Test-Path -LiteralPath $_) })
    if ($missing.Count -eq 0) {
        Write-Host 'Settings SCSS consolidation is already applied.' -ForegroundColor Yellow
        exit 0
    }
    throw "Entrypoint is already consolidated, but partials are missing: $($missing -join ', ')"
}

$calmBoundary = Find-PreferredMarker $source $calmMarkers 'Calm UI boundary'
$workspaceBoundary = Find-PreferredMarker $source $workspaceMarkers 'workspace boundary'
$taskDialogBoundary = Find-PreferredMarker $source $taskDialogMarkers 'task-dialog boundary'

$idxCalm = $calmBoundary.Index
$idxWorkspace = $workspaceBoundary.Index
$idxTaskDialogs = $taskDialogBoundary.Index

if (-not ($idxCalm -gt 0 -and $idxWorkspace -gt $idxCalm -and $idxTaskDialogs -gt $idxWorkspace)) {
    throw "Unexpected Settings SCSS marker order. Calm=$idxCalm, Workspace=$idxWorkspace, TaskDialogs=$idxTaskDialogs"
}

Write-Host 'Using Settings SCSS boundaries:' -ForegroundColor Cyan
Write-Host "  Calm:       $($calmBoundary.Marker)"
Write-Host "  Workspace:  $($workspaceBoundary.Marker)"
Write-Host "  TaskDialog: $($taskDialogBoundary.Marker)"

$foundation = $source.Substring(0, $idxCalm)
$calm = $source.Substring($idxCalm, $idxWorkspace - $idxCalm)
$workspace = $source.Substring($idxWorkspace, $idxTaskDialogs - $idxWorkspace)
$taskDialogs = $source.Substring($idxTaskDialogs)

$reassembled = $foundation + $calm + $workspace + $taskDialogs
if (-not [string]::Equals($source, $reassembled, [System.StringComparison]::Ordinal)) {
    throw 'Internal split verification failed: reassembled SCSS differs from the original.'
}

$partialMap = [ordered]@{
    '_foundation.scss'   = $foundation
    '_calm.scss'         = $calm
    '_workspace.scss'    = $workspace
    '_task-dialogs.scss' = $taskDialogs
}

foreach ($name in $partialMap.Keys) {
    $path = Join-Path $partialsDir $name
    if (Test-Path -LiteralPath $path) {
        throw "Refusing to overwrite existing partial: $path"
    }
}

$entry = @"
$entryMarker
/*
 * Settings styles are split by responsibility.
 * Keep this file as the stable Sass entrypoint; edit the partials instead.
 * Import order preserves the pre-consolidation cascade.
 */
@use "settings/foundation";
@use "settings/calm";
@use "settings/workspace";
@use "settings/task-dialogs";
"@

# Write partials first. Replace the entrypoint only after all partials succeed.
foreach ($pair in $partialMap.GetEnumerator()) {
    Write-Utf8Text (Join-Path $partialsDir $pair.Key) $pair.Value
}
Write-Utf8Text $entryPath ($entry + [Environment]::NewLine)

Write-Host ''
Write-Host 'Done: Settings SCSS consolidation v14.1 applied.' -ForegroundColor Green
Write-Host 'Created:'
Write-Host '  spring-panel/src/main/resources/scss/settings/_foundation.scss'
Write-Host '  spring-panel/src/main/resources/scss/settings/_calm.scss'
Write-Host '  spring-panel/src/main/resources/scss/settings/_workspace.scss'
Write-Host '  spring-panel/src/main/resources/scss/settings/_task-dialogs.scss'
Write-Host 'Updated:'
Write-Host '  spring-panel/src/main/resources/scss/settings.scss'
Write-Host ''
Write-Host 'Next:'
Write-Host '  cd .\spring-panel'
Write-Host '  .\mvnw.cmd -q generate-resources'
Write-Host '  cd ..'
Write-Host '  git diff --check'
Write-Host '  git status --short'
