param(
    [ValidateRange(1, 65535)]
    [int]$StartPort = 8080,

    [ValidateRange(1, 65535)]
    [int]$MaxPort = 8999,

    [switch]$Exact,

    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

if ($MaxPort -lt $StartPort) {
    $MaxPort = $StartPort
}

try {
    $listeners = [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
}
catch {
    Write-Error "Unable to enumerate active TCP listeners: $($_.Exception.Message)"
    exit 20
}

$usedPorts = New-Object 'System.Collections.Generic.HashSet[int]'
foreach ($endpoint in $listeners) {
    [void]$usedPorts.Add([int]$endpoint.Port)
}

if ($Exact) {
    if ($usedPorts.Contains($StartPort)) {
        exit 10
    }
    exit 0
}

$selected = $null
for ($port = $StartPort; $port -le $MaxPort; $port++) {
    if (-not $usedPorts.Contains($port)) {
        $selected = $port
        break
    }
}

if ($null -eq $selected) {
    Write-Error "No free TCP port found in range $StartPort..$MaxPort."
    exit 30
}

if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    Write-Output $selected
} else {
    $parent = Split-Path -Parent $OutputPath
    if ($parent -and -not (Test-Path -LiteralPath $parent)) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }
    [System.IO.File]::WriteAllText(
        $OutputPath,
        [string]$selected,
        [System.Text.Encoding]::ASCII
    )
}

exit 0
