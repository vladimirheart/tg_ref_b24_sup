param(
    [string]$DatabaseContainerId, [string]$DatabaseName, [string]$DatabaseUser,
    [string]$Schema = 'public', [string]$Platform, [string]$ChannelId, [string]$UserId,
    [string]$EventId, [string]$ExpectedSnapshotSha256, [switch]$ReconcileReceipt,
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'

function New-FeedbackRecoveryPlan {
    param([string]$Container, [string]$Database, [string]$Username, [string]$Namespace,
        [string]$Transport, [string]$Channel, [string]$Recipient, [string]$Event,
        [bool]$Apply, [string]$Snapshot)
    if ($Container -cnotmatch '^[a-f0-9]{64}$') { throw 'Use the full PostgreSQL container ID' }
    foreach ($identifier in @($Database, $Username, $Namespace)) {
        if ($identifier -cnotmatch '^[a-z_][a-z0-9_]{0,62}$') { throw 'Explicit database/user/schema identifiers are required' }
    }
    if ($Transport -cnotin @('telegram', 'vk', 'max')) { throw 'Use canonical telegram, vk or max' }
    foreach ($identifier in @($Channel, $Recipient)) {
        $parsed = 0L
        if ($identifier -cnotmatch '^[1-9][0-9]{0,18}$' -or -not [long]::TryParse($identifier, [ref]$parsed)) { throw 'Positive BIGINT identifiers are required' }
    }
    if ([string]::IsNullOrWhiteSpace($Event)) { throw 'An exact event ID is required' }
    $utf8 = New-Object Text.UTF8Encoding($false, $true)
    $bytes = $utf8.GetBytes($Event)
    if ($bytes.Length -gt 4096 -or $Event.Contains([string][char]0)) { throw 'Invalid event ID' }
    if ($Apply -and $Snapshot -cnotmatch '^[a-f0-9]{64}$') { throw 'Reconciliation requires the exact reviewed SnapshotSha256' }
    if (-not $Apply -and $Snapshot) { throw 'Snapshot is only used with -ReconcileReceipt' }
    $eventHex = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    $name = if ($Apply) { 'feedback-delivery-reconcile.sql' } else { 'feedback-delivery-review.sql' }
    return [pscustomobject]@{
        Container = $Container; SqlFile = (Join-Path $PSScriptRoot ('sql/' + $name)); Apply = $Apply
        Arguments = @('-X', '-q', '-A', '-t', '-w', ('--dbname=' + $Database), ('--username=' + $Username),
            '-v', 'ON_ERROR_STOP=1', '-v', ('schema=' + $Namespace), '-v', ('platform=' + $Transport),
            '-v', ('channel_id=' + $Channel), '-v', ('user_id=' + $Recipient), '-v', ('event_hex=' + $eventHex),
            '-v', ('snapshot=' + $Snapshot), '-v', 'sink=/dev/null')
    }
}

function Invoke-FeedbackNative {
    param([string]$Program, [string[]]$NativeArguments, [string]$InputText = '')
    foreach ($value in $NativeArguments) {
        if ($value -cnotmatch '^[A-Za-z0-9_./:=\\-]+$') { throw 'Unsafe native argument' }
    }
    $start = New-Object Diagnostics.ProcessStartInfo
    $start.FileName = $Program; $start.Arguments = $NativeArguments -join ' '
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardInput = $true; $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    $start.StandardOutputEncoding = New-Object Text.UTF8Encoding($false)
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $start
    try {
        $null = $process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        $process.StandardInput.Write($InputText); $process.StandardInput.Close()
        if (-not $process.WaitForExit(30000)) { $process.Kill(); throw 'Native timeout; inspect the database before any further action' }
        $text = $stdout.GetAwaiter().GetResult(); $null = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw ('Native operation failed with exit code ' + $process.ExitCode + '; no success is asserted') }
        return $text.TrimEnd()
    } finally { $process.Dispose() }
}

if ($MyInvocation.InvocationName -eq '.') { return }
if ($SelfTest) {
    $plan = New-FeedbackRecoveryPlan ('a' * 64) 'test' 'tester' 'public' 'max' '9007199254740995' '9223372036854775807' (" padded/'" + [char]0x42e + " `n") $false ''
    if (-not ($plan.Arguments -contains 'channel_id=9007199254740995') -or $plan.Apply) { throw 'Plan self-test failed' }
    $rejected = $false
    try { $null = New-FeedbackRecoveryPlan ('a' * 64) 'test' 'tester' 'public' 'max' '1' '1' 'x' $true '' } catch { $rejected = $true }
    if (-not $rejected) { throw 'Missing proof accepted' }
    Write-Output 'SELF_TEST_GREEN; no Docker, database or provider calls'
    return
}
$plan = New-FeedbackRecoveryPlan $DatabaseContainerId $DatabaseName $DatabaseUser $Schema $Platform $ChannelId $UserId $EventId ([bool]$ReconcileReceipt) $ExpectedSnapshotSha256
$docker = (Get-Command docker -ErrorAction Stop).Source
$context = Invoke-FeedbackNative $docker @('context', 'show')
if ($context -cnotmatch '^[A-Za-z0-9_.-]+$') { throw 'Invalid Docker context' }
$configuration = @(ConvertFrom-Json -InputObject (Invoke-FeedbackNative $docker @('--context', $context, 'context', 'inspect', $context)))
if ($configuration.Count -ne 1 -or $configuration[0].Endpoints.docker.Host -notmatch '^(npipe:////\./pipe/[a-zA-Z0-9_-]+|unix:///[^\r\n]+)$') { throw 'Run on the project machine using its local Docker engine' }
$containers = @(ConvertFrom-Json -InputObject (Invoke-FeedbackNative $docker @('--context', $context, 'container', 'inspect', $plan.Container)))
if ($containers.Count -ne 1 -or $containers[0].Id -cne $plan.Container -or -not $containers[0].State.Running -or $containers[0].Config.Image -notmatch '(^|/)postgres(:|@)') { throw 'The exact running PostgreSQL container is required' }
$sql = Get-Content -LiteralPath $plan.SqlFile -Raw -Encoding UTF8
$result = Invoke-FeedbackNative $docker (@('--context', $context, 'exec', '-i', $plan.Container, 'psql') + $plan.Arguments) $sql
$record = ConvertFrom-Json -InputObject $result
if ($ReconcileReceipt -and -not $record.Reconciled) { throw 'Receipt or snapshot rejected; repeat the read-only review, never resend automatically' }
Write-Output ($record | ConvertTo-Json -Depth 5)
