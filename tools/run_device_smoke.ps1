param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [switch]$ReplaceTestInstall,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$debugApk = Join-Path $projectRoot 'app/build/outputs/apk/debug/app-debug.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
$artifacts = Join-Path $projectRoot 'artifacts'
foreach ($file in @($debugApk, $testApk, $AdbPath)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required local QA file missing: $file" }
}
$devices = & $AdbPath devices
if (-not ($devices | Where-Object { $_ -match "^$([Regex]::Escape($Serial))\s+device\s*$" })) {
    throw 'The explicitly selected device is not connected and authorised.'
}
$existing = & $AdbPath -s $Serial shell pm list packages com.dishnest.app
if ($existing -match '^package:com\.dishnest\.app$' -and -not $ReplaceTestInstall) {
    throw 'DishNest already exists. This script never clears app data. Use ReplaceTestInstall only for a known local QA installation.'
}
$model = (& $AdbPath -s $Serial shell getprop ro.product.model | Out-String).Trim()
$api = (& $AdbPath -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
$android = (& $AdbPath -s $Serial shell getprop ro.build.version.release | Out-String).Trim()
$locale = (& $AdbPath -s $Serial shell settings get system system_locales | Out-String).Trim()
& $AdbPath -s $Serial install -r $debugApk
if ($LASTEXITCODE -ne 0) { throw 'Local debug APK installation failed.' }
& $AdbPath -s $Serial install -r $testApk
if ($LASTEXITCODE -ne 0) { throw 'Local test APK installation failed.' }
$output = & $AdbPath -s $Serial shell am instrument -w -r com.dishnest.app.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String
$commandExit = $LASTEXITCODE
$output.TrimEnd() | Set-Content -LiteralPath (Join-Path $artifacts 'device-instrumentation.txt') -Encoding utf8
$match = [Regex]::Match($output, 'OK \((\d+) tests?\)')
$totalMatch = [Regex]::Match($output, 'INSTRUMENTATION_STATUS: numtests=(\d+)')
$passedTests = [Regex]::Matches($output, '(?m)^INSTRUMENTATION_STATUS_CODE: 0\r?$').Count
$failedTests = [Regex]::Matches($output, '(?m)^INSTRUMENTATION_STATUS_CODE: -[12]\r?$').Count
$passed = $commandExit -eq 0 -and $match.Success -and [int]$match.Groups[1].Value -ge 5 -and $output -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
$record = [ordered]@{
    testedAt = [DateTime]::UtcNow.ToString('o')
    model = $model; androidVersion = $android; api = $api; locale = $locale
    apkSha256 = (Get-FileHash -LiteralPath $debugApk -Algorithm SHA256).Hash.ToLowerInvariant()
    tests = if ($totalMatch.Success) { [int]$totalMatch.Groups[1].Value } else { 0 }
    passedTests = $passedTests; failedTests = $failedTests
    passed = $passed
    uiSmokePassed = $passed
    coverage = @('Empty kitchen and real recipe create/edit/delete UI', 'SQLite close/reopen persistence', 'Native bitmap photo ZIP round-trip', 'Bundled offline OCR synthetic printed fixture', 'Arabic/Hindi resource formatting')
    notCovered = @('App Tester/LAT purchases', 'Microphone or spoken commands', 'TTS/audio focus', 'Doze/reboot/alarm delivery', 'TalkBack and large fonts', 'Actual camera or real-photo OCR accuracy', 'Publisher-signed release')
    serialIdentifierStored = $false
    storeSubmission = $false; remoteDeployment = $false
}
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $artifacts 'fire-device-qa.json') -Encoding utf8
$record | ConvertTo-Json -Depth 5
if (-not $passed) { throw 'Device instrumentation failed. Inspect artifacts/device-instrumentation.txt.' }
