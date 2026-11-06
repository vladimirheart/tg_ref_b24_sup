param(
    [string]$ChannelId, [string]$Platform, [string]$OutputDirectory,
    [string]$VirtualHost = '/',
    [string]$InboundExchange = 'iguana.integration.inbound',
    [string]$OutboundExchange = 'iguana.integration.outbound',
    [string]$OutboundDeadLetterExchange = 'iguana.integration.outbound.dlx',
    [Security.SecureString]$MasterToken, [Security.SecureString]$SignatureSecret,
    [switch]$SelfTest
)
$ErrorActionPreference = 'Stop'

function Get-ChildTransportKey {
    param([string]$Master, [string]$Signing, [string]$Domain, [string]$Transport, [string]$Channel)
    $parsed = 0L
    if ($Channel -cnotmatch '^[1-9][0-9]{0,18}$' -or -not [long]::TryParse($Channel, [ref]$parsed)) { throw 'Positive canonical BIGINT channel required' }
    if ($Transport -cnotin @('telegram', 'vk', 'max') -or $Domain -cnotin @('api', 'sql', 'rabbit')) { throw 'Invalid credential domain or platform' }
    $key = if ([string]::IsNullOrWhiteSpace($Signing)) { $Master } else { $Signing.Trim() }
    if ([string]::IsNullOrWhiteSpace($Master) -or $key.Length -lt 32) { throw 'Configured control-plane secret of at least 32 characters required' }
    $mac = New-Object Security.Cryptography.HMACSHA256
    try {
        $mac.Key = [Text.Encoding]::UTF8.GetBytes($key)
        $payload = @('iguana-child-v1', $Domain, $Transport, $Channel, $Master) -join "`n"
        return ([BitConverter]::ToString($mac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)))).Replace('-', '').ToLowerInvariant()
    } finally { $mac.Dispose() }
}

function Get-ChildRabbitPasswordHash {
    param([string]$Password, [byte[]]$Salt)
    if ($Password -cnotmatch '^[a-f0-9]{64}$' -or $Salt.Length -ne 4) { throw 'Invalid Rabbit password hash input' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        [byte[]]$salted = $Salt + [Text.Encoding]::UTF8.GetBytes($Password)
        [byte[]]$result = $Salt + $sha.ComputeHash($salted)
        return [Convert]::ToBase64String($result)
    } finally { $sha.Dispose() }
}

function New-ChildRabbitDefinitions {
    param([string]$Channel, [string]$Transport, [string]$PasswordHash, [string]$Vhost,
        [string]$Inbound, [string]$Outbound, [string]$DeadLetters)
    $parsed = 0L
    if ($Channel -cnotmatch '^[1-9][0-9]{0,18}$' -or -not [long]::TryParse($Channel, [ref]$parsed) -or $Transport -cnotin @('telegram', 'vk', 'max')) { throw 'Exact child identity required' }
    foreach ($name in @($Inbound, $Outbound, $DeadLetters)) { if ($name -cnotmatch '^[A-Za-z0-9_.:-]{1,200}$') { throw 'Invalid explicit exchange name' } }
    if ([string]::IsNullOrWhiteSpace($Vhost) -or $Vhost.Length -gt 128 -or $Vhost -match '[\x00-\x1f]') { throw 'Invalid virtual host' }
    if ([Convert]::FromBase64String($PasswordHash).Length -ne 36) { throw 'Invalid salted SHA256 password hash' }
    $user = 'iguana_child_' + $Channel
    $queue = 'iguana.integration.outbound.feedback-prompt.' + $Transport + '.channel.' + $Channel + '.bot'
    $dlq = $queue + '.dlq'
    $routing = 'integration.outbound.feedback.prompt.' + $Transport + '.channel.' + $Channel
    return [ordered]@{
        users = @([ordered]@{ name = $user; password_hash = $PasswordHash; hashing_algorithm = 'rabbit_password_hashing_sha256'; tags = @() })
        permissions = @([ordered]@{ user = $user; vhost = $Vhost; configure = '^$'; write = '^' + [regex]::Escape($Inbound) + '$'; read = '^' + [regex]::Escape($queue) + '$' })
        queues = @(
            [ordered]@{ name = $queue; vhost = $Vhost; durable = $true; auto_delete = $false; arguments = @{ 'x-dead-letter-exchange' = $DeadLetters; 'x-dead-letter-routing-key' = $dlq } },
            [ordered]@{ name = $dlq; vhost = $Vhost; durable = $true; auto_delete = $false; arguments = @{} }
        )
        bindings = @(
            [ordered]@{ source = $Outbound; vhost = $Vhost; destination = $queue; destination_type = 'queue'; routing_key = $routing; arguments = @{} },
            [ordered]@{ source = $DeadLetters; vhost = $Vhost; destination = $dlq; destination_type = 'queue'; routing_key = $dlq; arguments = @{} }
        )
    }
}

