param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [switch]$ReplaceTestInstall,
    [string]$AdbPath = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
)
$ErrorActionPreference = 'Stop'
# The UI reset and background alarm fixture share one disposable app database.
$qaMutex = [System.Threading.Mutex]::new($false, 'Local\DishNestFireQa')
try { $qaLock = $qaMutex.WaitOne(0) }
catch [System.Threading.AbandonedMutexException] { $qaLock = $true }
if (-not $qaLock) { $qaMutex.Dispose(); throw 'Another DishNest Fire QA run is active. Run the UI suite and timer probe sequentially.' }
try {
$projectRoot = Split-Path -Parent $PSScriptRoot
$debugApk = Join-Path $projectRoot 'app/build/outputs/apk/qa/app-qa.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
$artifacts = Join-Path $projectRoot 'artifacts'
foreach ($file in @($debugApk, $testApk, $AdbPath)) {
    if (-not (Test-Path -LiteralPath $file)) { throw "Required local QA file missing: $file" }
}
$devices = & $AdbPath devices
if (-not ($devices | Where-Object { $_ -match "^$([Regex]::Escape($Serial))\s+device\s*$" })) {
    throw 'The explicitly selected device is not connected and authorised.'
}
$aapt = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/35.0.0/aapt.exe'
$identity = & $aapt dump badging $debugApk
if ($LASTEXITCODE -ne 0 -or $identity[0] -notmatch "name='com\.dishnest\.app\.qa'") { throw 'Refusing to install a non-QA app.' }
& $AdbPath -s $Serial shell input keyevent KEYCODE_WAKEUP
$lockState = & $AdbPath -s $Serial shell dumpsys window policy | Out-String
if ($lockState -match 'isStatusBarKeyguard=true|mIsShowing=true') {
    throw 'The selected device has a lock screen showing. Unlock it before UI QA; this script never bypasses authentication.'
}
$existing = & $AdbPath -s $Serial shell pm list packages com.dishnest.app.qa
if ($existing -match '^package:com\.dishnest\.app\.qa$' -and -not $ReplaceTestInstall) {
    throw 'DishNest QA already exists. ReplaceTestInstall authorizes resetting only disposable QA fixtures.'
}
$model = (& $AdbPath -s $Serial shell getprop ro.product.model | Out-String).Trim()
$api = (& $AdbPath -s $Serial shell getprop ro.build.version.sdk | Out-String).Trim()
$android = (& $AdbPath -s $Serial shell getprop ro.build.version.release | Out-String).Trim()
$locale = (& $AdbPath -s $Serial shell settings get system system_locales | Out-String).Trim()
& $AdbPath -s $Serial install -r $debugApk
if ($LASTEXITCODE -ne 0) { throw 'Local debug APK installation failed.' }
& $AdbPath -s $Serial install -r $testApk
if ($LASTEXITCODE -ne 0) { throw 'Local test APK installation failed.' }
& $AdbPath -s $Serial shell pm revoke com.dishnest.app.qa android.permission.RECORD_AUDIO
$output = & $AdbPath -s $Serial shell am instrument -w -r -e notClass com.dishnest.app.FireBackgroundAlarmTest com.dishnest.app.qa.test/androidx.test.runner.AndroidJUnitRunner 2>&1 | Out-String
$commandExit = $LASTEXITCODE
$output.TrimEnd() | Set-Content -LiteralPath (Join-Path $artifacts 'device-instrumentation.txt') -Encoding utf8
$match = [Regex]::Match($output, 'OK \((\d+) tests?\)')
$totalMatch = [Regex]::Match($output, 'INSTRUMENTATION_STATUS: numtests=(\d+)')
$passedTests = [Regex]::Matches($output, '(?m)^INSTRUMENTATION_STATUS_CODE: 0\r?$').Count
$failedTests = [Regex]::Matches($output, '(?m)^INSTRUMENTATION_STATUS_CODE: -[12]\r?$').Count
$passed = $commandExit -eq 0 -and $passedTests -ge 10 -and $failedTests -eq 0 -and $match.Success -and [int]$match.Groups[1].Value -ge 10 -and $output -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
$record = [ordered]@{
    packageId = 'com.dishnest.app.qa'; ordinaryAppModified = $false
    testedAt = [DateTime]::UtcNow.ToString('o')
    model = $model; androidVersion = $android; api = $api; locale = $locale
    apkSha256 = (Get-FileHash -LiteralPath $debugApk -Algorithm SHA256).Hash.ToLowerInvariant()
    instrumentationApkSha256 = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash.ToLowerInvariant()
    tests = if ($totalMatch.Success) { [int]$totalMatch.Groups[1].Value } else { 0 }
    passedTests = $passedTests; failedTests = $failedTests
    passed = $passed
    uiSmokePassed = $passed
    coverage = @('Empty kitchen and real recipe create/edit/delete UI', 'SQLite close/reopen persistence', 'Native bitmap photo ZIP round-trip', 'Bundled offline OCR synthetic printed fixture', 'Arabic/Hindi resource formatting', 'Real SQLite v1-to-v2 migration', 'Named plan creation/selection across activity recreation', 'Opt-in generic pantry reminder and duplicate suppression', 'Denied microphone touch fallback', 'Production Arabic planner/native dialog controls under QA activity font scale 1.5 and RTL')
    notCovered = @('App Tester/LAT purchases', 'Granted microphone or spoken commands', 'TTS/audio focus', 'Doze/reboot; process-termination timer delivery is recorded separately', 'TalkBack and full system font matrix', 'Actual camera or real-photo OCR accuracy', 'Publisher-signed release')
    backgroundAlarmEvidence = 'artifacts/fire-background-alarm.json'
    serialIdentifierStored = $false
    storeSubmission = $false; remoteDeployment = $false
}
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $artifacts 'fire-device-qa.json') -Encoding utf8
$record | ConvertTo-Json -Depth 5
if (-not $passed) { throw 'Device instrumentation failed. Inspect artifacts/device-instrumentation.txt.' }

} finally {
    $qaMutex.ReleaseMutex()
    $qaMutex.Dispose()
}
