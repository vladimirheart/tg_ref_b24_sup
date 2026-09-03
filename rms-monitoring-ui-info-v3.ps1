param(
    [string]$RepoRoot = "C:\Users\SinicinVV\git_h\tg_ref_b24_sup",
    [string]$ExpectedMain = "d349750673e47d03b2f234c9f152127f736347b5",
    [string]$ProjectName = "tg_ref_b24_sup",
    [switch]$Apply,
    [switch]$ValidateOnly
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,
        [switch]$AllowFailure
    )

    $savedErrorAction = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $nativeOutput = @(& $FilePath @ArgumentList 2>&1)
        $nativeExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $savedErrorAction
    }

    if (($nativeExitCode -ne 0) -and (-not $AllowFailure)) {
        $nativeText = (($nativeOutput | ForEach-Object { [string]$_ }) -join "`n")
        throw ("Native command failed ({0}): {1} {2}`n{3}" -f `
            $nativeExitCode,
            $FilePath,
            ($ArgumentList -join " "),
            $nativeText)
    }

    return [pscustomobject]@{
        ExitCode = $nativeExitCode
        Output = @($nativeOutput)
    }
}

function Get-NativeText {
    param([object[]]$Output)
    return (($Output | ForEach-Object { [string]$_ }) -join "`n").Trim()
}

function Read-Utf8File {
    param([string]$Path)
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Write-Utf8NoBom {
    param(
        [string]$Path,
        [string]$Text
    )

    $encoding = New-Object -TypeName System.Text.UTF8Encoding -ArgumentList $false
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Convert-ToLf {
    param([string]$Text)

    if ($null -eq $Text) {
        return ""
    }

    return $Text.Replace("`r`n", "`n").Replace("`r", "`n")
}

function Get-Newline {
    param([string]$Text)

    if ($Text.Contains("`r`n")) {
        return "`r`n"
    }

    return "`n"
}

function Convert-FromLf {
    param(
        [string]$Text,
        [string]$Newline
    )

    if ($Newline -eq "`r`n") {
        return $Text.Replace("`n", "`r`n")
    }

    return $Text
}

function Replace-ExactOnce {
    param(
        [string]$Text,
        [string]$OldText,
        [string]$NewText,
        [string]$Label
    )

    $first = $Text.IndexOf($OldText, [System.StringComparison]::Ordinal)
    if ($first -lt 0) {
        throw "Expected source fragment missing: $Label"
    }

    $second = $Text.IndexOf(
        $OldText,
        $first + $OldText.Length,
        [System.StringComparison]::Ordinal
    )

    if ($second -ge 0) {
        throw "Expected exactly one source fragment: $Label"
    }

    return $Text.Substring(0, $first) +
        $NewText +
        $Text.Substring($first + $OldText.Length)
}

function Replace-RegexOnceLiteral {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Replacement,
        [string]$Label
    )

    $regexOptions = [System.Text.RegularExpressions.RegexOptions]::Multiline -bor `
        [System.Text.RegularExpressions.RegexOptions]::Singleline
    $regex = New-Object `
        -TypeName System.Text.RegularExpressions.Regex `
        -ArgumentList @($Pattern, $regexOptions)

    $matches = @($regex.Matches($Text))
    if ($matches.Count -ne 1) {
        throw ("Expected exactly one regex match for {0}; found {1}" -f `
            $Label,
            $matches.Count)
    }

    $match = $matches[0]
    return $Text.Substring(0, $match.Index) +
        $Replacement +
        $Text.Substring($match.Index + $match.Length)
}

