param(
    [string]$RepoRoot = "C:\Users\SinicinVV\git_h\tg_ref_b24_sup",
    [string]$ExpectedMain = "83ab582e691eef8b24d5db30bf14eef855728187",
    [string]$ProjectName = "tg_ref_b24_sup",
    [switch]$Apply
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

function Run-Native {
    param([string]$File, [string[]]$NativeArgs, [switch]$AllowFailure)
    $saved = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $out = @(& $File @NativeArgs 2>&1)
        $code = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $saved
    }
    if (($code -ne 0) -and (-not $AllowFailure)) {
        throw ("Native command failed ({0}): {1} {2}`n{3}" -f $code, $File, ($NativeArgs -join " "), (($out | ForEach-Object { [string]$_ }) -join "`n"))
    }
    return [pscustomobject]@{ ExitCode = $code; Output = @($out) }
}

function Text($result) {
    return (($result.Output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Read-Utf8([string]$Path) {
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8([string]$Path, [string]$Value) {
    $enc = New-Object -TypeName System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($Path, $Value, $enc)
}

function Get-ServiceIds([string]$Service) {
    $r = Run-Native "docker.exe" @(
        "ps", "-q",
        "--filter", "label=com.docker.compose.project=$ProjectName",
        "--filter", "label=com.docker.compose.service=$Service"
    )
    return @($r.Output | ForEach-Object { ([string]$_).Trim() } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Inspect([string]$Id) {
    $r = Run-Native "docker.exe" @("inspect", $Id)
    $raw = Text $r
    $items = @($raw | ConvertFrom-Json)
    if ($items.Count -ne 1) { throw "Expected one inspect object" }
    return $items[0]
}

function Assert-Healthy([string]$Id, [string]$Service) {
    $i = Inspect $Id
    if ($i.State.Status -ne "running") { throw "${Service}: not running" }
    if (($null -eq $i.State.Health) -or ($i.State.Health.Status -ne "healthy")) {
        throw "${Service}: not healthy"
    }
    return $i
}

function Compose-Base([string]$Compose, [string]$EnvFile) {
    $a = @("compose", "-p", $ProjectName, "--project-directory", $RepoRoot)
    if (Test-Path -LiteralPath $EnvFile -PathType Leaf) { $a += @("--env-file", $EnvFile) }
    $a += @("-f", $Compose)
    return @($a)
}

function Recreate-Web([string]$Compose, [string]$EnvFile, [string]$Image, [int]$Count) {
    $old = [Environment]::GetEnvironmentVariable("IGUANA_PANEL_IMAGE", "Process")
    try {
        [Environment]::SetEnvironmentVariable("IGUANA_PANEL_IMAGE", $Image, "Process")
        $a = @(Compose-Base $Compose $EnvFile)
        $a += @("up", "-d", "--no-deps", "--no-build", "--force-recreate", "--scale", "panel-web=$Count", "panel-web")
        Run-Native "docker.exe" $a | Out-Null
    }
    finally {
        [Environment]::SetEnvironmentVariable("IGUANA_PANEL_IMAGE", $old, "Process")
    }
}

function Wait-Web([int]$Count) {
    $deadline = (Get-Date).AddMinutes(4)
    while ((Get-Date) -lt $deadline) {
        $ids = @(Get-ServiceIds "panel-web")
        if ($ids.Count -eq $Count) {
            $ok = $true
            foreach ($id in $ids) {
                $i = Inspect $id
                if (($i.State.Status -ne "running") -or ($null -eq $i.State.Health) -or ($i.State.Health.Status -ne "healthy")) { $ok = $false }
            }
            if ($ok) { return @($ids) }
        }
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for panel-web"
}

if ($PSVersionTable.PSVersion.Major -ne 5) { throw "Run with Windows PowerShell 5.1" }
if (-not $Apply) { throw "Use -Apply after parser check" }

$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
Set-Location -LiteralPath $RepoRoot

$jsRel = "spring-panel/src/main/resources/static/js/rms-monitoring.js"
$htmlRel = "spring-panel/src/main/resources/templates/analytics/rms-control.html"
$dockerRel = "docker/panel.Dockerfile"
$js = Join-Path $RepoRoot ($jsRel.Replace("/", "\"))
$html = Join-Path $RepoRoot ($htmlRel.Replace("/", "\"))
$dockerfile = Join-Path $RepoRoot ($dockerRel.Replace("/", "\"))
$compose = Join-Path $RepoRoot "docker-compose.production-contour.yml"
$envFile = Join-Path $RepoRoot ".env"

Run-Native "git.exe" @("fetch", "--quiet", "origin", "refs/heads/main:refs/remotes/origin/main") | Out-Null
$head = Text (Run-Native "git.exe" @("rev-parse", "HEAD"))
$originMain = Text (Run-Native "git.exe" @("rev-parse", "origin/main"))
Write-Host "[PRECHECK] head=$head"
Write-Host "[PRECHECK] origin_main=$originMain"
if ($originMain -cne $ExpectedMain) { throw "STOP: origin/main changed" }
if ($head -cne $originMain) { throw "STOP: local HEAD differs from origin/main" }

$status = Run-Native "git.exe" @("status", "--porcelain", "--untracked-files=all")
$unexpected = @()
foreach ($rawLine in $status.Output) {
    $line = [string]$rawLine
    $allowed = (
        ($line -eq " M docker/panel.Dockerfile") -or
        ($line -match '^\?\? run/') -or
        ($line -match '^\?\? fix-rms-monitoring-runtime-tools\.ps1$') -or
        ($line -match '^\?\? rms-monitoring-ui-info\.ps1$') -or
        ($line -match '^\?\? ai-context/changelog/2026-09-03_[0-9]+_rms-monitoring-runtime-tools\.md$')
    )
    if (-not $allowed) { $unexpected += $line }
}
if ($unexpected.Count -gt 0) {
    Write-Host "[RED] unexpected repository state:"
    foreach ($line in $unexpected) { Write-Host "  $line" }
    throw "STOP: unexpected repository changes exist"
}

$jsDiff = Run-Native "git.exe" @("diff", "--quiet", "origin/main", "--", $jsRel) -AllowFailure
if ($jsDiff.ExitCode -ne 0) { throw "STOP: rms-monitoring.js already has local changes" }
$htmlDiff = Run-Native "git.exe" @("diff", "--quiet", "origin/main", "--", $htmlRel) -AllowFailure
if ($htmlDiff.ExitCode -ne 0) { throw "STOP: rms-control.html already has local changes" }

$dockerText = Read-Utf8 $dockerfile
$baseDockerResult = Run-Native "git.exe" @("show", "origin/main:$dockerRel")
$baseDocker = (($baseDockerResult.Output | ForEach-Object { [string]$_ }) -join "`n") + "`n"
$oldInstallLine = '    && apt-get install -y --no-install-recommends ca-certificates curl \'
$fixedInstallBlock = @'
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        iputils-ping \
        traceroute \
    && command -v ping >/dev/null \
    && command -v traceroute >/dev/null \
'@
$expectedDocker = $baseDocker.Replace($oldInstallLine, $fixedInstallBlock.TrimEnd("`r", "`n"))
$dockerNorm = $dockerText.Replace("`r`n", "`n").TrimEnd()
$expectedDockerNorm = $expectedDocker.Replace("`r`n", "`n").TrimEnd()
if ($dockerNorm -cne $expectedDockerNorm) {
    throw "STOP: Dockerfile differs from the reviewed ping/traceroute fix"
}

$workerIds = @(Get-ServiceIds "ops-worker")
$webIds = @(Get-ServiceIds "panel-web")
if ($workerIds.Count -ne 1) { throw "Expected one ops-worker, found $($workerIds.Count)" }
if ($webIds.Count -lt 1) { throw "No panel-web containers found" }
Assert-Healthy $workerIds[0] "ops-worker" | Out-Null
Run-Native "docker.exe" @("exec", $workerIds[0], "sh", "-lc", "command -v ping >/dev/null && command -v traceroute >/dev/null") | Out-Null

$oldImageId = ""
$imageName = ""
foreach ($id in $webIds) {
    $i = Assert-Healthy $id "panel-web"
    if ([string]::IsNullOrWhiteSpace($oldImageId)) {
        $oldImageId = [string]$i.Image
        $imageName = [string]$i.Config.Image
    }
    elseif ([string]$i.Image -cne $oldImageId) {
        throw "STOP: panel-web replicas use different image IDs"
    }
    Run-Native "docker.exe" @("exec", $id, "sh", "-lc", "command -v ping >/dev/null && command -v traceroute >/dev/null") | Out-Null
}

$a = @(Compose-Base $compose $envFile)
$a += @("config", "-q")
Run-Native "docker.exe" $a | Out-Null
Write-Host "[GREEN] live/repo preflight passed"

$oldJs = Read-Utf8 $js
$oldHtml = Read-Utf8 $html
$newJs = $oldJs
$newHtml = $oldHtml

$rx = New-Object System.Text.RegularExpressions.Regex("(?m)^  const scheduleSummary = document\.getElementById\('rmsScheduleSummary'\);\r?\n")
if ($rx.Matches($newJs).Count -ne 1) { throw "Expected one scheduleSummary declaration" }
$newJs = $rx.Replace($newJs, "", 1)

$rx = New-Object System.Text.RegularExpressions.Regex("(?ms)^  function renderScheduleSettings\(\) \{\r?\n    if \(!scheduleSummary \|\| !scheduleSettings\) return;\r?\n    scheduleSummary\.textContent = .*?;\r?\n  \}\r?\n\r?\n")
if ($rx.Matches($newJs).Count -ne 1) { throw "Expected one renderScheduleSettings function" }
$newJs = $rx.Replace($newJs, "", 1)

$rx = New-Object System.Text.RegularExpressions.Regex("(?m)^      renderScheduleSettings\(\);\r?\n")
if ($rx.Matches($newJs).Count -ne 1) { throw "Expected one renderScheduleSettings call" }
$newJs = $rx.Replace($newJs, "", 1)

$rx = New-Object System.Text.RegularExpressions.Regex("(?ms)^  function renderQueueLine\(label, queue\) \{.*?^  \}\r?\n\r?\n(?=  function renderQueueState\(\))")
if ($rx.Matches($newJs).Count -ne 1) { throw "Expected one renderQueueLine function" }

$replacement = @'
  function renderQueueInfoButton(label, queue) {
    const isLicenseQueue = queue === refreshState?.licenses;
    const intervalValue = isLicenseQueue
      ? scheduleSettings?.license_interval_minutes
      : scheduleSettings?.network_interval_minutes;
    const interval = Number(intervalValue);
    const gap = Number(scheduleSettings?.queue_gap_seconds);
    const intervalText = Number.isFinite(interval) && interval > 0
      ? `${interval} \u043c\u0438\u043d.`
      : '\u2014';
    const gapText = Number.isFinite(gap) && gap >= 0
      ? `${gap} \u0441.`
      : '\u2014';
    const tooltip = [
      label,
      `\u0418\u043d\u0442\u0435\u0440\u0432\u0430\u043b: ${intervalText}`,
      `\u041f\u0430\u0443\u0437\u0430 \u043e\u0447\u0435\u0440\u0435\u0434\u0438: ${gapText}`,
      `\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0438\u0439 \u0437\u0430\u043f\u0443\u0441\u043a: ${formatDateTime(queue?.last_requested_at)}`,
      `\u041f\u043e\u0441\u043b\u0435\u0434\u043d\u0435\u0435 \u0437\u0430\u0432\u0435\u0440\u0448\u0435\u043d\u0438\u0435: ${formatDateTime(queue?.last_completed_at)}`,
    ].map(escapeHtml).join('&#10;');

    return `<span class="text-secondary ms-1" role="img" tabindex="0" title="${tooltip}" aria-label="${escapeHtml(label)} details">&#9432;</span>`;
  }

  function renderQueueLine(label, queue) {
    const running = Boolean(queue?.running);
    const queued = Boolean(queue?.queued);
    const total = Number(queue?.total_count || 0);
    const completed = Number(queue?.completed_count || 0);
    const currentMonitorId = queue?.current_monitor_id;
    const progressText = total > 0 ? `${completed}/${total}` : '\u2014';
    const currentText = currentMonitorId
      ? `\u0441\u0435\u0439\u0447\u0430\u0441: ${findSiteName(currentMonitorId)}`
      : '\u0441\u0435\u0439\u0447\u0430\u0441: \u2014';
    return `
      <div class="d-flex flex-wrap align-items-center gap-2">
        <strong>${escapeHtml(label)}:</strong>
        ${queueBadge(running, queued)}
        <span>\u043f\u0440\u043e\u0433\u0440\u0435\u0441\u0441: ${escapeHtml(progressText)}</span>
        <span>${escapeHtml(currentText)}</span>
        ${renderQueueInfoButton(label, queue)}
      </div>
    `;
  }

'@
$newJs = $rx.Replace($newJs, $replacement, 1)

$rx = New-Object System.Text.RegularExpressions.Regex('(?ms)\s*<div class="small text-muted mb-2">\s*<span id="rmsScheduleSummary">.*?</span>\s*</div>')
if ($rx.Matches($newHtml).Count -ne 1) { throw "Expected one rmsScheduleSummary HTML block" }
$newHtml = $rx.Replace($newHtml, "", 1)

if ($newJs.Contains("rmsScheduleSummary")) { throw "Transformed JS still contains rmsScheduleSummary" }
if ($newHtml.Contains("rmsScheduleSummary")) { throw "Transformed HTML still contains rmsScheduleSummary" }
if (-not $newJs.Contains("function renderQueueInfoButton")) { throw "Queue info renderer missing" }
Write-Host "[GREEN] UI transform validation passed"

$timestamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$candidate = "iguana-panel-rms-queue-ui:$timestamp"
$changelog = Join-Path $RepoRoot ("ai-context\changelog\{0}_rms-queue-info-ui.md" -f $timestamp)
$filesChanged = $false
$tagChanged = $false
$webChanged = $false

try {
    Write-Utf8 $js $newJs
    Write-Utf8 $html $newHtml
    $filesChanged = $true

    $cl = @(
        "# RMS queue info UI compaction",
        "",
        "- Base main: $ExpectedMain",
        "- Time: $timestamp",
        "",
        "## Change",
        "",
        "- Remove the always-visible schedule summary line.",
        "- Keep queue state, progress and current RMS visible.",
        "- Move interval, queue gap, last requested time and last completed time into an info icon tooltip per queue.",
        "- Backend queue behavior is unchanged.",
        "- Redeploy panel-web only; ops-worker is not recreated.",
        ""
    ) -join "`r`n"
    Write-Utf8 $changelog $cl

    Write-Host "[BUILD] candidate=$candidate"
    Run-Native "docker.exe" @("build", "--file", $dockerfile, "--tag", $candidate, $RepoRoot) | Out-Null
    Run-Native "docker.exe" @("run", "--rm", "--entrypoint", "sh", $candidate, "-lc", "command -v ping >/dev/null && command -v traceroute >/dev/null") | Out-Null
    Write-Host "[GREEN] candidate image smoke passed"

    Run-Native "docker.exe" @("tag", $candidate, $imageName) | Out-Null
    $tagChanged = $true
    $webChanged = $true

    Write-Host "[DEPLOY] recreating panel-web only"
    Recreate-Web $compose $envFile $imageName $webIds.Count
    $newWebIds = @(Wait-Web $webIds.Count)

    foreach ($id in $newWebIds) {
        Run-Native "docker.exe" @("exec", $id, "sh", "-lc", "command -v ping >/dev/null && command -v traceroute >/dev/null && curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'function renderQueueInfoButton'") | Out-Null
        $oldProbe = Run-Native "docker.exe" @("exec", $id, "sh", "-lc", "curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'rmsScheduleSummary'") -AllowFailure
        if ($oldProbe.ExitCode -eq 0) { throw "Deployed JS still contains old schedule summary code" }
        Write-Host "[GREEN] panel-web=$id healthy and serves compact RMS queue UI"
    }

    Write-Host ""
    Write-Host "=== SUCCESS ==="
    Write-Host "Only panel-web was recreated; ops-worker was not touched."
    Run-Native "git.exe" @("status", "--short", "--", $jsRel, $htmlRel, $dockerRel, "ai-context/changelog") | ForEach-Object {
        foreach ($line in $_.Output) { Write-Host "  $line" }
    }
}
catch {
    $err = $_
    Write-Host "[RED] failed: $($err.Exception.Message)"
    Write-Host "[ROLLBACK] starting"
    try {
        if ($tagChanged) { Run-Native "docker.exe" @("tag", $oldImageId, $imageName) | Out-Null }
        if ($webChanged) {
            Recreate-Web $compose $envFile $imageName $webIds.Count
            Wait-Web $webIds.Count | Out-Null
        }
        if ($filesChanged) {
            Write-Utf8 $js $oldJs
            Write-Utf8 $html $oldHtml
        }
        if (Test-Path -LiteralPath $changelog -PathType Leaf) { Remove-Item -LiteralPath $changelog -Force }
        Write-Host "[GREEN] rollback completed"
    }
    catch {
        Write-Host "[RED] rollback problem: $($_.Exception.Message)"
    }
    throw $err
}
