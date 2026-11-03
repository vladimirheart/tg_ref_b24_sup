param(
 [Parameter(Mandatory=$true)][ValidatePattern('^sha256:[0-9a-f]{64}$')][string]$RedisImage,
 [Parameter(Mandatory=$true)][ValidatePattern('^sha256:[0-9a-f]{64}$')][string]$MavenImage,
 [Parameter(Mandatory=$true)][string]$WrapperDirectory,
 [Parameter(Mandatory=$true)][string]$MavenRepository,
 [Parameter(Mandatory=$true)][string]$OutputDirectory
)
# Only disposable isolated resources; no Compose/.env/live endpoints/host Docker socket.
$ErrorActionPreference='Stop'
$module=$PSScriptRoot;$javaRoot=Split-Path $module -Parent
$owner='01-278-r282-session-loss';$nonce=[guid]::NewGuid().ToString()
$network='iguana-r282-'+$nonce;$redisName=$network+'-redis';$builderName=$network+'-maven'
$password='disposable-'+[guid]::NewGuid().ToString('N')
$redisId=$null;$builderId=$null;$networkId=$null
function Invoke-OwnedDocker([string[]]$Arguments) {
 $old=$ErrorActionPreference;$ErrorActionPreference='Continue'
 try {$raw=@(& docker.exe @Arguments 2>&1);$code=$LASTEXITCODE} finally {$ErrorActionPreference=$old}
 if($code -ne 0){throw ('Owned Docker operation failed: '+$Arguments[0])}
 return ($raw -join "`n")
}
function Inspect-Owned([string]$Kind,[string]$Id) {
 $parsed=ConvertFrom-Json -InputObject (Invoke-OwnedDocker -Arguments @($Kind,'inspect',$Id))
 return @($parsed)[0]
}
function Assert-Labels($Labels) {
 if($Labels.'io.iguana.owner' -cne $owner -or $Labels.'io.iguana.test-nonce' -cne $nonce){throw 'Ownership mismatch; refusing mutation'}
}
function Assert-Container([string]$Id,[string]$Name,[string]$Image) {
 $c=Inspect-Owned 'container' $Id;Assert-Labels $c.Config.Labels
 if($c.Id -cne $Id -or $c.Name -cne ('/'+$Name) -or $c.Image -cne $Image -or $c.HostConfig.NetworkMode -cne $network -or @($c.HostConfig.PortBindings.PSObject.Properties).Count){throw 'Container isolation differs'}
 return $c
}
if(Test-Path -LiteralPath $OutputDirectory){throw 'Use a fresh output directory; prior evidence is never overwritten'}
$wrapper=(Resolve-Path -LiteralPath $WrapperDirectory).Path
$repository=(Resolve-Path -LiteralPath $MavenRepository).Path
if(-not (Test-Path -LiteralPath (Join-Path $wrapper 'apache-maven-3.9.6/bin/mvn'))){throw 'Prepared repository Maven wrapper 3.9.6 is required'}
$null=New-Item -ItemType Directory -Path $OutputDirectory
$output=(Resolve-Path -LiteralPath $OutputDirectory).Path
$beforeIds=@((Invoke-OwnedDocker -Arguments @('ps','-aq','--no-trunc')) -split "`n" | Where-Object {$_})
$before=@(foreach($id in $beforeIds){$c=Inspect-Owned 'container' $id;[pscustomobject]@{Id=$c.Id;Image=$c.Image;Started=$c.State.StartedAt;Running=$c.State.Running;Restarts=$c.RestartCount}})
$labels=@('--label',"io.iguana.owner=$owner",'--label',"io.iguana.test-nonce=$nonce")
try {
 foreach($image in @($RedisImage,$MavenImage)){$c=Inspect-Owned 'image' $image;if($c.Id -cne $image){throw 'Exact cached images required; no pull'}}
 $networkId=(Invoke-OwnedDocker -Arguments (@('network','create','--internal')+$labels+@($network))).Trim()
 $net=Inspect-Owned 'network' $networkId;Assert-Labels $net.Labels;if(-not $net.Internal){throw 'Test network must be internal'}
 $redisId=(Invoke-OwnedDocker -Arguments (@('run','-d','--pull','never','--name',$redisName)+$labels+@(
  '--network',$network,'--network-alias','r282-redis','--memory','64m','--memory-swap','64m','--cpus','1',
  '--user','999:999','--read-only','--cap-drop','ALL','--security-opt','no-new-privileges',
  '--tmpfs','/data:rw,uid=999,gid=999,mode=700,size=16m',$RedisImage,
  'redis-server','--appendonly','no','--maxmemory','32mb','--maxmemory-policy','noeviction','--requirepass',$password))).Trim()
 $null=Assert-Container $redisId $redisName $RedisImage
 $ready=$false
 for($attempt=0;$attempt -lt 30;$attempt++){
  try{$ready=(Invoke-OwnedDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$redisId,'redis-cli','PING')).Trim() -ceq 'PONG'}catch{$ready=$false}
  if($ready){break};Start-Sleep -Milliseconds 250
 }
 if(-not $ready){throw 'Disposable Redis not ready'}
 foreach($pair in @(@('r282:owner',$nonce),@('r282:acceptance-only','true'))){$null=Invoke-OwnedDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$redisId,'redis-cli','SET',$pair[0],$pair[1])}
 $builderId=(Invoke-OwnedDocker -Arguments (@('create','--pull','never','--name',$builderName)+$labels+@(
  '--network',$network,'--memory','768m','--memory-swap','768m','--cpus','1','--cap-drop','ALL','--security-opt','no-new-privileges',
  '--mount',"type=bind,source=$javaRoot,target=/input,readonly",
  '--mount',"type=bind,source=$wrapper,target=/wrapper-cache,readonly",
  '--mount',"type=bind,source=$repository,target=/maven-cache",
  '-e',"QUESTIONNAIRE_REDIS_OWNER=$nonce",'-e','QUESTIONNAIRE_REDIS_HOST=r282-redis','-e','QUESTIONNAIRE_REDIS_PORT=6379',
  '-e',"QUESTIONNAIRE_REDIS_PASSWORD=$password",'-e','MAVEN_OPTS=-Xmx96m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2',
  '-e','JAVA_TOOL_OPTIONS=-Xmx192m -XX:MaxMetaspaceSize=192m -XX:ActiveProcessorCount=2',
  $MavenImage,'sh','/input/bot-max/redis-session-loss-check-v2.sh'))).Trim()
 $null=Assert-Container $builderId $builderName $MavenImage
 $null=Invoke-OwnedDocker -Arguments @('start',$builderId)
 $exitCode=[int](Invoke-OwnedDocker -Arguments @('wait',$builderId)).Trim()
 Invoke-OwnedDocker -Arguments @('logs',$builderId) | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'maven.log')
 try { $null=Invoke-OwnedDocker -Arguments @('cp',($builderId+':/work/java-bot/bot-max/target/surefire-reports'),(Join-Path $output 'max-reports')) } catch { if($exitCode -eq 0){throw} }
 if($exitCode -ne 0){throw ('Acceptance Maven exited '+$exitCode+'; inspect retained logs')}
 $tests=0
 $reports=@(Get-ChildItem -LiteralPath (Join-Path $output 'max-reports') -Filter 'TEST-*.xml')
 if($reports.Count -lt 25){throw 'MAX regression suites missing'}
 foreach($name in @('MaxQuestionnaireSelectionTest','MaxSessionLossRedisIT','MaxCanonicalFollowupTest','MaxActiveTicketAvailabilityTest')){
  if(-not (Test-Path -LiteralPath (Join-Path $output ('max-reports/TEST-com.example.supportbot.max.'+$name+'.xml')))){throw ('Required suite missing: '+$name)}
 }
 $null=Invoke-OwnedDocker -Arguments @('cp',($builderId+':/work/java-bot/bot-core/target/surefire-reports'),(Join-Path $output 'core-reports'))
 $coreReports=@(Get-ChildItem -LiteralPath (Join-Path $output 'core-reports') -Filter 'TEST-*.xml')
 if($coreReports.Count -ne 2){throw 'Canonical signed HTTP suites missing'}
 foreach($reportFile in @($reports)+@($coreReports)){
  [xml]$r=Get-Content -Raw -Encoding UTF8 -LiteralPath $reportFile.FullName
  if([int]$r.testsuite.failures -or [int]$r.testsuite.errors -or [int]$r.testsuite.skipped -or [int]$r.testsuite.tests -le 0){throw ('Incomplete/failed suite: '+$reportFile.Name)}
  if($r.testsuite.name -ceq 'com.example.supportbot.max.MaxSessionLossRedisIT'){
   $expectedCases=@{publishedRootSurvivesSessionLossCatalogRebindAndDedupLoss=4;immutableV4StillRejectsMediaAfterRealKeyLoss=16;invalidRootReleasesRealRedisClaimAndSameEventRecovers=20}
   foreach($method in $expectedCases.Keys){
    $count=@($r.testsuite.testcase | Where-Object {([string]$_.name).StartsWith($method)}).Count
    if($count -ne $expectedCases[$method]){throw ('Missing session-loss parameter cases: '+$method)}
   }
  }
  $tests+=[int]$r.testsuite.tests
 }
 $size=[int](Invoke-OwnedDocker -Arguments @('exec','-e',"REDISCLI_AUTH=$password",$redisId,'redis-cli','DBSIZE')).Trim()
 if($size -ne 2){throw 'Synthetic test namespaces not cleaned'}
 foreach($pair in @(@($builderId,$builderName,$MavenImage),@($redisId,$redisName,$RedisImage))){$c=Assert-Container $pair[0] $pair[1] $pair[2];if($c.State.OOMKilled){throw 'Disposable resource OOM'}}
 [pscustomobject]@{Passed=$true;Tests=$tests;Failures=0;Errors=0;Skips=0;RedisImage=$RedisImage;MavenImage=$MavenImage;RedisKeys=$size;ProviderWrites=$false;Time=(Get-Date -Format o)} | ConvertTo-Json | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'acceptance-proof.json')
} finally {
 foreach($pair in @(@($builderId,$builderName,$MavenImage),@($redisId,$redisName,$RedisImage))){if($pair[0]){
  $null=Assert-Container $pair[0] $pair[1] $pair[2]
  $null=Invoke-OwnedDocker -Arguments @('stop','--time','5',$pair[0]);$null=Invoke-OwnedDocker -Arguments @('rm',$pair[0])
 }}
 if($networkId){$n=Inspect-Owned 'network' $networkId;Assert-Labels $n.Labels;if($n.Id -cne $networkId -or @($n.Containers.PSObject.Properties).Count){throw 'Network cleanup identity/attachment mismatch'};$null=Invoke-OwnedDocker -Arguments @('network','rm',$networkId)}
 $afterIds=@((Invoke-OwnedDocker -Arguments @('ps','-aq','--no-trunc')) -split "`n" | Where-Object {$_})
 if(@(Compare-Object $beforeIds $afterIds).Count){throw 'Existing container set differs'}
 foreach($b in $before){$c=Inspect-Owned 'container' $b.Id;if($c.Image -cne $b.Image -or $c.State.StartedAt -cne $b.Started -or $c.State.Running -ne $b.Running -or $c.RestartCount -ne $b.Restarts){throw 'Existing container state changed'}}
 [pscustomobject]@{OwnedContainersRemoved=$true;OwnedNetworkRemoved=$true;ExistingContainersUnchanged=$before.Count;NamedVolumesCreated=0;Time=(Get-Date -Format o)} | ConvertTo-Json | Out-File -Encoding utf8 -LiteralPath (Join-Path $output 'cleanup-proof.json')
}