function Convert-ChildSecretToText([Security.SecureString]$Secret) {
    if ($null -eq $Secret) { return '' }
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}

if ($MyInvocation.InvocationName -eq '.') { return }
if ($SelfTest) {
    $fake = 'r291-test-only-master-012345678901234567890'
    $sql = Get-ChildTransportKey $fake '' 'sql' 'max' '9007199254740993'
    if ($sql -cne 'eb64ca6d36f104aa2b361ce143613c50e93929e03fd3ad9af474612d80ce5cb3') { throw 'Independent credential vector mismatch' }
    $rabbit = Get-ChildTransportKey $fake '' 'rabbit' 'max' '9007199254740993'
    $hash = Get-ChildRabbitPasswordHash $rabbit ([byte[]]@(0, 1, 2, 3))
    if ($hash -cne 'AAECA9ZtTBF29E/VkziGPBvVHf+58tK1Rx7BM4d1VRQ5GnxJ') { throw 'Rabbit hash vector mismatch' }
    $definitions = New-ChildRabbitDefinitions '9007199254740993' 'max' $hash '/' $InboundExchange $OutboundExchange $OutboundDeadLetterExchange
    $null = ($definitions | ConvertTo-Json -Depth 12) | ConvertFrom-Json
    Write-Output 'GREEN: independent HMAC/hash vectors and definitions JSON; no files, database or broker changed'
    return
}

if ([Environment]::OSVersion.Platform -ne 'Win32NT') { throw 'This preparation command requires Windows private directory ACLs' }
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$privateRoot = [IO.Path]::GetFullPath((Join-Path $repository '.tmp')) + [IO.Path]::DirectorySeparatorChar
if (-not $OutputDirectory) { $OutputDirectory = Join-Path $privateRoot ('child-principals-' + $ChannelId + '-' + [guid]::NewGuid().ToString('N')) }
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (-not $output.StartsWith($privateRoot, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)) { throw 'Use a new private directory inside repository/.tmp' }
$cursor = [IO.Path]::GetDirectoryName($output)
while ($cursor.Length -ge $privateRoot.TrimEnd([IO.Path]::DirectorySeparatorChar).Length) {
    if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Private output cannot traverse a junction or symbolic link' }
    $cursor = [IO.Path]::GetDirectoryName($cursor)
}
if ($null -eq $MasterToken) { $MasterToken = Read-Host 'APP_INTERNAL_BOT_API_TOKEN (exact configured value)' -AsSecureString }
if ($null -eq $SignatureSecret) { $SignatureSecret = Read-Host 'APP_INTERNAL_BOT_API_SIGNATURE_SECRET (Enter when empty in configuration)' -AsSecureString }
$master = Convert-ChildSecretToText $MasterToken
$signing = Convert-ChildSecretToText $SignatureSecret
try {
    $sqlPassword = Get-ChildTransportKey $master $signing 'sql' $Platform $ChannelId
    $rabbitPassword = Get-ChildTransportKey $master $signing 'rabbit' $Platform $ChannelId
    [byte[]]$salt = New-Object byte[] 4
    $random = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $random.GetBytes($salt) } finally { $random.Dispose() }
    $hash = Get-ChildRabbitPasswordHash $rabbitPassword $salt
    $definitions = New-ChildRabbitDefinitions $ChannelId $Platform $hash $VirtualHost $InboundExchange $OutboundExchange $OutboundDeadLetterExchange
    $sourceSql = [IO.File]::ReadAllText((Join-Path $PSScriptRoot 'sql/provision-child-transport.sql'))
    $sqlText = "\set channel_id '$ChannelId'`n\set platform '$Platform'`n\set password '$sqlPassword'`n" + $sourceSql
    $null = [IO.Directory]::CreateDirectory($output)
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $output -AclObject $acl
    $utf8 = New-Object Text.UTF8Encoding($false)
    [IO.File]::WriteAllText((Join-Path $output 'provision.sql'), $sqlText, $utf8)
    [IO.File]::WriteAllText((Join-Path $output 'rabbitmq-definitions.json'), ($definitions | ConvertTo-Json -Depth 12), $utf8)
    $manifest = [ordered]@{ ChannelId = $ChannelId; Platform = $Platform; Principal = 'iguana_child_' + $ChannelId; Directory = $output; Private = $true; Applied = $false; Files = @() }
    foreach ($name in @('provision.sql', 'rabbitmq-definitions.json')) {
        $manifest.Files += @{ Name = $name; SHA256 = (Get-FileHash -LiteralPath (Join-Path $output $name) -Algorithm SHA256).Hash.ToLowerInvariant() }
    }
    [IO.File]::WriteAllText((Join-Path $output 'manifest.json'), ($manifest | ConvertTo-Json -Depth 8), $utf8)
    $manifest | ConvertTo-Json -Depth 8
} finally { $master = $null; $signing = $null; $sqlPassword = $null; $rabbitPassword = $null; $sqlText = $null }
