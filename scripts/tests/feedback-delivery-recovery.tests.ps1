param([Parameter(Mandatory = $true)][string]$PostgresBin)
# Disposable native PostgreSQL only. Does not read application configuration or use Docker.
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
. (Join-Path $root 'scripts/feedback-delivery-recovery.ps1')
$nonce = [guid]::NewGuid().ToString('N')
$output = Join-Path $root ('.tmp/feedback-recovery-test-' + $nonce)
if (Test-Path -LiteralPath $output) { throw 'Fresh test output required' }
$null = New-Item -ItemType Directory -Path $output
$data = Join-Path $output 'data'
$bin = [IO.Path]::GetFullPath($PostgresBin)
foreach ($name in @('initdb.exe', 'pg_ctl.exe', 'psql.exe', 'postgres.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $bin $name))) { throw ('Missing native test binary ' + $name) }
}
$portProbe = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
$portProbe.Start(); $port = $portProbe.LocalEndpoint.Port; $portProbe.Stop()
$psql = Join-Path $bin 'psql.exe'
$pgctl = Join-Path $bin 'pg_ctl.exe'
$nativeArgs = @('-h', '127.0.0.1', '-p', [string]$port, '-X', '-q', '-A', '-t', '-w', '--username=r289_test', '--dbname=postgres', '-v', 'ON_ERROR_STOP=1')
$cases = New-Object 'Collections.Generic.List[object]'
$raw = ' padded/''' + [char]0x42e + [char]36 + '(whoami)' + [char]96 + "`n%? -- "
$channel = '9007199254740995'; $user = '9223372036854775807'; $container = 'a' * 64
function Assert-True([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Query([string]$Sql) { return Invoke-FeedbackNative $psql $nativeArgs ("SET client_encoding='UTF8';`n" + $Sql) }
function Hex([string]$Text) { return ([BitConverter]::ToString([Text.Encoding]::UTF8.GetBytes($Text))).Replace('-', '').ToLowerInvariant() }
function Fixture([string]$Platform, [string]$Status = 'sending', [string]$Receipt = 'positive') {
    $eventHex = Hex $raw
    $ticketHex = Hex (' ticket/' + [char]0x42e + ' ')
    $sql = @"
TRUNCATE integration_outbound_delivery_receipts, integration_outbound_event_deliveries;
INSERT INTO integration_outbound_event_deliveries
 (event_id,event_kind,routing_key,channel_id,user_id,ticket_id,request_id,status,last_error,created_at,updated_at,delivered_at)
VALUES (convert_from(decode('$eventHex','hex'),'UTF8'),'feedback.prompt.dispatch',
 'integration.outbound.feedback.prompt.$Platform.channel.$channel',$channel,$user,
 convert_from(decode('$ticketHex','hex'),'UTF8'),9223372036854775807,'$Status','private error',
 '2026-11-01 01:00:00','2026-11-01 01:00:01',NULL);
"@
    if ($Receipt -ne 'absent') {
        $positive = if ($Receipt -eq 'positive') { "'provider.123','2026-11-01 01:00:02'" } else { 'NULL,NULL' }
        $sql += @"
INSERT INTO integration_outbound_delivery_receipts
 (event_id,attempt_id,event_kind,routing_key,channel_id,user_id,ticket_id,request_id,
 platform,sender_fingerprint,prompt_sha256,prepared_at,provider_message_id,accepted_at)
SELECT event_id,'11111111-1111-1111-1111-111111111111',event_kind,routing_key,channel_id,user_id,ticket_id,request_id,
 '$Platform',repeat('a',64),repeat('c',64),'2026-11-01 01:00:01',$positive FROM integration_outbound_event_deliveries;
"@
    }
    $null = Query $sql
}
function Plan([string]$Platform, [bool]$Apply = $false, [string]$Snapshot = '', [string]$SelectedChannel = $channel, [string]$Recipient = $user, [string]$Event = $raw) {
    return New-FeedbackRecoveryPlan $container 'postgres' 'r289_test' 'public' $Platform $SelectedChannel $Recipient $Event $Apply $Snapshot
}
function Execute($Plan) {
    $sql = Get-Content -Raw -Encoding UTF8 -LiteralPath $Plan.SqlFile
    $psqlArguments = $nativeArgs + $Plan.Arguments + @('-v', 'sink=NUL')
    return ConvertFrom-Json -InputObject (Invoke-FeedbackNative $psql $psqlArguments $sql)
}
function Dump {
    return Query "SELECT jsonb_build_object('deliveries',(SELECT jsonb_agg(to_jsonb(d) ORDER BY event_id) FROM integration_outbound_event_deliveries d),'receipts',(SELECT jsonb_agg(to_jsonb(a) ORDER BY event_id) FROM integration_outbound_delivery_receipts a));"
}
function Case([string]$Name, [scriptblock]$Body) {
    & $Body
    $cases.Add([pscustomobject]@{ Name = $Name; Passed = $true })
}
function Control-Postgres([bool]$Start) {
    $controlArguments = if ($Start) { 'start -D "' + $data + '" -l "' + (Join-Path $output 'postgres.log') + '" -o "-h127.0.0.1 -p' + $port + ' -F" -w' } else { 'stop -D "' + $data + '" -m fast -w' }
    $prefix = if ($Start) { 'start' } else { 'stop' }
    # Capture the process handle before exit; -Wait also waits for the server's descendants on Windows.
    # File redirection avoids inherited daemon pipe handles keeping ReadToEndAsync open.
    $child = Start-Process -FilePath $pgctl -ArgumentList $controlArguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $output ($prefix + '.log')) -RedirectStandardError (Join-Path $output ($prefix + '.err'))
    try {
        $null = $child.Handle
        if (-not $child.WaitForExit(20000)) { throw 'Owned pg_ctl timed out' }
        if ($child.ExitCode -ne 0) { throw 'Owned PostgreSQL control failed; inspect retained logs' }
    } finally { $child.Dispose() }
}
$started = $false
$failure = $null
try {
    $init = Start-Process -FilePath (Join-Path $bin 'initdb.exe') -ArgumentList @('-D', ('"' + $data + '"'), '-U', 'r289_test', '-A', 'trust', '--no-locale', '--encoding=UTF8') -WindowStyle Hidden -Wait -PassThru -RedirectStandardOutput (Join-Path $output 'init.log') -RedirectStandardError (Join-Path $output 'init.err')
    if ($init.ExitCode -ne 0) { throw 'Disposable initdb failed; inspect retained logs' }
    Control-Postgres $true
    $started = $true
    $reportedData = Query 'SHOW data_directory;'
    Assert-True ([IO.Path]::GetFullPath($reportedData) -ieq [IO.Path]::GetFullPath($data)) 'Wrong PostgreSQL instance'
    $null = Query (Get-Content -Raw -Encoding UTF8 -LiteralPath (Join-Path $root 'spring-panel/src/main/resources/db/migration/postgresql/V85__outbound_delivery_receipts.sql'))
    foreach ($platform in @('telegram', 'vk', 'max')) {
        foreach ($status in @('sending', 'uncertain')) {
            Case "$platform/$status/positive-receipt-and-idempotence" {
                Fixture $platform $status
                $before = Dump; $review = Execute (Plan $platform)
                Assert-True ((Dump) -ceq $before) 'Read-only review wrote data'
                Assert-True ($review.Outcome -ceq 'RECEIPT_AVAILABLE' -and $review.EventId -ceq $raw -and $review.ChannelId -ceq $channel -and $review.UserId -ceq $user) 'Review changed the raw identity'
                $receiptBefore = Query 'SELECT to_jsonb(a) FROM integration_outbound_delivery_receipts a;'
                $preserved = Query "SELECT to_jsonb(d)-'status'-'delivered_at'-'last_error'-'updated_at' FROM integration_outbound_event_deliveries d;"
                $result = Execute (Plan $platform $true $review.SnapshotSha256)
                Assert-True $result.Reconciled 'Committed receipt was not reconciled'
                Assert-True ((Query "SELECT status='delivered' AND delivered_at=(SELECT accepted_at FROM integration_outbound_delivery_receipts) AND last_error IS NULL FROM integration_outbound_event_deliveries;") -ceq 't') 'Wrong delivered state or timestamp'
                Assert-True ((Query 'SELECT to_jsonb(a) FROM integration_outbound_delivery_receipts a;') -ceq $receiptBefore) 'Receipt was rewritten'
                Assert-True ((Query "SELECT to_jsonb(d)-'status'-'delivered_at'-'last_error'-'updated_at' FROM integration_outbound_event_deliveries d;") -ceq $preserved) 'Unrelated delivery fields changed'
                $committed = Dump; $duplicate = Execute (Plan $platform $true $review.SnapshotSha256)
                Assert-True (-not $duplicate.Reconciled -and (Dump) -ceq $committed) 'Duplicate changed data'
                Assert-True ((Execute (Plan $platform)).Outcome -ceq 'ALREADY_DELIVERED') 'Delivered result was not recognized'
            }
        }
        foreach ($status in @('processing', 'retryable', 'failed', 'delivered', 'unknown')) {
            Case "$platform/$status/blocked-even-with-receipt" {
                Fixture $platform $status; $review = Execute (Plan $platform); $before = Dump
                Assert-True (-not (Execute (Plan $platform $true $review.SnapshotSha256)).Reconciled -and (Dump) -ceq $before) 'Unsafe status was changed'
            }
        }
        foreach ($receipt in @('absent', 'prepared')) {
            Case "$platform/$receipt/unknown-stays-blocked" {
                Fixture $platform 'uncertain' $receipt; $review = Execute (Plan $platform); $before = Dump
                Assert-True ($review.Outcome -ceq 'BLOCKED_REQUIRES_REVIEW') 'Missing receipt was accepted'
                Assert-True (-not (Execute (Plan $platform $true $review.SnapshotSha256)).Reconciled -and (Dump) -ceq $before) 'Unknown result was changed'
            }
        }
        $mutations = @{
            event_kind = "event_kind='other'"; routing_key = "routing_key='other'"; channel_id = 'channel_id=1'; user_id = 'user_id=1'
            ticket_id = "ticket_id='other'"; request_id = 'request_id=1'; platform = "platform='other-placeholder'"
        }
        foreach ($key in @($mutations.Keys | Sort-Object)) {
            Case "$platform/receipt-context-$key/blocked" {
                Fixture $platform
                $mutation = $mutations[$key]
                if ($key -eq 'platform') { $foreign = if ($platform -eq 'max') { 'vk' } else { 'max' }; $mutation = "platform='$foreign'" }
                $null = Query ('UPDATE integration_outbound_delivery_receipts SET ' + $mutation + ';')
                $review = Execute (Plan $platform); $before = Dump
                Assert-True (-not (Execute (Plan $platform $true $review.SnapshotSha256)).Reconciled -and (Dump) -ceq $before) 'Foreign receipt was accepted'
            }
        }
    }
    $drifts = @{
        delivery_timestamp = "UPDATE integration_outbound_event_deliveries SET updated_at=updated_at+interval '1 second';"
        delivery_error = "UPDATE integration_outbound_event_deliveries SET last_error='changed';"
        receipt_attempt = "UPDATE integration_outbound_delivery_receipts SET attempt_id='22222222-2222-2222-2222-222222222222';"
        receipt_time = "UPDATE integration_outbound_delivery_receipts SET accepted_at=accepted_at+interval '1 second';"
        receipt_provider = "UPDATE integration_outbound_delivery_receipts SET provider_message_id='provider.456';"
    }
    foreach ($key in @($drifts.Keys | Sort-Object)) {
        Case "stale-proof/$key/blocked" {
            Fixture 'max'; $review = Execute (Plan 'max'); $null = Query $drifts[$key]; $before = Dump
            Assert-True (-not (Execute (Plan 'max' $true $review.SnapshotSha256)).Reconciled -and (Dump) -ceq $before) 'Stale proof was accepted'
        }
    }
    Case 'wrong-scope-and-event/hidden-without-write' {
        Fixture 'max'; $before = Dump
        foreach ($plan in @((Plan 'vk'), (Plan 'max' $false '' '1'), (Plan 'max' $false '' $channel '1'), (Plan 'max' $false '' $channel $user 'other'))) {
            Assert-True ((Execute $plan).Outcome -ceq 'NOT_FOUND') 'Foreign identity was exposed'
        }
        Assert-True ((Dump) -ceq $before) 'Wrong scope wrote data'
    }
    Case 'sql-failure/transaction-rollback' {
        Fixture 'max'; $review = Execute (Plan 'max'); $before = Dump
        $null = Query 'CREATE FUNCTION fail_reconcile() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION ''injected failure''; END $$; CREATE TRIGGER fail_reconcile BEFORE UPDATE ON integration_outbound_event_deliveries FOR EACH ROW EXECUTE FUNCTION fail_reconcile();'
        $rejected = $false
        try { $null = Execute (Plan 'max' $true $review.SnapshotSha256) } catch { $rejected = $true }
        Assert-True ($rejected -and (Dump) -ceq $before) 'Failed mutation was reported as success or committed'
        $null = Query 'DROP TRIGGER fail_reconcile ON integration_outbound_event_deliveries; DROP FUNCTION fail_reconcile();'
    }
    $invalid = @(@(0, 'postgres'), @(1, ''), @(1, 'x;drop'), @(2, 'x y'), @(3, 'x";drop'), @(4, 'MAX'), @(4, ''), @(5, '0'), @(5, '-1'), @(5, '01'), @(5, '9223372036854775808'), @(6, '1.0'), @(6, '1e3'), @(7, ''), @(7, ' '), @(8, $true), @(9, ('a' * 64)))
    for ($i = 0; $i -lt $invalid.Count; $i++) {
        Case "input-guard/$i/rejected-before-native" {
            $values = @($container, 'postgres', 'r289_test', 'public', 'max', $channel, $user, $raw, $false, '')
            $values[$invalid[$i][0]] = $invalid[$i][1]; $rejected = $false
            try { $null = New-FeedbackRecoveryPlan @values } catch { $rejected = $true }
            Assert-True $rejected 'Malformed operator input accepted'
        }
    }
    Case 'native-nonzero/never-success' {
        $rejected = $false
        try { $null = Invoke-FeedbackNative $psql ($nativeArgs + @('--invalid-r289-option')) '' } catch { $rejected = $true }
        Assert-True $rejected 'Failed native process was reported as success'
    }
} catch { $failure = $_ }
finally {
    $stopped = $false
    if ($started -or (Test-Path -LiteralPath (Join-Path $data 'postmaster.pid'))) {
        if (-not [IO.Path]::GetFullPath($data).StartsWith([IO.Path]::GetFullPath($output) + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Owned PostgreSQL path escaped' }
        try { Control-Postgres $false; $stopped = $true } catch { if (-not $failure) { $failure = $_ } }
    }
    [pscustomobject]@{ Phase = 'R289'; Tests = $cases.Count; Passed = -not [bool]$failure; Cases = @($cases.ToArray()); PostgreSQLStopped = $stopped; WorkingMachineAccessed = $false; DockerUsed = $false; Output = $output } | ConvertTo-Json -Depth 7 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $output 'proof.json')
}
if ($failure) { throw $failure }
Write-Output ('GREEN: ' + $cases.Count + ' cases on disposable PostgreSQL; evidence ' + $output)
