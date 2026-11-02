param(
    [Parameter(Mandatory=$true)][ValidatePattern('^sha256:[0-9a-f]{64}$')][string]$RedisImage
)
# Приёмка только на новом disposable Redis. Compose/.env/рабочие контейнеры не используются.
$ErrorActionPreference='Stop'
$module=$PSScriptRoot
$javaRoot=Split-Path $module -Parent
$docker=(Get-Command docker.exe -ErrorAction Stop).Source
$name='iguana-r270-redis-acceptance'
$network='iguana_r270_redis_acceptance'
$volume='iguana_r270_redis_acceptance_data'
$owner='01-278-r270-redis'
$nonce=[guid]::NewGuid().ToString()
$password='disposable-'+[guid]::NewGuid().ToString('N')
$containerId=$null;$networkId=$null;$volumeCreated=$false
$names=@('MAVEN_OPTS','JAVA_TOOL_OPTIONS','QUESTIONNAIRE_REDIS_OWNER','QUESTIONNAIRE_REDIS_PASSWORD','QUESTIONNAIRE_REDIS_CONTAINER_ID','QUESTIONNAIRE_REDIS_IMAGE_ID','QUESTIONNAIRE_REDIS_PORT')
$previous=@{};foreach($key in $names){$previous[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}
function Invoke-AcceptanceDocker([string[]]$Arguments) {
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try {$result=& $docker @Arguments 2>&1;$code=$LASTEXITCODE} finally {$ErrorActionPreference=$old}
    if($code -ne 0){throw "Disposable Redis Docker operation failed: $($Arguments[0])"}
    return ($result -join "`n")
}
function Assert-Owner($Labels) {
    if($Labels.'io.iguana.owner' -cne $owner -or $Labels.'io.iguana.test-nonce' -cne $nonce){throw 'Disposable resource ownership differs; refusing mutation'}
}
function Inspect-Json([string[]]$Arguments) {
    $parsed=Invoke-AcceptanceDocker -Arguments $Arguments|ConvertFrom-Json
    return @($parsed)[0]
}
$labels=@('--label',"io.iguana.owner=$owner",'--label',"io.iguana.test-nonce=$nonce")
try {
    $image=Inspect-Json -Arguments @('image','inspect',$RedisImage)
    if($image.Id -cne $RedisImage){throw 'Use an exact, locally cached Redis image ID; no pull is performed'}
    if((Invoke-AcceptanceDocker -Arguments @('ps','-a','--filter',"name=^/$name$",'--format','{{.ID}}')).Trim()){throw 'Acceptance container name already occupied'}
    if((Invoke-AcceptanceDocker -Arguments @('network','ls','--filter',"name=^$network$",'--format','{{.ID}}')).Trim()){throw 'Acceptance network name already occupied'}
    if((Invoke-AcceptanceDocker -Arguments @('volume','ls','--filter',"name=^$volume$",'--format','{{.Name}}')).Trim()){throw 'Acceptance volume name already occupied'}
    # Dedicated bridge: internal networks on Docker Desktop may not publish host ports.
    $networkId=(Invoke-AcceptanceDocker -Arguments (@('network','create')+$labels+@($network))).Trim()
    $null=Invoke-AcceptanceDocker -Arguments (@('volume','create')+$labels+@($volume));$volumeCreated=$true
    $listener=[Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback,0)
    try {$listener.Start();$port=$listener.LocalEndpoint.Port} finally {$listener.Stop()}
    $containerId=(Invoke-AcceptanceDocker -Arguments (@('run','-d','--pull','never','--name',$name)+$labels+@(
        '--network',$network,'--memory','64m','--memory-swap','64m','--cpus','1',
        '--publish',"127.0.0.1:${port}:6379",'--mount',"type=volume,src=$volume,dst=/data",$RedisImage,
        'redis-server','--appendonly','yes','--appendfsync','always','--maxmemory','32mb',
        '--maxmemory-policy','noeviction','--requirepass',$password))).Trim()
    if($containerId -cnotmatch '^[0-9a-f]{64}$'){throw 'Invalid owned container ID'}
    $info=Inspect-Json -Arguments @('inspect',$containerId);Assert-Owner $info.Config.Labels
    $port=[int]$info.NetworkSettings.Ports.'6379/tcp'[0].HostPort
    if($info.Id -cne $containerId -or $info.Name -cne ('/'+$name) -or $info.Image -cne $RedisImage -or
        $info.HostConfig.Memory -ne 67108864 -or $info.HostConfig.PortBindings.'6379/tcp'[0].HostIp -cne '127.0.0.1' -or $port -lt 1024){throw 'Unsafe disposable Redis configuration'}
    $ready=$false
    for($attempt=0;$attempt -lt 30;$attempt++) {
        try {$ready=(Invoke-AcceptanceDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$containerId,'redis-cli','PING')).Trim() -ceq 'PONG'} catch {$ready=$false}
        if($ready){break};Start-Sleep -Milliseconds 250
    }
    if(-not $ready){throw 'Disposable Redis did not become ready'}
    foreach($entry in @(@('r270:owner',$nonce),@('r270:acceptance-only','true'))) {
        if((Invoke-AcceptanceDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$containerId,'redis-cli','SET',$entry[0],$entry[1])).Trim() -cne 'OK'){throw 'Acceptance sentinel write failed'}
    }
    $env:QUESTIONNAIRE_REDIS_OWNER=$nonce;$env:QUESTIONNAIRE_REDIS_PASSWORD=$password
    $env:QUESTIONNAIRE_REDIS_CONTAINER_ID=$containerId;$env:QUESTIONNAIRE_REDIS_IMAGE_ID=$RedisImage;$env:QUESTIONNAIRE_REDIS_PORT=[string]$port
    $env:MAVEN_OPTS='-Xmx96m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2'
    $env:JAVA_TOOL_OPTIONS='-Xmx192m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2'
    $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
    try {& (Join-Path $javaRoot 'mvnw.cmd') -B -o -f (Join-Path $javaRoot 'pom.xml') -pl ':bot-max' -am '-Dtest=MaxQuestionnaireRedisIT' '-Dsurefire.failIfNoSpecifiedTests=false' test;$code=$LASTEXITCODE} finally {$ErrorActionPreference=$old}
    if($code -ne 0){throw "Redis acceptance Maven failed ($code)"}
    [xml]$report=Get-Content (Join-Path $module 'target\surefire-reports\TEST-com.example.supportbot.max.MaxQuestionnaireRedisIT.xml') -Raw -Encoding UTF8
    if([int]$report.testsuite.tests -lt 112 -or [int]$report.testsuite.failures -ne 0 -or [int]$report.testsuite.errors -ne 0 -or [int]$report.testsuite.skipped -ne 0){throw 'Redis acceptance matrix incomplete or failed'}
    $dbsize=[int](Invoke-AcceptanceDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$containerId,'redis-cli','DBSIZE')).Trim()
    if($dbsize -ne 2){throw 'Test namespaces were not completely cleaned'}
    $info=Inspect-Json -Arguments @('inspect',$containerId);Assert-Owner $info.Config.Labels
    if($info.State.OOMKilled){throw 'Disposable Redis exceeded its memory limit'}
    [ordered]@{Passed=$true;Tests=[int]$report.testsuite.tests;Failures=0;Errors=0;Skips=0;Image=$RedisImage;Container=$containerId;MemoryBytes=67108864;KeysAfterTests=$dbsize;Time=(Get-Date -Format o)}|
        ConvertTo-Json|Set-Content (Join-Path $module 'target\redis-acceptance-proof.json') -Encoding UTF8
} finally {
    try {
        if($containerId) {
            $info=Inspect-Json -Arguments @('inspect',$containerId);Assert-Owner $info.Config.Labels
            if($info.Id -cne $containerId -or $info.Name -cne ('/'+$name) -or $info.Image -cne $RedisImage){throw 'Cleanup container identity differs'}
            if(Test-Path (Join-Path $module 'target')){Invoke-AcceptanceDocker -Arguments @('logs',$containerId)|Out-File (Join-Path $module 'target\redis-acceptance-redis.log') -Encoding UTF8}
            $null=Invoke-AcceptanceDocker -Arguments @('stop','--time','10',$containerId)
            $null=Invoke-AcceptanceDocker -Arguments @('rm',$containerId)
        }
        if($volumeCreated) {
            $info=Inspect-Json -Arguments @('volume','inspect',$volume);Assert-Owner $info.Labels
            if($info.Name -cne $volume){throw 'Cleanup volume identity differs'}
            $null=Invoke-AcceptanceDocker -Arguments @('volume','rm',$volume)
        }
        if($networkId) {
            $info=Inspect-Json -Arguments @('network','inspect',$networkId);Assert-Owner $info.Labels
            if($info.Id -cne $networkId -or $info.Name -cne $network -or @($info.Containers.PSObject.Properties).Count -ne 0){throw 'Cleanup network identity/attachments differ'}
            $null=Invoke-AcceptanceDocker -Arguments @('network','rm',$networkId)
        }
    } finally {foreach($key in $names){[Environment]::SetEnvironmentVariable($key,$previous[$key],'Process')}}
}
