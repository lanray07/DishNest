param([string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactDirectory = Join-Path $projectRoot 'artifacts'
if (-not (Test-Path -LiteralPath $AdbPath)) { throw 'Android platform-tools adb was not found.' }
$inventory = & $AdbPath devices -l
if ($LASTEXITCODE -ne 0) { throw 'adb device inventory failed.' }
$inventory | Set-Content -LiteralPath (Join-Path $artifactDirectory 'device-inventory.txt') -Encoding utf8
$devices = @($inventory | Where-Object { $_ -match '^\S+\s+device(?:\s|$)' })
$record = [ordered]@{
    collectedAt = [DateTime]::UtcNow.ToString('o')
    connectedDeviceCount = $devices.Count
    deviceTests = 'Not executed by this read-only inventory script'
    physicalFireQa = 'Pending explicit hardware testing and evidence'
    published = $false
}
$record | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $artifactDirectory 'device-evidence.json') -Encoding utf8
$record | ConvertTo-Json
