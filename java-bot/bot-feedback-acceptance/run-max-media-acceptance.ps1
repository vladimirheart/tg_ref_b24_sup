param(
    [string]$OutputDirectory,
    [switch]$ValidateOnly,
    [switch]$SelfTest
)
# Test-only: no Compose, application configuration, migrations, deployment or existing-container mutations.
$ErrorActionPreference = 'Stop'
$rootDirectory = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$owner = '01-278-r287-max-media'
$nonce = [guid]::NewGuid().ToString('N')
function Resolve-OwnedOutput([string]$Root, [string]$Target) {
    $parent = [IO.Path]::GetFullPath((Join-Path $Root '.tmp'))
    $resolved = [IO.Path]::GetFullPath($Target)
    if (-not $resolved.StartsWith($parent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Output must be a fresh child directory of repository/.tmp'
    }
    return $resolved
}
function Invoke-Native([string]$Program, [string[]]$NativeArguments) {
    if (-not (Get-Command $Program -ErrorAction SilentlyContinue)) { throw ('Required executable missing: ' + $Program) }
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try { $lines = @(& $Program @NativeArguments 2>&1); $code = $LASTEXITCODE }
    finally { $ErrorActionPreference = $previous }
    if ($code -ne 0) { throw ('Native operation failed: ' + $Program + ' ' + $NativeArguments[0]) }
    return ($lines -join "`n").TrimEnd()
}
function Get-TextHash([string]$Text) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($Text)))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Assert-OwnedNetwork($Network, [string]$Id) {
    $role = $Network.Labels.'io.iguana.fixture-role'
    $validIsolation = ($role -ceq 'services' -and $Network.Internal -eq $true) -or ($role -ceq 'ingress' -and $Network.Internal -eq $false)
    if ($Network.Id -cne $Id -or $Network.Labels.'io.iguana.owner' -cne $owner -or $Network.Labels.'io.iguana.test-nonce' -cne $nonce -or $Network.Driver -cne 'bridge' -or -not $validIsolation -or $null -eq $Network.Containers -or @($Network.Containers.PSObject.Properties).Count) {
        throw 'Owned network cleanup mismatch'
    }
}
function Invoke-IntegrationMatrix([string[]]$Reactor, [string]$RunNonce) {
    $media = 0; $principal = 0; $firstFailure = $null
    # Docker 29 requires API >=1.44; pin docker-java's protocol without changing daemon settings.
    $integrationArguments = $Reactor + @('-Dapi.version=1.44', ('-Dmax.media.acceptance.nonce=' + $RunNonce))
    try {
        Invoke-Maven 'java-bot' 'integration.log' ($integrationArguments + @('-Dtest=MaxFollowupMediaChainIT'))
        $media = Assert-Report 'MaxFollowupMediaChainIT' 42
    } catch { $firstFailure = $_ }
    # Preserve a real partial principal result even if the MAX fixture fails during startup.
    try {
        Invoke-Maven 'java-bot' 'principals.log' ($integrationArguments + @('-Dtest=ChildTransportPrincipalIT'))
        $principal = Assert-Report 'ChildTransportPrincipalIT' 15
    } catch { if (-not $firstFailure) { $firstFailure = $_ } }
    return [pscustomobject]@{ Media = $media; Principals = $principal; Failure = $firstFailure }
}
if ($SelfTest) {
    $valid = Resolve-OwnedOutput $rootDirectory (Join-Path $rootDirectory ('.tmp/r287-' + $nonce))
    if (-not $valid.EndsWith($nonce)) { throw 'Output guard self-test failed' }
    foreach ($bad in @($rootDirectory, (Join-Path $rootDirectory '.tmp'), (Join-Path $rootDirectory '.tmp/../outside'))) {
        $rejected = $false
        try { $null = Resolve-OwnedOutput $rootDirectory $bad } catch { $rejected = $true }
        if (-not $rejected) { throw 'Output escape was accepted' }
    }
    if ((Get-TextHash 'abc') -cne 'ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad') { throw 'Evidence hash self-test failed' }
    $rejected = $false
    try { $null = Invoke-Native ('r287-missing-' + $nonce) @('unused') } catch { $rejected = $true }
    if (-not $rejected) { throw 'Missing executable was reported as successful' }
    $rejected = $false
    try { $null = Invoke-Native (Get-Process -Id $PID).Path @('-NoProfile', '-NonInteractive', '-Command', 'exit 7') } catch { $rejected = $true }
    if (-not $rejected) { throw 'Failed native process was reported as successful' }
    foreach ($role in @('services', 'ingress')) {
        $network = [pscustomobject]@{ Id = 'owned'; Driver = 'bridge'; Internal = ($role -ceq 'services'); Containers = [pscustomobject]@{}; Labels = [pscustomobject]@{ 'io.iguana.owner' = $owner; 'io.iguana.test-nonce' = $nonce; 'io.iguana.fixture-role' = $role } }
        Assert-OwnedNetwork $network 'owned'
    }
    foreach ($kind in @('services-external', 'ingress-internal', 'foreign-owner', 'foreign-nonce', 'unknown-role', 'foreign-driver', 'foreign-id', 'occupied')) {
        $network = [pscustomobject]@{ Id = 'owned'; Driver = 'bridge'; Internal = $true; Containers = [pscustomobject]@{}; Labels = [pscustomobject]@{ 'io.iguana.owner' = $owner; 'io.iguana.test-nonce' = $nonce; 'io.iguana.fixture-role' = 'services' } }
        switch ($kind) {
            'services-external' { $network.Internal = $false }
            'ingress-internal' { $network.Labels.'io.iguana.fixture-role' = 'ingress' }
            'foreign-owner' { $network.Labels.'io.iguana.owner' = 'foreign' }
            'foreign-nonce' { $network.Labels.'io.iguana.test-nonce' = 'foreign' }
            'unknown-role' { $network.Labels.'io.iguana.fixture-role' = 'foreign' }
            'foreign-driver' { $network.Driver = 'host' }
            'foreign-id' { $network.Id = 'foreign' }
            'occupied' { $network.Containers = [pscustomobject]@{ foreign = [pscustomobject]@{} } }
        }
        $rejected = $false
        try { Assert-OwnedNetwork $network 'owned' } catch { $rejected = $true }
        if (-not $rejected) { throw ('Unsafe network cleanup was accepted: ' + $kind) }
    }
    # Exercise the real matrix control flow with fake Maven functions; never start infrastructure here.
    function Invoke-Maven([string]$Project, [string]$LogName, [string[]]$MavenArguments) {
        if ($MavenArguments -cnotcontains '-Dapi.version=1.44' -or $MavenArguments -cnotcontains ('-Dmax.media.acceptance.nonce=' + $nonce)) { throw 'Missing Docker protocol/nonce' }
        $script:selfTestCalls += $LogName
        if ($script:selfTestFailures -contains $LogName) { throw ('injected ' + $LogName) }
    }
    function Assert-Report([string]$Name, [int]$Expected) { return $Expected }
    foreach ($kind in @('green', 'media-failure', 'principal-failure', 'both-fail')) {
        $script:selfTestCalls = @(); $script:selfTestFailures = @()
        if ($kind -in @('media-failure', 'both-fail')) { $script:selfTestFailures += 'integration.log' }
        if ($kind -in @('principal-failure', 'both-fail')) { $script:selfTestFailures += 'principals.log' }
        $matrix = Invoke-IntegrationMatrix @('test') $nonce
        $expectedMedia = if ($script:selfTestFailures -contains 'integration.log') { 0 } else { 42 }
        $expectedPrincipals = if ($script:selfTestFailures -contains 'principals.log') { 0 } else { 15 }
        if (($script:selfTestCalls -join ',') -cne 'integration.log,principals.log' -or $matrix.Media -ne $expectedMedia -or $matrix.Principals -ne $expectedPrincipals -or [bool]$matrix.Failure -ne ($kind -cne 'green')) { throw ('Incorrect partial integration result: ' + $kind) }
        if ($kind -ceq 'both-fail' -and $matrix.Failure.Exception.Message -cne 'injected integration.log') { throw 'First integration failure was lost' }
    }
    Write-Output 'SELF_TEST_GREEN; Docker, builds and integration tests were not executed.'
    return
}
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $rootDirectory ('.tmp/r287-acceptance-' + $nonce) }
$output = Resolve-OwnedOutput $rootDirectory $OutputDirectory
if (Test-Path -LiteralPath $output) { throw 'Use a fresh output directory; previous evidence is retained' }
if ((Invoke-Native git @('-C', $rootDirectory, 'status', '--porcelain', '--untracked-files=all'))) { throw 'Commit the source changes before running against an isolated checkout' }
$sourceCommit = (Invoke-Native git @('-C', $rootDirectory, 'rev-parse', 'HEAD')).Trim()
if ($sourceCommit -notmatch '^[a-f0-9]{40}$') { throw 'Invalid source commit' }
$null = Invoke-Native git @('-C', $rootDirectory, 'cat-file', '-e', ($sourceCommit + ':java-bot/bot-feedback-acceptance/src/test/java/com/example/acceptance/MaxFollowupMediaChainIT.java'))
$javaVersion = Invoke-Native java @('-version')
if ($javaVersion -notmatch 'version "17[."]') { throw 'JDK 17 and the repository Maven wrappers are required' }
$context = $null
function Invoke-Docker([string[]]$DockerArguments) { return Invoke-Native docker (@('--context', $context) + $DockerArguments) }
function Get-Container([string]$Id) {
    $objects = @(ConvertFrom-Json -InputObject (Invoke-Docker @('container', 'inspect', $Id)))
    if ($objects.Count -ne 1 -or $objects[0].Id -cne $Id) { throw 'Container identity mismatch' }
    return $objects[0]
}
function Get-ContainerSnapshot([string]$Id) {
    $c = Get-Container $Id
    return [pscustomobject]@{
        Id = $c.Id; Image = $c.Image; Started = $c.State.StartedAt; Running = $c.State.Running; Restarts = $c.RestartCount
        ConfigHash = Get-TextHash ($c.Config | ConvertTo-Json -Depth 100 -Compress)
        HostConfigHash = Get-TextHash ($c.HostConfig | ConvertTo-Json -Depth 100 -Compress)
    }
}
$before = @()
$dockerHost = $null
if (-not $ValidateOnly) {
    $context = (Invoke-Native docker @('context', 'show')).Trim()
    $dockerHost = (Invoke-Docker @('context', 'inspect', $context, '--format', '{{.Endpoints.docker.Host}}')).Trim()
    if ($dockerHost -notmatch '^(npipe:////\./pipe/[a-zA-Z0-9_-]+|unix:///[^\r\n]+)$') { throw 'Only an explicitly local Docker engine is allowed' }
    if ((Invoke-Docker @('info', '--format', '{{.OSType}}')).Trim() -cne 'linux') { throw 'A running local Linux Docker engine is required' }
    $ids = @((Invoke-Docker @('ps', '-aq', '--no-trunc')) -split "`n" | Where-Object { $_ })
    $before = @(foreach ($id in $ids) { Get-ContainerSnapshot $id })
}
$null = New-Item -ItemType Directory -Path $output
$before | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'existing-containers-before.json')
$sandbox = Join-Path $output 'source'
$null = Invoke-Native git @('-C', $rootDirectory, 'worktree', 'add', '--detach', $sandbox, $sourceCommit)
$sourceFiles = @((Invoke-Native git @('-C', $sandbox, 'ls-files')) -split "`n" | Where-Object { $_ })
$sourceProof = @(foreach ($file in $sourceFiles) { [pscustomobject]@{ Path = $file; Sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $sandbox $file)).Hash } })
$sourceProof | ConvertTo-Json -Depth 4 | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'source-hashes.json')
function Invoke-Maven([string]$Project, [string]$LogName, [string[]]$MavenArguments) {
    Push-Location (Join-Path $sandbox $Project)
    $previous = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd @MavenArguments *> (Join-Path $output $LogName)
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previous; Pop-Location }
    if ($code -ne 0) { throw ('Maven failed; inspect ' + $LogName) }
}
function Assert-Report([string]$Name, [int]$Expected) {
    $file = Join-Path $sandbox ('java-bot/bot-feedback-acceptance/target/surefire-reports/TEST-com.example.acceptance.' + $Name + '.xml')
    [xml]$report = Get-Content -Raw -Encoding UTF8 -LiteralPath $file
    if ([int]$report.testsuite.tests -ne $Expected -or [int]$report.testsuite.failures -or [int]$report.testsuite.errors -or [int]$report.testsuite.skipped) {
        throw ('Missing, skipped or failed cases: ' + $Name)
    }
    Copy-Item -LiteralPath $file -Destination (Join-Path $output ([IO.Path]::GetFileName($file)))
    return [int]$report.testsuite.tests
}
$savedMavenOptions = $env:MAVEN_OPTS
$savedDockerHost = $env:DOCKER_HOST
$failure = $null
$compiled = $false
$contracts = 0
$integration = 0
$principals = 0
$integrationStarted = $false
$unchanged = $null
try {
    $env:MAVEN_OPTS = '-Xmx768m -XX:ActiveProcessorCount=2'
    if ($dockerHost) { $env:DOCKER_HOST = $dockerHost }
    Invoke-Maven 'spring-panel' 'panel-compile.log' @('compile', '-DskipTests', '-Dmaven.compiler.useIncrementalCompilation=false', '-Dstyle.color=never')
    $compiled = $true
    $reactor = @('-f', 'pom.xml', '-Pfeedback-chain-acceptance', '-pl', ':bot-feedback-acceptance', '-am', 'test', '-Dsurefire.failIfNoSpecifiedTests=false', '-Dmaven.compiler.useIncrementalCompilation=false', '-DargLine=-Xmx384m', '-Dstyle.color=never')
    Invoke-Maven 'java-bot' 'contracts.log' ($reactor + @('-Dtest=MaxMediaChainContractTest,MaxLinkedMediaWireContractTest,MaxMediaFixtureNetworkTest,MaxMediaRepositoryAdaptersTest,MaxMediaHttpQueryTest'))
    $contracts = Assert-Report 'MaxMediaChainContractTest' 26
    $contracts += (Assert-Report 'MaxLinkedMediaWireContractTest' 8)
    $contracts += (Assert-Report 'MaxMediaFixtureNetworkTest' 41)
    $contracts += (Assert-Report 'MaxMediaHttpQueryTest' 9)
    $contracts += (Assert-Report 'MaxMediaRepositoryAdaptersTest' 6)
    if (-not $ValidateOnly) {
        $integrationStarted = $true
        $matrix = Invoke-IntegrationMatrix $reactor $nonce
        $integration = $matrix.Media; $principals = $matrix.Principals; $failure = $matrix.Failure
    }
} catch { $failure = $_ }
finally {
    $env:MAVEN_OPTS = $savedMavenOptions
    $env:DOCKER_HOST = $savedDockerHost
    # Keep failed/startup XML too; a failed Maven invocation never reaches Assert-Report.
    try {
        $reports = Join-Path $sandbox 'java-bot/bot-feedback-acceptance/target/surefire-reports'
        if (Test-Path -LiteralPath $reports) {
            Get-ChildItem -LiteralPath $reports -Filter 'TEST-*.xml' -File | ForEach-Object {
                Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $output $_.Name)
            }
        }
    } catch { if (-not $failure) { $failure = $_ } }
    if (-not $ValidateOnly) {
        try {
            $ownedIds = @((Invoke-Docker @('ps', '-aq', '--no-trunc', '--filter', ('label=io.iguana.owner=' + $owner), '--filter', ('label=io.iguana.test-nonce=' + $nonce))) -split "`n" | Where-Object { $_ })
            foreach ($id in $ownedIds) {
                $c = Get-Container $id
                if ($c.Config.Labels.'io.iguana.owner' -cne $owner -or $c.Config.Labels.'io.iguana.test-nonce' -cne $nonce -or $c.Config.Image -notin @('postgres:16-alpine', 'rabbitmq:3.13-management-alpine', 'alpine/socat:1.7.4.3-r0')) { throw 'Owned cleanup identity mismatch' }
                if ($c.Config.Image -ceq 'alpine/socat:1.7.4.3-r0' -and $c.Config.Labels.'io.iguana.fixture-role' -cne 'relay') { throw 'Owned relay identity mismatch' }
                $null = Invoke-Docker @('rm', '-f', '-v', $id)
            }
            $networks = @((Invoke-Docker @('network', 'ls', '-q', '--no-trunc', '--filter', ('label=io.iguana.owner=' + $owner), '--filter', ('label=io.iguana.test-nonce=' + $nonce))) -split "`n" | Where-Object { $_ })
            foreach ($id in $networks) {
                $objects = @(ConvertFrom-Json -InputObject (Invoke-Docker @('network', 'inspect', $id)))
                if ($objects.Count -ne 1) { throw 'Network identity missing' }
                $n = $objects[0]
                Assert-OwnedNetwork $n $id
                $null = Invoke-Docker @('network', 'rm', $id)
            }
            $after = @(foreach ($b in $before) { Get-ContainerSnapshot $b.Id })
            if ((ConvertTo-Json -Depth 8 -Compress -InputObject $before) -cne (ConvertTo-Json -Depth 8 -Compress -InputObject $after)) { throw 'Existing containers changed; inspect evidence' }
            $after | ConvertTo-Json -Depth 8 | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'existing-containers-after.json')
            $unchanged = $true
        } catch { $unchanged = $false; if (-not $failure) { $failure = $_ } }
    }
    [pscustomobject]@{
        Phase = 'R301'; SourceCommit = $sourceCommit; Nonce = $nonce; Compiled = $compiled; ContractTests = $contracts; PrincipalTests = $principals
        FixtureTopology = 'internal-services-loopback-relay'; DockerApiVersion = if ($ValidateOnly) { $null } else { '1.44' }
        IntegrationAttempted = $integrationStarted; IntegrationExecuted = ($integration -gt 0 -or $principals -gt 0)
        MaxMediaIntegrationTests = $integration; PrincipalIntegrationExecuted = ($principals -gt 0)
        IntegrationTests = ($integration + $principals)
        IntegrationPassed = if ($ValidateOnly) { $null } else { (-not $failure -and $integration -eq 42 -and $principals -eq 15) }
        ExistingContainersUnchanged = $unchanged; Deployment = $false; WorkingDataUsed = $false
        ValidationOnly = [bool]$ValidateOnly; Failed = [bool]$failure; Time = (Get-Date -Format o)
    } | ConvertTo-Json -Depth 5 | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'r292-proof.json')
}
if ($failure) { throw $failure }
if ($ValidateOnly) { Write-Output ('PREPARED; integration not executed. Evidence: ' + $output) }
else { Write-Output ('INTEGRATION_GREEN; 42 media + 15 principal cases, no skips. Evidence: ' + $output) }
