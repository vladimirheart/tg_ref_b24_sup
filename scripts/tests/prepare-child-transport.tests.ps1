param()
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$generator = Join-Path $repository 'scripts/prepare-child-transport.ps1'
. $generator
$fakeMaster = 'r291-test-only-master-012345678901234567890'
$fakeSecure = ConvertTo-SecureString $fakeMaster -AsPlainText -Force
$emptySecure = New-Object Security.SecureString
$testOutput = Join-Path $repository ('.tmp/child-preparation-test-' + [guid]::NewGuid().ToString('N'))
$null = New-Item -ItemType Directory -Path $testOutput
$cases = New-Object 'Collections.Generic.List[string]'
function Assert-Child([bool]$Condition, [string]$Message) { if (-not $Condition) { throw $Message } }
function Expect-ChildFailure([scriptblock]$Action) {
    $failed = $false
    try { $null = & $Action } catch { $failed = $true }
    Assert-Child $failed 'Expected refusal before output creation'
}

& $generator -SelfTest | Out-Null
$cases.Add('Independent Node HMAC and Rabbit hash vectors')
foreach ($transport in @('telegram', 'vk', 'max')) {
    $channel = '9007199254740993'
    $directory = Join-Path $testOutput $transport
    $text = (& $generator -ChannelId $channel -Platform $transport -OutputDirectory $directory -MasterToken $fakeSecure -SignatureSecret $emptySecure) -join "`n"
    $manifest = $text | ConvertFrom-Json
    Assert-Child ($manifest.ChannelId -ceq $channel -and $manifest.Principal -ceq ('iguana_child_' + $channel)) 'BIGINT identity changed'
    Assert-Child ($text -cnotmatch [regex]::Escape($fakeMaster)) 'Master secret appeared in output'
    $acl = Get-Acl -LiteralPath $directory
    Assert-Child $acl.AreAccessRulesProtected 'Output ACL must not inherit grants'
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
    $rules = @($acl.GetAccessRules($true, $true, [Security.Principal.SecurityIdentifier]))
    Assert-Child ($rules.Count -eq 1 -and $rules[0].IdentityReference.Value -ceq $sid) 'Private ACL contains another recipient'
    foreach ($file in $manifest.Files) {
        Assert-Child ((Get-FileHash -LiteralPath (Join-Path $directory $file.Name) -Algorithm SHA256).Hash.ToLowerInvariant() -ceq $file.SHA256) 'Generated file hash mismatch'
    }
    $sql = [IO.File]::ReadAllText((Join-Path $directory 'provision.sql'))
    $expectedPassword = Get-ChildTransportKey $fakeMaster '' 'sql' $transport $channel
    Assert-Child ($sql.Contains("\set password '$expectedPassword'")) 'SQL credential differs from the runtime domain'
    Assert-Child (-not $text.Contains($expectedPassword)) 'Derived SQL password appeared in output'
    $definitions = [IO.File]::ReadAllText((Join-Path $directory 'rabbitmq-definitions.json')) | ConvertFrom-Json
    $permissions = @($definitions.permissions)
    Assert-Child ($permissions.Count -eq 1 -and $permissions[0].configure -ceq '^$') 'Child gained broker configuration rights'
    Assert-Child ('iguana.integration.inbound' -cmatch $permissions[0].write) 'Inbound publish permission missing'
    Assert-Child ('iguana.integration.outbound' -cnotmatch $permissions[0].write) 'Child can publish backend messages'
    $queue = 'iguana.integration.outbound.feedback-prompt.' + $transport + '.channel.' + $channel + '.bot'
    Assert-Child ($queue -cmatch $permissions[0].read -and ($queue + '.foreign') -cnotmatch $permissions[0].read) 'Queue permission is not exact'
    Assert-Child (@($definitions.users[0].tags).Count -eq 0) 'Child gained a management or impersonator tag'
    [byte[]]$hashBytes = [Convert]::FromBase64String($definitions.users[0].password_hash)
    $salt = [byte[]]$hashBytes[0..3]
    $password = Get-ChildTransportKey $fakeMaster '' 'rabbit' $transport $channel
    Assert-Child ((Get-ChildRabbitPasswordHash $password $salt) -ceq $definitions.users[0].password_hash) 'Rabbit password hash does not match runtime credential'
    Expect-ChildFailure { & $generator -ChannelId $channel -Platform $transport -OutputDirectory $directory -MasterToken $fakeSecure -SignatureSecret $emptySecure }
    $cases.Add($transport + ': private ACL, credential domains, JSON, hashes, exact queue permission, no overwrite')
}
foreach ($identity in @('0', '01', '-1', ' 22', '9223372036854775808')) {
    $directory = Join-Path $testOutput ([guid]::NewGuid().ToString('N'))
    Expect-ChildFailure { & $generator -ChannelId $identity -Platform max -OutputDirectory $directory -MasterToken $fakeSecure -SignatureSecret $emptySecure }
    Assert-Child (-not (Test-Path -LiteralPath $directory)) 'Invalid identity created output'
    $cases.Add('Reject malformed BIGINT ' + $identity)
}
foreach ($transport in @('MAX', 'max ', 'other')) {
    $directory = Join-Path $testOutput ([guid]::NewGuid().ToString('N'))
    Expect-ChildFailure { & $generator -ChannelId 22 -Platform $transport -OutputDirectory $directory -MasterToken $fakeSecure -SignatureSecret $emptySecure }
    Assert-Child (-not (Test-Path -LiteralPath $directory)) 'Invalid platform created output'
    $cases.Add('Reject noncanonical platform')
}
$weak = ConvertTo-SecureString 'short' -AsPlainText -Force
$directory = Join-Path $testOutput 'weak'
Expect-ChildFailure { & $generator -ChannelId 22 -Platform max -OutputDirectory $directory -MasterToken $weak -SignatureSecret $emptySecure }
Assert-Child (-not (Test-Path -LiteralPath $directory)) 'Weak secret created output'
$cases.Add('Reject weak control-plane key')
$tokens = $null; $parseErrors = $null
$null = [Management.Automation.Language.Parser]::ParseFile($generator, [ref]$tokens, [ref]$parseErrors)
Assert-Child (@($parseErrors).Count -eq 0) 'Windows PowerShell parser rejected generator'
$cases.Add('Windows PowerShell parser')
[pscustomobject]@{ Status = 'GREEN'; Cases = $cases.Count; DatabaseChanged = $false; BrokerChanged = $false; Output = $testOutput } | ConvertTo-Json