function Get-ServiceIds {
    param([string]$Service)

    $result = Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "ps",
            "-q",
            "--filter", "label=com.docker.compose.project=$ProjectName",
            "--filter", "label=com.docker.compose.service=$Service"
        )

    return @(
        $result.Output |
            ForEach-Object { ([string]$_).Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Get-InspectObject {
    param([string]$ContainerId)

    $result = Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @("inspect", $ContainerId)

    $raw = Get-NativeText -Output $result.Output
    if ([string]::IsNullOrWhiteSpace($raw)) {
        throw "docker inspect returned empty output"
    }

    try {
        $items = @($raw | ConvertFrom-Json)
    }
    catch {
        throw "docker inspect returned invalid JSON"
    }

    if ($items.Count -ne 1) {
        throw "Expected exactly one docker inspect object"
    }

    return $items[0]
}

function Assert-ContainerHealthy {
    param(
        [string]$ContainerId,
        [string]$Service
    )

    $inspect = Get-InspectObject -ContainerId $ContainerId

    if ([string]$inspect.State.Status -cne "running") {
        throw "${Service}: container is not running"
    }

    if ($null -eq $inspect.State.Health) {
        throw "${Service}: container has no healthcheck state"
    }

    if ([string]$inspect.State.Health.Status -cne "healthy") {
        throw "${Service}: health=$($inspect.State.Health.Status)"
    }

    return $inspect
}

function Get-ComposeBaseArguments {
    param(
        [string]$ComposePath,
        [string]$EnvPath
    )

    $arguments = @(
        "compose",
        "-p", $ProjectName,
        "--project-directory", $RepoRoot
    )

    if (Test-Path -LiteralPath $EnvPath -PathType Leaf) {
        $arguments += @("--env-file", $EnvPath)
    }

    $arguments += @("-f", $ComposePath)
    return @($arguments)
}

function Recreate-PanelWeb {
    param(
        [string]$ComposePath,
        [string]$EnvPath,
        [string]$ImageName,
        [int]$ReplicaCount
    )

    $oldImageEnvironment = [Environment]::GetEnvironmentVariable(
        "IGUANA_PANEL_IMAGE",
        "Process"
    )

    try {
        [Environment]::SetEnvironmentVariable(
            "IGUANA_PANEL_IMAGE",
            $ImageName,
            "Process"
        )

        $arguments = @(Get-ComposeBaseArguments `
            -ComposePath $ComposePath `
            -EnvPath $EnvPath)

        $arguments += @(
            "up",
            "-d",
            "--no-deps",
            "--no-build",
            "--force-recreate",
            "--scale", "panel-web=$ReplicaCount",
            "panel-web"
        )

        Invoke-Native `
            -FilePath "docker.exe" `
            -ArgumentList $arguments |
            Out-Null
    }
    finally {
        [Environment]::SetEnvironmentVariable(
            "IGUANA_PANEL_IMAGE",
            $oldImageEnvironment,
            "Process"
        )
    }
}

function Wait-PanelWebHealthy {
    param(
        [int]$ExpectedCount,
        [int]$TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastState = ""

    while ((Get-Date) -lt $deadline) {
        $ids = @(Get-ServiceIds -Service "panel-web")

        if ($ids.Count -eq $ExpectedCount) {
            $allHealthy = $true

            foreach ($id in $ids) {
                $inspect = Get-InspectObject -ContainerId $id
                $health = "none"

                if ($null -ne $inspect.State.Health) {
                    $health = [string]$inspect.State.Health.Status
                }

                $lastState = "id=$id status=$($inspect.State.Status) health=$health"

                if (([string]$inspect.State.Status -cne "running") -or
                    ($health -cne "healthy")) {
                    $allHealthy = $false
                }
            }

            if ($allHealthy) {
                return @($ids)
            }
        }
        else {
            $lastState = "container-count=$($ids.Count)"
        }

        Start-Sleep -Seconds 5
    }

    throw "Timed out waiting for panel-web. Last state: $lastState"
}

function Build-UiContent {
    param(
        [string]$JavaScriptText,
        [string]$HtmlText
    )

    $jsNewline = Get-Newline -Text $JavaScriptText
    $htmlNewline = Get-Newline -Text $HtmlText
    $js = Convert-ToLf -Text $JavaScriptText
    $html = Convert-ToLf -Text $HtmlText

    $js = Replace-ExactOnce `
        -Text $js `
        -OldText "  const scheduleSummary = document.getElementById('rmsScheduleSummary');`n" `
        -NewText "" `
        -Label "scheduleSummary declaration"

    $schedulePattern = '^  function renderScheduleSettings\(\) \{\n    if \(!scheduleSummary \|\| !scheduleSettings\) return;\n    scheduleSummary\.textContent = `[^`]*`;\n  \}\n\n' 
    $js = Replace-RegexOnceLiteral `
        -Text $js `
        -Pattern $schedulePattern `
        -Replacement "" `
        -Label "renderScheduleSettings function"

    $js = Replace-ExactOnce `
        -Text $js `
        -OldText "      renderScheduleSettings();`n" `
        -NewText "" `
        -Label "renderScheduleSettings call"

    $queuePattern = '^  function renderQueueLine\(label, queue\) \{.*?^  \}\n\n(?=  function renderQueueState\(\))' 

    $queueReplacement = Convert-ToLf -Text @'
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

    return `
      <span
        class="text-secondary ms-1"
        role="img"
        tabindex="0"
        title="${tooltip}"
        aria-label="${escapeHtml(label)} info"
      >&#9432;</span>
    `;
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

    $js = Replace-RegexOnceLiteral `
        -Text $js `
        -Pattern $queuePattern `
        -Replacement $queueReplacement `
        -Label "renderQueueLine function"

    $htmlPattern = '\n                    <div class="small text-muted mb-2">\n                        <span id="rmsScheduleSummary">.*?</span>\n                    </div>' 
    $html = Replace-RegexOnceLiteral `
        -Text $html `
        -Pattern $htmlPattern `
        -Replacement "" `
        -Label "rmsScheduleSummary HTML block"

    if ($js.Contains("rmsScheduleSummary")) {
        throw "Transformed JavaScript still contains rmsScheduleSummary"
    }

    if ($html.Contains("rmsScheduleSummary")) {
        throw "Transformed HTML still contains rmsScheduleSummary"
    }

    if (-not $js.Contains("function renderQueueInfoButton")) {
        throw "Transformed JavaScript is missing renderQueueInfoButton"
    }

    return [pscustomobject]@{
        JavaScript = Convert-FromLf -Text $js -Newline $jsNewline
        Html = Convert-FromLf -Text $html -Newline $htmlNewline
    }
}

if ($Apply -and $ValidateOnly) {
    throw "Use either -Apply or -ValidateOnly"
}

if (-not $Apply) {
    $ValidateOnly = $true
}

if ($PSVersionTable.PSVersion.Major -ne 5) {
    throw "Run this script in Windows PowerShell 5.1"
}

Write-Host "=== RMS QUEUE UI INFO ==="
if ($Apply) {
    Write-Host "mode=APPLY"
}
else {
    Write-Host "mode=VALIDATE_ONLY"
}

$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
Set-Location -LiteralPath $RepoRoot

$jsRelative = "spring-panel/src/main/resources/static/js/rms-monitoring.js"
$htmlRelative = "spring-panel/src/main/resources/templates/analytics/rms-control.html"
$dockerRelative = "docker/panel.Dockerfile"
$composePath = Join-Path $RepoRoot "docker-compose.production-contour.yml"
$envPath = Join-Path $RepoRoot ".env"
$jsPath = Join-Path $RepoRoot ($jsRelative.Replace("/", "\"))
$htmlPath = Join-Path $RepoRoot ($htmlRelative.Replace("/", "\"))
$dockerPath = Join-Path $RepoRoot ($dockerRelative.Replace("/", "\"))

foreach ($requiredPath in @(
    $jsPath,
    $htmlPath,
    $dockerPath,
    $composePath
)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required file missing: $requiredPath"
    }
}

# Repository freshness gate.
Invoke-Native `
    -FilePath "git.exe" `
    -ArgumentList @(
        "fetch",
        "--quiet",
        "origin",
        "refs/heads/main:refs/remotes/origin/main"
    ) |
    Out-Null

$localHead = Get-NativeText -Output (
    Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @("rev-parse", "HEAD")
).Output

$originMain = Get-NativeText -Output (
    Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @("rev-parse", "origin/main")
).Output

Write-Host "[PRECHECK] local_head=$localHead"
Write-Host "[PRECHECK] origin_main=$originMain"
Write-Host "[PRECHECK] expected_main=$ExpectedMain"

if ($originMain -cne $ExpectedMain) {
    throw "STOP: origin/main changed; script assumptions must be reviewed again"
}

if ($localHead -cne $originMain) {
    throw "STOP: local HEAD differs from origin/main"
}

$statusResult = Invoke-Native `
    -FilePath "git.exe" `
    -ArgumentList @("status", "--porcelain", "--untracked-files=all")

$unexpectedStatus = @()
foreach ($rawLine in $statusResult.Output) {
    $line = [string]$rawLine
    $allowed = (
        ($line -match '^\?\? run/') -or
        ($line -match '^\?\? fix-rms-monitoring-runtime-tools\.ps1$') -or
        ($line -match '^\?\? rms-monitoring-ui-info-v[0-9]+\.ps1$')
    )

    if (-not $allowed) {
        $unexpectedStatus += $line
    }
}

if ($unexpectedStatus.Count -gt 0) {
    Write-Host "[RED] unexpected repository state:"
    foreach ($line in $unexpectedStatus) {
        Write-Host "  $line"
    }
    throw "STOP: repository has unexpected local changes"
}

foreach ($target in @($jsRelative, $htmlRelative, $dockerRelative)) {
    $diffResult = Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @("diff", "--quiet", "origin/main", "--", $target) `
        -AllowFailure

    if ($diffResult.ExitCode -ne 0) {
        throw "STOP: target file already differs from origin/main: $target"
    }
}

$dockerText = Read-Utf8File -Path $dockerPath
foreach ($requiredText in @(
    "iputils-ping",
    "traceroute",
    "command -v ping >/dev/null",
    "command -v traceroute >/dev/null"
)) {
    if (-not $dockerText.Contains($requiredText)) {
        throw "STOP: Dockerfile is missing reviewed runtime tool guard: $requiredText"
    }
}

# Live production gate.
$workerIds = @(Get-ServiceIds -Service "ops-worker")
$webIds = @(Get-ServiceIds -Service "panel-web")

if ($workerIds.Count -ne 1) {
    throw "Expected exactly one ops-worker, found $($workerIds.Count)"
}

if ($webIds.Count -lt 1) {
    throw "Expected at least one panel-web container"
}

$workerInspect = Assert-ContainerHealthy `
    -ContainerId $workerIds[0] `
    -Service "ops-worker"

Invoke-Native `
    -FilePath "docker.exe" `
    -ArgumentList @(
        "exec",
        $workerIds[0],
        "sh",
        "-lc",
        "command -v ping >/dev/null && command -v traceroute >/dev/null"
    ) |
    Out-Null

$oldWebImageId = ""
$currentImageName = ""

foreach ($webId in $webIds) {
    $inspect = Assert-ContainerHealthy `
        -ContainerId $webId `
        -Service "panel-web"

    if ([string]::IsNullOrWhiteSpace($oldWebImageId)) {
        $oldWebImageId = [string]$inspect.Image
        $currentImageName = [string]$inspect.Config.Image
    }
    elseif ([string]$inspect.Image -cne $oldWebImageId) {
        throw "STOP: panel-web replicas use different image IDs"
    }

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "exec",
            $webId,
            "sh",
            "-lc",
            "command -v ping >/dev/null && command -v traceroute >/dev/null"
        ) |
        Out-Null
}

if ([string]::IsNullOrWhiteSpace($currentImageName)) {
    throw "Unable to resolve current panel-web image name"
}

Write-Host "[PRECHECK] ops_worker=$($workerIds[0])"
Write-Host "[PRECHECK] panel_web_count=$($webIds.Count)"
Write-Host "[PRECHECK] panel_web_image=$currentImageName"
Write-Host "[PRECHECK] panel_web_image_id=$oldWebImageId"

$composeArguments = @(Get-ComposeBaseArguments `
    -ComposePath $composePath `
    -EnvPath $envPath)
$composeArguments += @("config", "-q")

Invoke-Native `
    -FilePath "docker.exe" `
    -ArgumentList $composeArguments |
    Out-Null

$originalJavaScript = Read-Utf8File -Path $jsPath
$originalHtml = Read-Utf8File -Path $htmlPath
$transformed = Build-UiContent `
    -JavaScriptText $originalJavaScript `
    -HtmlText $originalHtml

Write-Host "[GREEN] read-only repository/live/UI transform preflight passed"

if ($ValidateOnly) {
    Write-Host "[VALIDATE_ONLY] Nothing was changed"
    exit 0
}

$timestamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$candidateTag = "iguana-panel-rms-queue-ui:$timestamp"
$changelogPath = Join-Path `
    $RepoRoot `
    ("ai-context\changelog\{0}_rms-queue-info-ui.md" -f $timestamp)

$sourceChanged = $false
$changelogCreated = $false
$imageTagChanged = $false
$webRecreateStarted = $false

try {
    Write-Utf8NoBom -Path $jsPath -Text $transformed.JavaScript
    Write-Utf8NoBom -Path $htmlPath -Text $transformed.Html
    $sourceChanged = $true

    $changelogText = @(
        "# RMS queue info UI compaction",
        "",
        "- Base main: $ExpectedMain",
        "- Time: $timestamp",
        "",
        "## Change",
        "",
        "- Remove the always-visible schedule summary line.",
        "- Keep queue state, progress and current RMS visible.",
        "- Move interval, queue gap, last requested time and last completed time into an info icon tooltip for each queue.",
        "- Backend queue semantics are unchanged.",
        "- Redeploy panel-web only; ops-worker is not recreated.",
        ""
    ) -join "`r`n"

    Write-Utf8NoBom `
        -Path $changelogPath `
        -Text ($changelogText + "`r`n")
    $changelogCreated = $true

    Write-Host "[APPLY] source files updated"
    Write-Host "[APPLY] changelog=$changelogPath"
    Write-Host "[BUILD] candidate=$candidateTag"

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "build",
            "--file", $dockerPath,
            "--tag", $candidateTag,
            $RepoRoot
        ) |
        Out-Null

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "run",
            "--rm",
            "--entrypoint", "sh",
            $candidateTag,
            "-lc",
            "command -v ping >/dev/null && command -v traceroute >/dev/null"
        ) |
        Out-Null

    Write-Host "[GREEN] candidate runtime smoke passed"

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @("tag", $candidateTag, $currentImageName) |
        Out-Null
    $imageTagChanged = $true

    Write-Host "[DEPLOY] recreating panel-web only"
    $webRecreateStarted = $true

    Recreate-PanelWeb `
        -ComposePath $composePath `
        -EnvPath $envPath `
        -ImageName $currentImageName `
        -ReplicaCount $webIds.Count

    $newWebIds = @(Wait-PanelWebHealthy `
        -ExpectedCount $webIds.Count `
        -TimeoutSeconds 240)

    foreach ($id in $newWebIds) {
        Invoke-Native `
            -FilePath "docker.exe" `
            -ArgumentList @(
                "exec",
                $id,
                "sh",
                "-lc",
                "command -v ping >/dev/null && command -v traceroute >/dev/null && curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'function renderQueueInfoButton' && curl -fsS http://localhost:8080/analytics/rms-control >/dev/null"
            ) |
            Out-Null

        $oldJsProbe = Invoke-Native `
            -FilePath "docker.exe" `
            -ArgumentList @(
                "exec",
                $id,
                "sh",
                "-lc",
                "curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'rmsScheduleSummary'"
            ) `
            -AllowFailure

        if ($oldJsProbe.ExitCode -eq 0) {
            throw "Deployed JavaScript still contains rmsScheduleSummary"
        }

        Write-Host "[GREEN] panel-web=$id healthy and serves compact RMS queue UI"
    }

    $gitStatus = Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @(
            "status",
            "--short",
            "--",
            $jsRelative,
            $htmlRelative,
            "ai-context/changelog"
        )

    Write-Host ""
    Write-Host "=== SUCCESS ==="
    Write-Host "Only panel-web was recreated"
    Write-Host "ops-worker was not touched"
    Write-Host "Repository UI changes are left uncommitted"
    Write-Host "[GIT STATUS]"

    foreach ($line in $gitStatus.Output) {
        Write-Host "  $line"
    }
}
catch {
    $caught = $_

    Write-Host ""
    Write-Host "[RED] UI deployment failed: $($caught.Exception.Message)"
    Write-Host "[ROLLBACK] starting"

    $rollbackErrors = New-Object System.Collections.Generic.List[string]

    try {
        if ($imageTagChanged) {
            Invoke-Native `
                -FilePath "docker.exe" `
                -ArgumentList @("tag", $oldWebImageId, $currentImageName) |
                Out-Null
            Write-Host "[ROLLBACK] image tag restored"
        }
    }
    catch {
        $rollbackErrors.Add(
            "image tag restore failed: $($_.Exception.Message)"
        )
    }

    try {
        if ($webRecreateStarted) {
            Recreate-PanelWeb `
                -ComposePath $composePath `
                -EnvPath $envPath `
                -ImageName $currentImageName `
                -ReplicaCount $webIds.Count

            Wait-PanelWebHealthy `
                -ExpectedCount $webIds.Count `
                -TimeoutSeconds 240 |
                Out-Null

            Write-Host "[ROLLBACK] panel-web restored and healthy"
        }
    }
    catch {
        $rollbackErrors.Add(
            "panel-web rollback failed: $($_.Exception.Message)"
        )
    }

    try {
        if ($sourceChanged) {
            Write-Utf8NoBom -Path $jsPath -Text $originalJavaScript
            Write-Utf8NoBom -Path $htmlPath -Text $originalHtml
            Write-Host "[ROLLBACK] source files restored"
        }

        if ($changelogCreated -and
            (Test-Path -LiteralPath $changelogPath -PathType Leaf)) {
            Remove-Item -LiteralPath $changelogPath -Force
            Write-Host "[ROLLBACK] changelog removed"
        }
    }
    catch {
        $rollbackErrors.Add(
            "source rollback failed: $($_.Exception.Message)"
        )
    }

    if ($rollbackErrors.Count -gt 0) {
        Write-Host "[RED] rollback had errors:"
        foreach ($rollbackError in $rollbackErrors) {
            Write-Host "  $rollbackError"
        }
    }
    else {
        Write-Host "[GREEN] rollback completed"
    }

    throw $caught
}
