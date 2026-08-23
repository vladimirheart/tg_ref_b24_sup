param(
    [switch]$SkipBuild,
    [switch]$AllowDirty
)

$ErrorActionPreference = 'Stop'
$root = (Get-Location).Path

if (-not (Test-Path -LiteralPath (Join-Path $root '.git'))) {
    throw "Run from repository root: $root"
}

$failures = New-Object System.Collections.Generic.List[string]
$passes = New-Object System.Collections.Generic.List[string]

function Pass([string]$Message) {
    $passes.Add($Message)
    Write-Host "[PASS] $Message"
}

function Fail([string]$Message) {
    $failures.Add($Message)
    Write-Host "[FAIL] $Message"
}

function ReadRepo([string]$RelativePath) {
    $path = Join-Path $root $RelativePath
    if (-not (Test-Path -LiteralPath $path)) {
        Fail "Missing file: $RelativePath"
        return $null
    }
    return [System.IO.File]::ReadAllText($path)
}

function Has([string]$Text, [string]$Needle, [string]$Label) {
    if ($null -eq $Text) { return }
    if ($Text.Contains($Needle)) { Pass $Label } else { Fail "$Label (missing marker)" }
}

function HasNot([string]$Text, [string]$Needle, [string]$Label) {
    if ($null -eq $Text) { return }
    if (-not $Text.Contains($Needle)) { Pass $Label } else { Fail "$Label (unexpected marker)" }
}

function Git([string[]]$Arguments) {
    $previousErrorActionPreference = $ErrorActionPreference
    $nativeExitCode = 1
    try {
        # Windows PowerShell 5.1 may promote harmless native stderr lines
        # (for example Git LF/CRLF warnings) to ErrorRecord objects.
        # Keep those warnings visible/captured, but decide success by exit code.
        $ErrorActionPreference = 'Continue'
        $out = @(& git.exe @Arguments 2>&1)
        $nativeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return @{ Code = $nativeExitCode; Out = $out }
}

Write-Host ''
Write-Host 'Iguana release-readiness gate v25'
Write-Host '================================='

$check = Git @('diff', '--check')
if ($check.Code -eq 0) { Pass 'git diff --check' } else { Fail 'git diff --check' }

$status = Git @('status', '--porcelain')
if ($status.Code -ne 0) {
    Fail 'git status --porcelain'
} elseif ($status.Out.Count -gt 0 -and -not $AllowDirty) {
    Fail 'Working tree is not clean'
} else {
    Pass 'Working tree state accepted'
}

$trackedResult = Git @('ls-files')
$tracked = @()
if ($trackedResult.Code -eq 0) {
    $tracked = @($trackedResult.Out | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ })
} else {
    Fail 'git ls-files'
}

$trackedLogs = @($tracked | Where-Object { $_ -match '^java-bot/[^/]+/logs/' })
if ($trackedLogs.Count -eq 0) { Pass 'Runtime bot logs are untracked' } else { Fail 'Tracked runtime bot logs found' }

$trackedHelpers = @($tracked | Where-Object { $_ -notmatch '/' -and $_ -match '^(apply|fix|repair)-.*\.ps1$' })
if ($trackedHelpers.Count -eq 0) { Pass 'Root patch helpers are untracked' } else { Fail 'Tracked root patch helpers found' }

$gitignore = ReadRepo '.gitignore'
foreach ($pattern in @('/java-bot/**/logs/', '/apply-*.ps1', '/fix-*.ps1', '/repair-*.ps1')) {
    Has $gitignore $pattern ".gitignore: $pattern"
}

$templatesRoot = Join-Path $root 'spring-panel\src\main\resources\templates'
$cdnPattern = 'https://(?:cdn\.jsdelivr\.net|code\.jquery\.com|cdnjs\.cloudflare\.com|unpkg\.com|fonts\.googleapis\.com|fonts\.gstatic\.com|cdn\.datatables\.net|ajax\.googleapis\.com)'
$cdnHits = New-Object System.Collections.Generic.List[string]

