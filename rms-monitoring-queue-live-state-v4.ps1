param(
    [string]$RepoRoot = "C:\Users\SinicinVV\git_h\tg_ref_b24_sup",
    [string]$ExpectedMain = "ae0650a8b583aef64937f4e9ec8d342226393fd7",
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

    $saved = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $output = @(& $FilePath @ArgumentList 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $saved
    }

    if (($exitCode -ne 0) -and (-not $AllowFailure)) {
        $text = (($output | ForEach-Object { [string]$_ }) -join "`n")
        throw ("Native command failed ({0}): {1} {2}`n{3}" -f `
            $exitCode, $FilePath, ($ArgumentList -join " "), $text)
    }

    return [pscustomobject]@{
        ExitCode = $exitCode
        Output = @($output)
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

    $encoding = New-Object `
        -TypeName System.Text.UTF8Encoding `
        -ArgumentList $false

    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Normalize-Lf {
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

function Restore-Newline {
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

function Replace-RegexOnce {
    param(
        [string]$Text,
        [string]$Pattern,
        [string]$Replacement,
        [string]$Label
    )

    $options = [System.Text.RegularExpressions.RegexOptions]::Multiline -bor `
        [System.Text.RegularExpressions.RegexOptions]::Singleline

    $regex = New-Object `
        -TypeName System.Text.RegularExpressions.Regex `
        -ArgumentList @($Pattern, $options)

    $matches = @($regex.Matches($Text))

    if ($matches.Count -ne 1) {
        throw ("Expected exactly one regex match for {0}; found {1}" -f `
            $Label, $matches.Count)
    }

    $match = $matches[0]

    return $Text.Substring(0, $match.Index) +
        $Replacement +
        $Text.Substring($match.Index + $match.Length)
}

function Get-GitFileText {
    param(
        [string]$Ref,
        [string]$RelativePath
    )

    $result = Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @("show", "${Ref}:$RelativePath")

    return (($result.Output | ForEach-Object { [string]$_ }) -join "`n") + "`n"
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

function Assert-Healthy {
    param(
        [string]$ContainerId,
        [string]$Service
    )

    $inspect = Get-InspectObject -ContainerId $ContainerId

    if ([string]$inspect.State.Status -cne "running") {
        throw "${Service}: container is not running"
    }

    if ($null -eq $inspect.State.Health) {
        throw "${Service}: container has no health state"
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

function Recreate-Service {
    param(
        [string]$ComposePath,
        [string]$EnvPath,
        [string]$ImageName,
        [string]$Service,
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
            "--scale", "${Service}=$ReplicaCount",
            $Service
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

function Wait-ServiceHealthy {
    param(
        [string]$Service,
        [int]$ExpectedCount,
        [int]$TimeoutSeconds = 240
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    $lastState = ""

    while ((Get-Date) -lt $deadline) {
        $ids = @(Get-ServiceIds -Service $Service)

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

    throw "Timed out waiting for ${Service}. Last state: $lastState"
}

function Get-PostgresContext {
    $ids = @(Get-ServiceIds -Service "postgres")

    if ($ids.Count -ne 1) {
        throw "Expected exactly one postgres container, found $($ids.Count)"
    }

    $inspect = Assert-Healthy -ContainerId $ids[0] -Service "postgres"
    $user = ""
    $database = ""

    foreach ($entry in @($inspect.Config.Env)) {
        $line = [string]$entry

        if ($line -like "POSTGRES_USER=*") {
            $user = $line.Substring("POSTGRES_USER=".Length)
        }

        if ($line -like "POSTGRES_DB=*") {
            $database = $line.Substring("POSTGRES_DB=".Length)
        }
    }

    if ([string]::IsNullOrWhiteSpace($user)) {
        throw "POSTGRES_USER not found"
    }

    if ([string]::IsNullOrWhiteSpace($database)) {
        throw "POSTGRES_DB not found"
    }

    return [pscustomobject]@{
        Id = $ids[0]
        User = $user
        Database = $database
    }
}

function Invoke-Psql {
    param(
        [pscustomobject]$Context,
        [string]$Sql,
        [switch]$TuplesOnly
    )

    $arguments = @(
        "exec",
        $Context.Id,
        "psql",
        "-v", "ON_ERROR_STOP=1",
        "-P", "pager=off",
        "-U", $Context.User,
        "-d", $Context.Database
    )

    if ($TuplesOnly) {
        $arguments += @("-qAt")
    }

    $arguments += @("-c", $Sql)

    return Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList $arguments
}

function Recover-RmsCommandsForWorker {
    param(
        [pscustomobject]$Postgres,
        [string]$DeadWorker
    )

    if ([string]::IsNullOrWhiteSpace($DeadWorker)) {
        return @()
    }

    $safeWorker = $DeadWorker.Replace("'", "''")

    $sql = @"
WITH recovered AS (
    UPDATE backend_ops_command
       SET status = 'queued',
           claimed_by = NULL,
           claimed_at = NULL,
           heartbeat_at = NULL,
           available_at = now(),
           progress_message = 'Recovered after targeted ops-worker recreate',
           updated_at = now()
     WHERE status = 'running'
       AND claimed_by = '$safeWorker'
       AND command_type IN (
           'rms.network.refresh',
           'rms.license.refresh'
       )
     RETURNING command_id
)
SELECT command_id
FROM recovered;
"@

    $result = Invoke-Psql `
        -Context $Postgres `
        -Sql $sql `
        -TuplesOnly

    return @(
        $result.Output |
            ForEach-Object { ([string]$_).Trim() } |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    )
}

function Build-V3Expected {
    param(
        [string]$BaseJavaScript,
        [string]$BaseHtml
    )

    $js = Normalize-Lf -Text $BaseJavaScript
    $html = Normalize-Lf -Text $BaseHtml

    $js = Replace-ExactOnce `
        -Text $js `
        -OldText "  const scheduleSummary = document.getElementById('rmsScheduleSummary');`n" `
        -NewText "" `
        -Label "v3 scheduleSummary declaration"

    $schedulePattern = '^  function renderScheduleSettings\(\) \{\n    if \(!scheduleSummary \|\| !scheduleSettings\) return;\n    scheduleSummary\.textContent = `[^`]*`;\n  \}\n\n'

    $js = Replace-RegexOnce `
        -Text $js `
        -Pattern $schedulePattern `
        -Replacement "" `
        -Label "v3 renderScheduleSettings function"

    $js = Replace-ExactOnce `
        -Text $js `
        -OldText "      renderScheduleSettings();`n" `
        -NewText "" `
        -Label "v3 renderScheduleSettings call"

    $queuePattern = '^  function renderQueueLine\(label, queue\) \{.*?^  \}\n\n(?=  function renderQueueState\(\))'

    $queueReplacement = @'
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

    $js = Replace-RegexOnce `
        -Text $js `
        -Pattern $queuePattern `
        -Replacement $queueReplacement `
        -Label "v3 renderQueueLine"

    $htmlPattern = '\n                    <div class="small text-muted mb-2">\n                        <span id="rmsScheduleSummary">.*?</span>\n                    </div>'

    $html = Replace-RegexOnce `
        -Text $html `
        -Pattern $htmlPattern `
        -Replacement "" `
        -Label "v3 rmsScheduleSummary HTML"

    return [pscustomobject]@{
        JavaScript = $js
        Html = $html
    }
}

function Build-V4JavaScript {
    param([string]$CurrentJavaScript)

    $js = Normalize-Lf -Text $CurrentJavaScript

    $badgePattern = '^  function queueBadge\(running, queued\) \{.*?^  \}\n\n(?=  function getCookieValue\(name\))'

    $badgeReplacement = @'
  function queueBadge(queue) {
    const phase = normalizeStatus(queue?.phase);

    if (phase === 'checking') {
      return '<span class="badge text-bg-primary">\u0412 \u0440\u0430\u0431\u043e\u0442\u0435</span>';
    }
    if (phase === 'waiting_gap') {
      return '<span class="badge text-bg-secondary">\u041f\u0430\u0443\u0437\u0430</span>';
    }
    if (phase === 'queued' || queue?.queued) {
      return '<span class="badge text-bg-warning">\u0412 \u043e\u0447\u0435\u0440\u0435\u0434\u0438</span>';
    }
    if (phase === 'starting') {
      return '<span class="badge text-bg-info">\u0417\u0430\u043f\u0443\u0441\u043a</span>';
    }
    if (phase === 'finishing') {
      return '<span class="badge text-bg-info">\u0417\u0430\u0432\u0435\u0440\u0448\u0430\u0435\u043c</span>';
    }
    if (queue?.running) {
      return '<span class="badge text-bg-primary">\u0412 \u0440\u0430\u0431\u043e\u0442\u0435</span>';
    }

    return '<span class="badge text-bg-secondary">\u041e\u0436\u0438\u0434\u0430\u043d\u0438\u0435</span>';
  }

'@

    $js = Replace-RegexOnce `
        -Text $js `
        -Pattern $badgePattern `
        -Replacement $badgeReplacement `
        -Label "queueBadge"

    $queuePattern = '^  function renderQueueInfoButton\(label, queue\) \{.*?^  \}\n\n  function renderQueueLine\(label, queue\) \{.*?^  \}\n\n(?=  function renderQueueState\(\))'

    $queueReplacement = @'
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
      <button
        type="button"
        class="page-header-info__toggle ms-1"
        title="${tooltip}"
        aria-label="${escapeHtml(label)} info"
      >i</button>
    `;
  }

  function queueActivityText(queue) {
    const phase = normalizeStatus(queue?.phase);
    const currentMonitorId = queue?.current_monitor_id;
    const nextMonitorId = queue?.next_monitor_id;

    if (phase === 'checking') {
      return currentMonitorId
        ? `\u0441\u0435\u0439\u0447\u0430\u0441: ${findSiteName(currentMonitorId)}`
        : '\u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u043c \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443';
    }
    if (phase === 'waiting_gap') {
      return nextMonitorId
        ? `\u043f\u0430\u0443\u0437\u0430 \u043f\u0435\u0440\u0435\u0434: ${findSiteName(nextMonitorId)}`
        : '\u043f\u0430\u0443\u0437\u0430 \u043c\u0435\u0436\u0434\u0443 \u0437\u0430\u043f\u0440\u043e\u0441\u0430\u043c\u0438';
    }
    if (phase === 'queued' || queue?.queued) {
      return '\u0436\u0434\u0451\u043c \u0437\u0430\u043f\u0443\u0441\u043a\u0430 worker';
    }
    if (phase === 'starting') {
      return '\u043f\u043e\u0434\u0433\u043e\u0442\u043e\u0432\u043a\u0430 \u043a \u0437\u0430\u043f\u0443\u0441\u043a\u0443';
    }
    if (phase === 'finishing') {
      return '\u0437\u0430\u0432\u0435\u0440\u0448\u0430\u0435\u043c \u0446\u0438\u043a\u043b \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438';
    }
    if (queue?.running) {
      return '\u0432\u044b\u043f\u043e\u043b\u043d\u044f\u0435\u043c \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0443';
    }

    return '\u0436\u0434\u0451\u043c \u0441\u043b\u0435\u0434\u0443\u044e\u0449\u0438\u0439 \u0437\u0430\u043f\u0443\u0441\u043a \u043f\u043e \u0440\u0430\u0441\u043f\u0438\u0441\u0430\u043d\u0438\u044e';
  }

  function renderQueueLine(label, queue) {
    const running = Boolean(queue?.running);
    const queued = Boolean(queue?.queued);
    const active = running || queued;
    const total = Number(queue?.total_count || 0);
    const completed = Number(queue?.completed_count || 0);
    const progressText = total > 0 ? `${completed}/${total}` : '\u2014';
    const progressHtml = active && total > 0
      ? `<span>\u043f\u0440\u043e\u0433\u0440\u0435\u0441\u0441: ${escapeHtml(progressText)}</span>`
      : '';
    const activityText = queueActivityText(queue);

    return `
      <div class="d-flex flex-wrap align-items-center gap-2">
        <strong>${escapeHtml(label)}:</strong>
        ${queueBadge(queue)}
        ${progressHtml}
        <span>${escapeHtml(activityText)}</span>
        ${renderQueueInfoButton(label, queue)}
      </div>
    `;
  }

'@

    $js = Replace-RegexOnce `
        -Text $js `
        -Pattern $queuePattern `
        -Replacement $queueReplacement `
        -Label "queue info/activity functions"

    if (-not $js.Contains("page-header-info__toggle")) {
        throw "V4 JS is missing page-header-info__toggle"
    }

    if (-not $js.Contains("queueActivityText")) {
        throw "V4 JS is missing queueActivityText"
    }

    return $js
}

function Build-V4Service {
    param([string]$ServiceText)

    $source = Normalize-Lf -Text $ServiceText

    $refreshStatePattern = '^    public RefreshState currentRefreshState\(\) \{.*?^    \}\n\n(?=    private void schedulePersistedLicenseRefreshIfNeeded\(\))'

    $refreshStateReplacement = @'
    public RefreshState currentRefreshState() {
        if (useDurableBackendOps()) {
            return new RefreshState(
                durableQueueState(BackendOpsCommandTypes.RMS_LICENSE_REFRESH),
                durableQueueState(BackendOpsCommandTypes.RMS_NETWORK_REFRESH)
            );
        }

        Long currentLicenseMonitorId = licenseQueueTracker.currentMonitorId();
        Long currentNetworkMonitorId = networkQueueTracker.currentMonitorId();

        return new RefreshState(
            new QueueState(
                licenseRefreshRunning.get(),
                licenseRefreshPending.get(),
                lastLicenseRefreshRequestedAt.get(),
                lastLicenseRefreshCompletedAt.get(),
                currentLicenseMonitorId,
                null,
                licenseQueueTracker.totalCount(),
                licenseQueueTracker.completedCount(),
                licenseRefreshRunning.get()
                    ? (currentLicenseMonitorId != null ? "checking" : "running")
                    : (licenseRefreshPending.get() ? "queued" : "scheduled_wait")
            ),
            new QueueState(
                networkRefreshRunning.get(),
                networkRefreshPending.get(),
                lastNetworkRefreshRequestedAt.get(),
                lastNetworkRefreshCompletedAt.get(),
                currentNetworkMonitorId,
                null,
                networkQueueTracker.totalCount(),
                networkQueueTracker.completedCount(),
                networkRefreshRunning.get()
                    ? (currentNetworkMonitorId != null ? "checking" : "running")
                    : (networkRefreshPending.get() ? "queued" : "scheduled_wait")
            )
        );
    }

'@

    $source = Replace-RegexOnce `
        -Text $source `
        -Pattern $refreshStatePattern `
        -Replacement $refreshStateReplacement `
        -Label "currentRefreshState"

    $backendPattern = '^    public Map<String, Object> executeBackendOpsLicenseRefresh\(Map<String, Object> payload\) \{.*?^    private boolean useDurableBackendOps\(\) \{'

    $backendReplacement = @'
    public Map<String, Object> executeBackendOpsLicenseRefresh(Map<String, Object> payload) {
        Long monitorId = longValue(payload == null ? null : payload.get("monitor_id"));
        boolean withNotifications = booleanValue(
            payload == null ? null : payload.get("with_notifications"),
            false
        );
        reportBackendOpsProgress(5, "starting");
        if (monitorId == null) {
            refreshAllLicensesInternal(withNotifications);
        } else {
            RmsLicenseMonitor monitor = requireMonitor(monitorId);
            licenseQueueTracker.start(List.of(monitor.getId()));
            licenseQueueTracker.markRunning(monitor.getId());
            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );
            refreshLicenseState(monitor, withNotifications);
            licenseQueueTracker.markCompleted(monitor.getId());
            reportBackendOpsQueueProgress(
                "finishing",
                null,
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );
        }
        return Map.of(
            "state", "success",
            "scope", monitorId == null ? "all" : "monitor:" + monitorId
        );
    }

    public Map<String, Object> executeBackendOpsNetworkRefresh(Map<String, Object> payload) {
        Long monitorId = longValue(payload == null ? null : payload.get("monitor_id"));
        reportBackendOpsProgress(5, "starting");
        if (monitorId == null) {
            refreshAllNetworkStatesInternal();
        } else {
            RmsLicenseMonitor monitor = requireMonitor(monitorId);
            networkQueueTracker.start(List.of(monitor.getId()));
            networkQueueTracker.markRunning(monitor.getId());
            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );
            refreshNetworkState(monitor);
            networkQueueTracker.markCompleted(monitor.getId());
            reportBackendOpsQueueProgress(
                "finishing",
                null,
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );
        }
        return Map.of(
            "state", "success",
            "scope", monitorId == null ? "all" : "monitor:" + monitorId
        );
    }

    private QueueState durableQueueState(String commandType) {
        BackendOpsCommandService.CommandSnapshot active =
            backendOpsCommandService.findActiveByType(commandType).orElse(null);
        BackendOpsCommandService.CommandSnapshot latest =
            backendOpsCommandService.findLatestByType(commandType).orElse(null);
        BackendOpsCommandService.CommandSnapshot lastSucceeded =
            backendOpsCommandService.findLatestSucceededByType(commandType).orElse(null);

        BackendOpsCommandService.CommandSnapshot source =
            active != null ? active : latest;
        Long requestedMonitorId = longValue(
            source == null ? null : source.payload().get("monitor_id")
        );

        int totalCount = 0;
        if (source != null) {
            totalCount = requestedMonitorId != null
                ? 1
                : repository.findAllByOrderByRmsAddressAscIdAsc().size();
        }

        int completedCount = source != null && source.succeeded()
            ? totalCount
            : 0;
        Long currentMonitorId = null;
        Long nextMonitorId = null;
        String phase = "scheduled_wait";

        DurableQueueProgress durableProgress = active == null
            ? null
            : parseDurableQueueProgress(active.progressMessage());

        if (active != null && active.queued()) {
            phase = "queued";
            if (durableProgress != null) {
                if (durableProgress.totalCount() > 0) {
                    totalCount = durableProgress.totalCount();
                }
                completedCount = clampCompletedCount(
                    durableProgress.completedCount(),
                    totalCount
                );
            }
        } else if (active != null && active.running()) {
            phase = "running";
            currentMonitorId = requestedMonitorId;

            if (durableProgress != null) {
                phase = durableProgress.phase();
                currentMonitorId = durableProgress.currentMonitorId();
                nextMonitorId = durableProgress.nextMonitorId();

                if (durableProgress.totalCount() > 0) {
                    totalCount = durableProgress.totalCount();
                }

                completedCount = clampCompletedCount(
                    durableProgress.completedCount(),
                    totalCount
                );
            }
        }

        return new QueueState(
            active != null && active.running(),
            active != null && active.queued(),
            latest == null ? null : latest.requestedAt(),
            lastSucceeded == null ? null : lastSucceeded.completedAt(),
            currentMonitorId,
            nextMonitorId,
            totalCount,
            completedCount,
            phase
        );
    }

    private boolean useDurableBackendOps() {
'@

    $source = Replace-RegexOnce `
        -Text $source `
        -Pattern $backendPattern `
        -Replacement $backendReplacement `
        -Label "durable backend ops block"

    $progressPattern = '^    private void reportBackendOpsProgress\(int progressPercent, String message\) \{\n        if \(backendOpsExecutionContext != null\) \{\n            backendOpsExecutionContext\.reportProgress\(progressPercent, message\);\n        \}\n    \}\n'

    $progressReplacement = @'
    private void reportBackendOpsProgress(int progressPercent, String message) {
        if (backendOpsExecutionContext != null) {
            backendOpsExecutionContext.reportProgress(progressPercent, message);
        }
    }

    private void reportBackendOpsQueueProgress(String phase,
                                               Long currentMonitorId,
                                               Long nextMonitorId,
                                               int completedCount,
                                               int totalCount) {
        if (backendOpsExecutionContext == null || !backendOpsExecutionContext.active()) {
            return;
        }

        int safeTotal = Math.max(0, totalCount);
        int safeCompleted = clampCompletedCount(completedCount, safeTotal);
        int progressPercent = safeTotal <= 0
            ? 5
            : Math.min(98, 5 + (int) Math.floor((safeCompleted * 93.0d) / safeTotal));

        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("kind", "rms_queue_progress");
        progress.put("phase", StringUtils.hasText(phase) ? phase.trim() : "running");
        progress.put("current_monitor_id", currentMonitorId);
        progress.put("next_monitor_id", nextMonitorId);
        progress.put("completed_count", safeCompleted);
        progress.put("total_count", safeTotal);

        try {
            reportBackendOpsProgress(
                progressPercent,
                objectMapper.writeValueAsString(progress)
            );
        } catch (Exception ex) {
            reportBackendOpsProgress(progressPercent, String.valueOf(progress.get("phase")));
        }
    }

    private DurableQueueProgress parseDurableQueueProgress(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }

        String trimmed = rawValue.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }

        try {
            Map<String, Object> progress = objectMapper.readValue(
                trimmed,
                new TypeReference<LinkedHashMap<String, Object>>() {
                }
            );

            if (!"rms_queue_progress".equals(String.valueOf(progress.get("kind")))) {
                return null;
            }

            String phase = progress.get("phase") == null
                ? "running"
                : String.valueOf(progress.get("phase")).trim();

            if (!StringUtils.hasText(phase)) {
                phase = "running";
            }

            return new DurableQueueProgress(
                phase,
                longValue(progress.get("current_monitor_id")),
                longValue(progress.get("next_monitor_id")),
                intValue(progress.get("completed_count"), 0),
                intValue(progress.get("total_count"), 0)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private int clampCompletedCount(int completedCount, int totalCount) {
        int safeTotal = Math.max(0, totalCount);
        return Math.max(0, Math.min(completedCount, safeTotal));
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
'@

    $source = Replace-RegexOnce `
        -Text $source `
        -Pattern $progressPattern `
        -Replacement $progressReplacement `
        -Label "backend progress helpers"

    $loopsPattern = '^    private void refreshAllLicensesInternal\(boolean withNotifications\) \{.*?^    \}\n\n    private void refreshAllNetworkStatesInternal\(\) \{.*?^    \}\n\n(?=    private void refreshLicenseState\()'

    $loopsReplacement = @'
    private void refreshAllLicensesInternal(boolean withNotifications) {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        licenseQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());

        for (int index = 0; index < monitors.size(); index++) {
            RmsLicenseMonitor monitor = monitors.get(index);
            licenseQueueTracker.markRunning(monitor.getId());

            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                licenseQueueTracker.completedCount(),
                licenseQueueTracker.totalCount()
            );

            refreshLicenseState(monitor, withNotifications);
            licenseQueueTracker.markCompleted(monitor.getId());

            Long nextMonitorId = index < monitors.size() - 1
                ? monitors.get(index + 1).getId()
                : null;

            if (nextMonitorId != null) {
                reportBackendOpsQueueProgress(
                    "waiting_gap",
                    null,
                    nextMonitorId,
                    licenseQueueTracker.completedCount(),
                    licenseQueueTracker.totalCount()
                );
                sleepBetweenQueueItems(index, monitors.size());
            } else {
                reportBackendOpsQueueProgress(
                    "finishing",
                    null,
                    null,
                    licenseQueueTracker.completedCount(),
                    licenseQueueTracker.totalCount()
                );
            }
        }
    }

    private void refreshAllNetworkStatesInternal() {
        List<RmsLicenseMonitor> monitors = repository.findAllByOrderByRmsAddressAscIdAsc();
        networkQueueTracker.start(monitors.stream().map(RmsLicenseMonitor::getId).toList());

        for (int index = 0; index < monitors.size(); index++) {
            RmsLicenseMonitor monitor = monitors.get(index);
            networkQueueTracker.markRunning(monitor.getId());

            reportBackendOpsQueueProgress(
                "checking",
                monitor.getId(),
                null,
                networkQueueTracker.completedCount(),
                networkQueueTracker.totalCount()
            );

            refreshNetworkState(monitor);
            networkQueueTracker.markCompleted(monitor.getId());

            Long nextMonitorId = index < monitors.size() - 1
                ? monitors.get(index + 1).getId()
                : null;

            if (nextMonitorId != null) {
                reportBackendOpsQueueProgress(
                    "waiting_gap",
                    null,
                    nextMonitorId,
                    networkQueueTracker.completedCount(),
                    networkQueueTracker.totalCount()
                );
                sleepBetweenQueueItems(index, monitors.size());
            } else {
                reportBackendOpsQueueProgress(
                    "finishing",
                    null,
                    null,
                    networkQueueTracker.completedCount(),
                    networkQueueTracker.totalCount()
                );
            }
        }
    }

'@

    $source = Replace-RegexOnce `
        -Text $source `
        -Pattern $loopsPattern `
        -Replacement $loopsReplacement `
        -Label "RMS queue loops"

    $queueStateOld = @'
    public record QueueState(boolean running,
                             boolean queued,
                             OffsetDateTime lastRequestedAt,
                             OffsetDateTime lastCompletedAt,
                             Long currentMonitorId,
                             int totalCount,
                             int completedCount) {
    }

'@

    $queueStateNew = @'
    public record QueueState(boolean running,
                             boolean queued,
                             OffsetDateTime lastRequestedAt,
                             OffsetDateTime lastCompletedAt,
                             Long currentMonitorId,
                             Long nextMonitorId,
                             int totalCount,
                             int completedCount,
                             String phase) {
    }

    private record DurableQueueProgress(String phase,
                                        Long currentMonitorId,
                                        Long nextMonitorId,
                                        int completedCount,
                                        int totalCount) {
    }

'@

    $source = Replace-ExactOnce `
        -Text $source `
        -OldText $queueStateOld `
        -NewText $queueStateNew `
        -Label "QueueState record"

    return $source
}

function Build-V4Controller {
    param([string]$ControllerText)

    $source = Normalize-Lf -Text $ControllerText

    $old = @'
        payload.put("current_monitor_id", state.currentMonitorId());
        payload.put("total_count", state.totalCount());
        payload.put("completed_count", state.completedCount());
'@

    $new = @'
        payload.put("current_monitor_id", state.currentMonitorId());
        payload.put("next_monitor_id", state.nextMonitorId());
        payload.put("total_count", state.totalCount());
        payload.put("completed_count", state.completedCount());
        payload.put("phase", state.phase());
'@

    return Replace-ExactOnce `
        -Text $source `
        -OldText $old `
        -NewText $new `
        -Label "controller queue state mapping"
}

if ($Apply -and $ValidateOnly) {
    throw "Use either -Apply or -ValidateOnly"
}

if (-not $Apply) {
    $ValidateOnly = $true
}

if ($PSVersionTable.PSVersion.Major -ne 5) {
    throw "Run with Windows PowerShell 5.1"
}

Write-Host "=== RMS QUEUE LIVE STATE UI V4 ==="

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
$serviceRelative = "spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java"
$controllerRelative = "spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java"
$dockerRelative = "docker/panel.Dockerfile"

$jsPath = Join-Path $RepoRoot ($jsRelative.Replace("/", "\"))
$htmlPath = Join-Path $RepoRoot ($htmlRelative.Replace("/", "\"))
$servicePath = Join-Path $RepoRoot ($serviceRelative.Replace("/", "\"))
$controllerPath = Join-Path $RepoRoot ($controllerRelative.Replace("/", "\"))
$dockerPath = Join-Path $RepoRoot ($dockerRelative.Replace("/", "\"))
$composePath = Join-Path $RepoRoot "docker-compose.production-contour.yml"
$envPath = Join-Path $RepoRoot ".env"

foreach ($requiredPath in @(
    $jsPath,
    $htmlPath,
    $servicePath,
    $controllerPath,
    $dockerPath,
    $composePath
)) {
    if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
        throw "Required file missing: $requiredPath"
    }
}

# Fresh GitHub/local gate.
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
    throw "STOP: origin/main changed"
}

if ($localHead -cne $originMain) {
    throw "STOP: local HEAD differs from origin/main"
}

$baseJs = Get-GitFileText -Ref "origin/main" -RelativePath $jsRelative
$baseHtml = Get-GitFileText -Ref "origin/main" -RelativePath $htmlRelative
$baseService = Get-GitFileText -Ref "origin/main" -RelativePath $serviceRelative
$baseController = Get-GitFileText -Ref "origin/main" -RelativePath $controllerRelative
$baseDocker = Get-GitFileText -Ref "origin/main" -RelativePath $dockerRelative

$currentJs = Read-Utf8File -Path $jsPath
$currentHtml = Read-Utf8File -Path $htmlPath
$currentService = Read-Utf8File -Path $servicePath
$currentController = Read-Utf8File -Path $controllerPath
$currentDocker = Read-Utf8File -Path $dockerPath

$expectedV3 = Build-V3Expected `
    -BaseJavaScript $baseJs `
    -BaseHtml $baseHtml

if ((Normalize-Lf $currentJs).TrimEnd() -cne $expectedV3.JavaScript.TrimEnd()) {
    throw "STOP: local rms-monitoring.js is not the exact reviewed V3 state"
}

if ((Normalize-Lf $currentHtml).TrimEnd() -cne $expectedV3.Html.TrimEnd()) {
    throw "STOP: local rms-control.html is not the exact reviewed V3 state"
}

if ((Normalize-Lf $currentService).TrimEnd() -cne
    (Normalize-Lf $baseService).TrimEnd()) {
    throw "STOP: RmsLicenseMonitoringService.java already has local changes"
}

if ((Normalize-Lf $currentController).TrimEnd() -cne
    (Normalize-Lf $baseController).TrimEnd()) {
    throw "STOP: RmsLicenseMonitoringApiController.java already has local changes"
}

if ((Normalize-Lf $currentDocker).TrimEnd() -cne
    (Normalize-Lf $baseDocker).TrimEnd()) {
    throw "STOP: docker/panel.Dockerfile differs from origin/main"
}

foreach ($requiredText in @(
    "iputils-ping",
    "traceroute",
    "command -v ping >/dev/null",
    "command -v traceroute >/dev/null"
)) {
    if (-not $currentDocker.Contains($requiredText)) {
        throw "STOP: Dockerfile runtime tool guard missing: $requiredText"
    }
}

$statusResult = Invoke-Native `
    -FilePath "git.exe" `
    -ArgumentList @("status", "--porcelain", "--untracked-files=all")

$unexpected = @()

foreach ($rawLine in $statusResult.Output) {
    $line = [string]$rawLine

    $allowed = (
        ($line -eq " M $jsRelative") -or
        ($line -eq " M $htmlRelative") -or
        ($line -match '^\?\? ai-context/changelog/2026-09-03_[0-9]+_rms-queue-info-ui\.md$') -or
        ($line -match '^\?\? run/') -or
        ($line -match '^\?\? fix-rms-monitoring-runtime-tools\.ps1$') -or
        ($line -match '^\?\? rms-monitoring-queue-live-state-v[0-9]+\.ps1$')
    )

    if (-not $allowed) {
        $unexpected += $line
    }
}

if ($unexpected.Count -gt 0) {
    Write-Host "[RED] unexpected repository state:"
    foreach ($line in $unexpected) {
        Write-Host "  $line"
    }
    throw "STOP: repository has unexpected local changes"
}

# Fresh live production gate.
$workerIds = @(Get-ServiceIds -Service "ops-worker")
$webIds = @(Get-ServiceIds -Service "panel-web")

if ($workerIds.Count -ne 1) {
    throw "Expected exactly one ops-worker, found $($workerIds.Count)"
}

if ($webIds.Count -lt 1) {
    throw "Expected at least one panel-web"
}

$workerInspect = Assert-Healthy `
    -ContainerId $workerIds[0] `
    -Service "ops-worker"

$oldWorkerId = $workerIds[0]
$oldWorkerHostname = [string]$workerInspect.Config.Hostname
$oldWorkerImageId = [string]$workerInspect.Image
$workerImageName = [string]$workerInspect.Config.Image

Invoke-Native `
    -FilePath "docker.exe" `
    -ArgumentList @(
        "exec",
        $oldWorkerId,
        "sh",
        "-lc",
        "command -v ping >/dev/null && command -v traceroute >/dev/null"
    ) |
    Out-Null

$oldWebImageId = ""
$webImageName = ""

foreach ($webId in $webIds) {
    $inspect = Assert-Healthy `
        -ContainerId $webId `
        -Service "panel-web"

    if ([string]::IsNullOrWhiteSpace($oldWebImageId)) {
        $oldWebImageId = [string]$inspect.Image
        $webImageName = [string]$inspect.Config.Image
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

if ($workerImageName -cne $webImageName) {
    throw "STOP: ops-worker and panel-web use different configured image names"
}

$currentImageName = $workerImageName

Write-Host "[PRECHECK] ops_worker=$oldWorkerId"
Write-Host "[PRECHECK] ops_worker_instance=$oldWorkerHostname"
Write-Host "[PRECHECK] worker_image_id=$oldWorkerImageId"
Write-Host "[PRECHECK] panel_web_count=$($webIds.Count)"
Write-Host "[PRECHECK] panel_web_image_id=$oldWebImageId"
Write-Host "[PRECHECK] configured_image=$currentImageName"

$postgres = Get-PostgresContext

$safeWorker = $oldWorkerHostname.Replace("'", "''")

$runningSql = @"
SELECT
    command_id || '|' ||
    command_type || '|' ||
    COALESCE(claimed_by, '')
FROM backend_ops_command
WHERE status = 'running'
  AND claimed_by = '$safeWorker'
ORDER BY requested_at;
"@

$runningResult = Invoke-Psql `
    -Context $postgres `
    -Sql $runningSql `
    -TuplesOnly

$runningCommands = @(
    $runningResult.Output |
        ForEach-Object { ([string]$_).Trim() } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
)

foreach ($line in $runningCommands) {
    $parts = @($line.Split("|"))

    if ($parts.Count -lt 2) {
        throw "Unexpected backend command row: $line"
    }

    $type = $parts[1]

    if ($type -cne "rms.network.refresh" -and
        $type -cne "rms.license.refresh") {
        throw "STOP: ops-worker is running non-RMS command: $line"
    }

    Write-Host "[PRECHECK] interruptible_rms_command=$line"
}

$composeArguments = @(Get-ComposeBaseArguments `
    -ComposePath $composePath `
    -EnvPath $envPath)
$composeArguments += @("config", "-q")

Invoke-Native `
    -FilePath "docker.exe" `
    -ArgumentList $composeArguments |
    Out-Null

# Build transformations in memory.
$jsNewline = Get-Newline -Text $currentJs
$serviceNewline = Get-Newline -Text $currentService
$controllerNewline = Get-Newline -Text $currentController

$newJsLf = Build-V4JavaScript -CurrentJavaScript $currentJs
$newServiceLf = Build-V4Service -ServiceText $currentService
$newControllerLf = Build-V4Controller -ControllerText $currentController

$newJs = Restore-Newline -Text $newJsLf -Newline $jsNewline
$newService = Restore-Newline -Text $newServiceLf -Newline $serviceNewline
$newController = Restore-Newline -Text $newControllerLf -Newline $controllerNewline

Write-Host "[GREEN] repository/live/source transform preflight passed"

if ($ValidateOnly) {
    Write-Host "[VALIDATE_ONLY] Nothing was changed"
    exit 0
}

$timestamp = Get-Date -Format "yyyy-MM-dd_HHmmss"
$candidateTag = "iguana-panel-rms-live-state:$timestamp"
$rollbackWebTag = "iguana-panel-rms-live-state-rollback-web:$timestamp"
$rollbackWorkerTag = "iguana-panel-rms-live-state-rollback-worker:$timestamp"

$changelogPath = Join-Path `
    $RepoRoot `
    ("ai-context\changelog\{0}_rms-queue-live-state-ui.md" -f $timestamp)

$sourceChanged = $false
$changelogCreated = $false
$imageTagChanged = $false
$webRecreateStarted = $false
$workerRecreateStarted = $false
$newWorkerHostname = ""

try {
    Write-Utf8NoBom -Path $jsPath -Text $newJs
    Write-Utf8NoBom -Path $servicePath -Text $newService
    Write-Utf8NoBom -Path $controllerPath -Text $newController
    $sourceChanged = $true

    $changelog = @(
        "# RMS queue live state UI",
        "",
        "- Base main: $ExpectedMain",
        "- Time: $timestamp",
        "",
        "## Change",
        "",
        "- Reuse the page-header info button hit target for RMS queue info.",
        "- Show human-readable queue phases: active check, inter-request pause, queued, scheduled wait, finishing.",
        "- Persist current/next RMS and completed/total progress through backend_ops_command.progress_message.",
        "- Refresh backend heartbeat whenever RMS queue progress changes.",
        "- Expose phase and next_monitor_id in the RMS refresh-state API.",
        "- Recreate panel-web and ops-worker on the same candidate image.",
        "- Recover interrupted RMS commands owned by the replaced worker back to queued.",
        ""
    ) -join "`r`n"

    Write-Utf8NoBom `
        -Path $changelogPath `
        -Text ($changelog + "`r`n")

    $changelogCreated = $true

    Write-Host "[APPLY] source updated"
    Write-Host "[APPLY] changelog=$changelogPath"

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @("tag", $oldWebImageId, $rollbackWebTag) |
        Out-Null

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @("tag", $oldWorkerImageId, $rollbackWorkerTag) |
        Out-Null

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

    Recreate-Service `
        -ComposePath $composePath `
        -EnvPath $envPath `
        -ImageName $currentImageName `
        -Service "panel-web" `
        -ReplicaCount $webIds.Count

    $newWebIds = @(Wait-ServiceHealthy `
        -Service "panel-web" `
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
                "curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'queueActivityText' && curl -fsS http://localhost:8080/js/rms-monitoring.js | grep -q 'page-header-info__toggle'"
            ) |
            Out-Null
    }

    Write-Host "[GREEN] panel-web healthy and serves V4 UI"

    # Re-check old worker immediately before interruption.
    $liveWorkerIds = @(Get-ServiceIds -Service "ops-worker")

    if ($liveWorkerIds.Count -ne 1 -or
        $liveWorkerIds[0] -cne $oldWorkerId) {
        throw "STOP: ops-worker changed during deployment"
    }

    Write-Host "[DEPLOY] recreating ops-worker"

    $workerRecreateStarted = $true

    Recreate-Service `
        -ComposePath $composePath `
        -EnvPath $envPath `
        -ImageName $currentImageName `
        -Service "ops-worker" `
        -ReplicaCount 1

    $newWorkerIds = @(Wait-ServiceHealthy `
        -Service "ops-worker" `
        -ExpectedCount 1 `
        -TimeoutSeconds 240)

    $newWorkerId = $newWorkerIds[0]
    $newWorkerInspect = Assert-Healthy `
        -ContainerId $newWorkerId `
        -Service "ops-worker"

    $newWorkerHostname = [string]$newWorkerInspect.Config.Hostname

    if ($newWorkerId -ceq $oldWorkerId) {
        throw "ops-worker container ID did not change"
    }

    Invoke-Native `
        -FilePath "docker.exe" `
        -ArgumentList @(
            "exec",
            $newWorkerId,
            "sh",
            "-lc",
            "command -v ping >/dev/null && command -v traceroute >/dev/null"
        ) |
        Out-Null

    $recovered = @(Recover-RmsCommandsForWorker `
        -Postgres $postgres `
        -DeadWorker $oldWorkerHostname)

    foreach ($commandId in $recovered) {
        Write-Host "[RECOVERED] RMS command=$commandId"
    }

    Write-Host "[GREEN] ops-worker healthy; interrupted RMS commands recovered"

    Start-Sleep -Seconds 3

    $status = Invoke-Native `
        -FilePath "git.exe" `
        -ArgumentList @(
            "status",
            "--short",
            "--",
            $jsRelative,
            $htmlRelative,
            $serviceRelative,
            $controllerRelative,
            "ai-context/changelog"
        )

    Write-Host ""
    Write-Host "=== SUCCESS ==="
    Write-Host "RMS queue state UI V4 deployed"
    Write-Host "panel-web and ops-worker recreated"
    Write-Host "RMS commands interrupted by worker replacement were returned to queued"
    Write-Host ""
    Write-Host "[GIT STATUS]"

    foreach ($line in $status.Output) {
        Write-Host "  $line"
    }
}
catch {
    $originalError = $_

    Write-Host ""
    Write-Host "[RED] V4 deploy failed: $($originalError.Exception.Message)"
    Write-Host "[ROLLBACK] starting"

    $rollbackErrors = New-Object System.Collections.Generic.List[string]

    try {
        if ($workerRecreateStarted) {
            $currentWorkers = @(Get-ServiceIds -Service "ops-worker")

            foreach ($id in $currentWorkers) {
                $inspect = Get-InspectObject -ContainerId $id
                $hostname = [string]$inspect.Config.Hostname

                if (-not [string]::IsNullOrWhiteSpace($hostname) -and
                    $hostname -cne $oldWorkerHostname) {
                    Recover-RmsCommandsForWorker `
                        -Postgres $postgres `
                        -DeadWorker $hostname |
                        Out-Null
                }
            }

            Invoke-Native `
                -FilePath "docker.exe" `
                -ArgumentList @("tag", $rollbackWorkerTag, $currentImageName) |
                Out-Null

            Recreate-Service `
                -ComposePath $composePath `
                -EnvPath $envPath `
                -ImageName $currentImageName `
                -Service "ops-worker" `
                -ReplicaCount 1

            $restoredWorkers = @(Wait-ServiceHealthy `
                -Service "ops-worker" `
                -ExpectedCount 1 `
                -TimeoutSeconds 240)

            Recover-RmsCommandsForWorker `
                -Postgres $postgres `
                -DeadWorker $oldWorkerHostname |
                Out-Null

            Write-Host "[ROLLBACK] ops-worker restored"
        }
    }
    catch {
        $rollbackErrors.Add(
            "ops-worker rollback failed: $($_.Exception.Message)"
        )
    }

    try {
        if ($webRecreateStarted) {
            Invoke-Native `
                -FilePath "docker.exe" `
                -ArgumentList @("tag", $rollbackWebTag, $currentImageName) |
                Out-Null

            Recreate-Service `
                -ComposePath $composePath `
                -EnvPath $envPath `
                -ImageName $currentImageName `
                -Service "panel-web" `
                -ReplicaCount $webIds.Count

            Wait-ServiceHealthy `
                -Service "panel-web" `
                -ExpectedCount $webIds.Count `
                -TimeoutSeconds 240 |
                Out-Null

            Write-Host "[ROLLBACK] panel-web restored"
        }
    }
    catch {
        $rollbackErrors.Add(
            "panel-web rollback failed: $($_.Exception.Message)"
        )
    }

    try {
        if ($sourceChanged) {
            Write-Utf8NoBom -Path $jsPath -Text $currentJs
            Write-Utf8NoBom -Path $servicePath -Text $currentService
            Write-Utf8NoBom -Path $controllerPath -Text $currentController
            Write-Host "[ROLLBACK] source files restored"
        }

        if ($changelogCreated -and
            (Test-Path -LiteralPath $changelogPath -PathType Leaf)) {
            Remove-Item -LiteralPath $changelogPath -Force
            Write-Host "[ROLLBACK] V4 changelog removed"
        }
    }
    catch {
        $rollbackErrors.Add(
            "source rollback failed: $($_.Exception.Message)"
        )
    }

    try {
        if ($imageTagChanged -and $webRecreateStarted) {
            Invoke-Native `
                -FilePath "docker.exe" `
                -ArgumentList @("tag", $rollbackWebTag, $currentImageName) |
                Out-Null
        }
    }
    catch {
        $rollbackErrors.Add(
            "final image tag restore failed: $($_.Exception.Message)"
        )
    }

    if ($rollbackErrors.Count -gt 0) {
        Write-Host "[RED] rollback had errors:"
        foreach ($item in $rollbackErrors) {
            Write-Host "  $item"
        }
    }
    else {
        Write-Host "[GREEN] rollback completed"
    }

    throw $originalError
}
