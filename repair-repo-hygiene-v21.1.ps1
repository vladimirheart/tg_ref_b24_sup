param()

$ErrorActionPreference = 'Stop'

$root = (Get-Location).Path
$gitDir = Join-Path $root '.git'
$gitignorePath = Join-Path $root '.gitignore'
$maxConfigPath = Join-Path $root 'java-bot\bot-max\src\main\resources\application.yml'

if (-not (Test-Path -LiteralPath $gitDir)) {
    throw "Run this script from the repository root: $root"
}

foreach ($path in @($gitignorePath, $maxConfigPath)) {
    if (-not (Test-Path -LiteralPath $path)) {
        throw "Required file not found: $path"
    }
}

function Has-Utf8Bom {
    param([string]$Path)

    $bytes = [System.IO.File]::ReadAllBytes($Path)
    return (
        $bytes.Length -ge 3 -and
        $bytes[0] -eq 0xEF -and
        $bytes[1] -eq 0xBB -and
        $bytes[2] -eq 0xBF
    )
}

function Write-Utf8 {
    param(
        [string]$Path,
        [string]$Text,
        [bool]$Bom
    )

    $encoding = New-Object System.Text.UTF8Encoding -ArgumentList $Bom
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

# ---------------------------------------------------------------------------
# 1. Normalize MAX application.yml to LF.
# ---------------------------------------------------------------------------

$maxBom = Has-Utf8Bom -Path $maxConfigPath
$maxConfig = [System.IO.File]::ReadAllText($maxConfigPath)

if (-not $maxConfig.Contains('# Iguana MAX launcher config v13')) {
    throw 'Expected MAX v13 launcher marker was not found.'
}

if (-not $maxConfig.Contains('name: logs/bot-max.log')) {
    throw 'Expected MAX-specific log path was not found.'
}

$maxConfig = $maxConfig.Replace("`r`r`n", "`n")
$maxConfig = $maxConfig.Replace("`r`n", "`n")
$maxConfig = $maxConfig.Replace("`r", "`n")
$maxConfig = $maxConfig.TrimEnd([char[]]"`n") + "`n"

Write-Utf8 -Path $maxConfigPath -Text $maxConfig -Bom $maxBom

# Byte-level guard: CR must not remain in the YAML file.
$maxBytes = [System.IO.File]::ReadAllBytes($maxConfigPath)
if ($maxBytes -contains 13) {
    throw 'CR byte still exists in MAX application.yml after LF normalization.'
}

# ---------------------------------------------------------------------------
# 2. Ignore root patch helper scripts.
# ---------------------------------------------------------------------------

$gitignoreBom = Has-Utf8Bom -Path $gitignorePath
$gitignore = [System.IO.File]::ReadAllText($gitignorePath)

$helperIgnore = '/apply-*.ps1'

if (-not $gitignore.Contains($helperIgnore)) {
    # Keep .gitignore itself LF-only to avoid introducing mixed endings.
    $gitignore = $gitignore.Replace("`r`r`n", "`n")
    $gitignore = $gitignore.Replace("`r`n", "`n")
    $gitignore = $gitignore.Replace("`r", "`n")
    $gitignore = $gitignore.TrimEnd([char[]]"`n") +
        "`n" +
        "`n# local patch helpers`n" +
        $helperIgnore +
        "`n"

    Write-Utf8 -Path $gitignorePath -Text $gitignore -Bom $gitignoreBom
}

Write-Host ''
Write-Host 'Repo hygiene v21.1 repaired.'
Write-Host '  MAX application.yml normalized to LF.'
Write-Host "  Ignored root patch helpers: $helperIgnore"
Write-Host '  Existing staged runtime-log removals were left untouched.'
Write-Host ''
Write-Host 'Next:'
Write-Host '  git diff --check'
Write-Host '  git status --short'
Write-Host ''
Write-Host 'bot-max.log will be created only after MAX starts with the new config.'