if (Test-Path -LiteralPath $templatesRoot) {
    Get-ChildItem -LiteralPath $templatesRoot -Recurse -File -Filter '*.html' | ForEach-Object {
        $content = [System.IO.File]::ReadAllText($_.FullName)
        if ([regex]::IsMatch($content, $cdnPattern, 'IgnoreCase')) {
            $cdnHits.Add($_.FullName.Substring($root.Length).TrimStart('\', '/'))
        }
    }
}

if ($cdnHits.Count -eq 0) { Pass 'Templates are runtime-CDN free' } else { Fail ('Runtime CDN references: ' + ($cdnHits -join ', ')) }

$vendorRoot = Join-Path $root 'spring-panel\src\main\resources\static\vendor'
$vendorFiles = @(
    'bootstrap\5.3.0\bootstrap.min.css',
    'bootstrap\5.3.3\bootstrap.min.css',
    'bootstrap\5.3.3\bootstrap.bundle.min.js',
    'bootstrap-icons\1.10.0\fonts\bootstrap-icons.woff2',
    'bootstrap-icons\1.10.5\fonts\bootstrap-icons.woff2',
    'jquery\3.6.0\jquery.min.js',
    'select2\4.1.0-rc.0\select2.min.js',
    'flatpickr\4.6.13\flatpickr.min.js',
    'chart.js\4.5.1\chart.umd.min.js',
    'lottie-web\5.12.2\lottie.min.js'
)

$vendorMissing = @($vendorFiles | Where-Object {
    $p = Join-Path $vendorRoot $_
    -not (Test-Path -LiteralPath $p) -or (Get-Item -LiteralPath $p).Length -le 0
})
if ($vendorMissing.Count -eq 0) { Pass 'Vendored frontend assets exist' } else { Fail ('Missing vendor assets: ' + ($vendorMissing -join ', ')) }

$botProcess = ReadRepo 'spring-panel\src\main\java\com\example\panel\service\BotProcessService.java'
Has $botProcess 'System.getenv("APP_BOT_LOG_DIR")' 'MAX/Telegram log directory runtime contract'
HasNot $botProcess 'System.getenv("APP_BOT_LOG_PATH")' 'No parent APP_BOT_LOG_PATH leakage'
Has $botProcess '"support-bot-" + platform + "-" + channelId + ".log"' 'Per-channel bot log identity'

$logback = ReadRepo 'java-bot\bot-core\src\main\resources\logback-spring.xml'
Has $logback 'defaultValue="logs/support-bot.log"' 'Neutral shared Logback fallback'
HasNot $logback 'defaultValue="logs/bot-telegram.log"' 'No Telegram fallback identity'

$maxConfig = ReadRepo 'java-bot\bot-max\src\main\resources\application.yml'
Has $maxConfig 'import: "classpath:/application-shared.yml"' 'MAX imports shared config'
HasNot $maxConfig 'name: logs/bot-max.log' 'MAX has no competing local log path'

$photo = ReadRepo 'spring-panel\src\main\java\com\example\panel\service\PanelUserPhotoService.java'
Has $photo 'migrateLegacyLocalAvatar' 'Panel-user avatar lazy migration'
Has $photo 'attachmentObjectStorageService.storeAvatar' 'Avatar migration targets canonical storage'

$dialogsIndex = ReadRepo 'spring-panel\src\main\resources\templates\dialogs\index.html'
Has $dialogsIndex 'id="workspaceComposerEmojiTrigger"' 'Workspace emoji control'
Has $dialogsIndex 'id="workspaceEmojiPanel"' 'Workspace emoji panel'

$workspace = ReadRepo 'spring-panel\src\main\resources\static\js\dialogs-workspace-runtime.js'
Has $workspace 'workspaceDragHasFiles' 'Workspace file drag-drop'
Has $workspace 'options.handleMediaSurfaceClick?.(event);' 'Workspace media click handler'
if ($null -ne $workspace) {
    $workspaceLf = $workspace.Replace("`r`n", "`n").Replace("`r", "`n")
    HasNot $workspaceLf "          }`n          return;`n          options.handleMediaSurfaceClick?.(event);" 'Media click is reachable'
}

$templatesRuntime = ReadRepo 'spring-panel\src\main\resources\static\js\dialogs-templates-runtime.js'
Has $templatesRuntime 'function bindEmojiPicker' 'Shared modal/workspace emoji picker'
Has $templatesRuntime 'textarea.setRangeText' 'Caret-aware emoji insertion'

$sidebar = ReadRepo 'spring-panel\src\main\resources\scss\sidebar\_sections.scss'
HasNot $sidebar '.sidebar-nav .nav-link:hover .nav-label-sub' 'Sidebar hover is spatially stable'
HasNot $sidebar '.sidebar-nav .nav-link:focus-visible .nav-label-sub' 'Sidebar focus is spatially stable'
Has $sidebar '.sidebar-nav .nav-link.active .nav-label-sub' 'Active sidebar secondary label remains'

if (-not $SkipBuild) {
    $panelDir = Join-Path $root 'spring-panel'
    $mvnw = Join-Path $panelDir 'mvnw.cmd'
    if (-not (Test-Path -LiteralPath $mvnw)) {
        Fail 'spring-panel\mvnw.cmd missing'
    } else {
        Push-Location $panelDir
        try {
            & $mvnw -q generate-resources
            if ($LASTEXITCODE -eq 0) { Pass 'Maven generate-resources' } else { Fail 'Maven generate-resources' }

            & $mvnw -q -DskipTests compile
            if ($LASTEXITCODE -eq 0) { Pass 'Maven compile' } else { Fail 'Maven compile' }

            & $mvnw -q -DskipTests test-compile
            if ($LASTEXITCODE -eq 0) { Pass 'Maven test-compile' } else { Fail 'Maven test-compile' }
        }
        finally {
            Pop-Location
        }

        $cssDiff = Git @('diff', '--exit-code', '--', 'spring-panel/src/main/resources/static/css')
        if ($cssDiff.Code -eq 0) { Pass 'Generated CSS is in sync' } else { Fail 'Generated CSS drift detected' }
    }
} else {
    Write-Host '[SKIP] Maven build checks'
}

Write-Host ''
Write-Host "PASS: $($passes.Count)"
Write-Host "FAIL: $($failures.Count)"

if ($failures.Count -gt 0) {
    Write-Host ''
    Write-Host 'Release-readiness gate FAILED.'
    foreach ($failure in $failures) { Write-Host "  - $failure" }
    exit 1
}

Write-Host ''
Write-Host 'Release-readiness gate PASSED.'
Write-Host 'Manual smoke remains: light/dark, 1366/1920/mobile, Dialogs media/reply/drop/paste/emoji, Settings resize/nested modals, Dashboard widgets, offline hard refresh.'
exit 0
