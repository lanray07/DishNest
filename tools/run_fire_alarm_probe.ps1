param(
    [Parameter(Mandatory=$true)][string]$Serial,
    [switch]$NotificationsBlocked,
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
$qaApk = Join-Path $projectRoot 'app/build/outputs/apk/qa/app-qa.apk'
$testApk = Join-Path $projectRoot 'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
$smokePath = Join-Path $projectRoot 'artifacts/fire-device-qa.json'
$smoke = Get-Content -LiteralPath $smokePath -Raw | ConvertFrom-Json
$hash = (Get-FileHash -LiteralPath $qaApk -Algorithm SHA256).Hash.ToLowerInvariant()
$testHash = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($smoke.packageId -ne 'com.dishnest.app.qa' -or $smoke.apkSha256 -ne $hash -or $smoke.instrumentationApkSha256 -ne $testHash -or -not $smoke.passed) {
    throw 'Pass the isolated QA smoke suite on this exact app and instrumentation APK first.'
}
$devices = & $AdbPath devices
if (-not ($devices | Where-Object { $_ -match "^$([Regex]::Escape($Serial))\s+device\s*$" })) { throw 'Selected device unavailable.' }
# A local report alone cannot prove what is installed on the selected tablet.
foreach ($package in @(@('com.dishnest.app.qa', $hash), @('com.dishnest.app.qa.test', $testHash))) {
    $installedPath = (& $AdbPath -s $Serial shell pm path $package[0] | Out-String).Trim()
    if ($installedPath -notmatch '^package:(/data/app/[^\r\n]+/base\.apk)$') { throw 'Expected a single installed QA APK.' }
    $installedHash = (& $AdbPath -s $Serial shell sha256sum $Matches[1] | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or ($installedHash -split '\s+')[0] -ne $package[1]) { throw 'Installed QA app or runner differs from the verified local APK.' }
}
$runner = 'com.dishnest.app.qa.test/androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.dishnest.app.FireBackgroundAlarmTest'
$evidencePrefix = if ($NotificationsBlocked) { 'fire-blocked-notifications' } else { 'fire-background' }
$blockedArgument = if ($NotificationsBlocked) { 'true' } else { 'false' }
$previousNotificationEnabled = $null
$notificationHelper = Join-Path $PSScriptRoot 'fire_notification_setting.py'
$reportName = if ($NotificationsBlocked) { 'fire-blocked-notifications.json' } else { 'fire-background-alarm.json' }
$reportPath = Join-Path $projectRoot "artifacts/$reportName"
# A failed rerun must not leave an older pass looking like the latest attempt.
@{testedAt=[DateTime]::UtcNow.ToString('o'); packageId='com.dishnest.app.qa'; apkSha256=$hash; instrumentationApkSha256=$testHash; passed=$false; observed='Probe started; completion and restoration not yet verified'} | ConvertTo-Json | Set-Content -LiteralPath $reportPath -Encoding utf8
if ($NotificationsBlocked) {
    $setting = & python $notificationHelper --serial $Serial --adb $AdbPath --state read
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read the QA notification switch.' }
    $previousNotificationEnabled = [bool](($setting | ConvertFrom-Json).enabled)
}
try {
if ($NotificationsBlocked) {
    & python $notificationHelper --serial $Serial --adb $AdbPath --state disabled
    if ($LASTEXITCODE -ne 0) { throw 'Cannot block notifications for the disposable QA package.' }
}
$seed = & $AdbPath -s $Serial shell am instrument -w -r -e class $testClass -e phase seed -e notificationsBlocked $blockedArgument $runner 2>&1 | Out-String
$seedExit = $LASTEXITCODE
$seed.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot "artifacts/$evidencePrefix-seed.txt") -Encoding utf8
if ($seedExit -ne 0 -or $seed -notmatch 'OK \(1 test\)' -or $seed -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw 'Timer and kitchen seed failed.' }
# Ordinary am kill, never force-stop: Android intentionally suppresses alarms after force-stop.
& $AdbPath -s $Serial shell input keyevent KEYCODE_HOME
& $AdbPath -s $Serial shell am kill com.dishnest.app.qa
$killDeadline = [DateTime]::UtcNow.AddSeconds(3)
do {
    $remaining = (& $AdbPath -s $Serial shell pidof com.dishnest.app.qa | Out-String).Trim()
    if ($remaining) { Start-Sleep -Milliseconds 100 }
} while ($remaining -and [DateTime]::UtcNow -lt $killDeadline)
$processAbsent = [string]::IsNullOrEmpty($remaining)
Start-Sleep -Seconds 35
$verify = & $AdbPath -s $Serial shell am instrument -w -r -e class $testClass -e phase verify -e notificationsBlocked $blockedArgument $runner 2>&1 | Out-String
$verifyExit = $LASTEXITCODE
$verify.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot "artifacts/$evidencePrefix-verify.txt") -Encoding utf8
$passed = $verifyExit -eq 0 -and $processAbsent -and $verify -match 'OK \(1 test\)' -and $verify -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
$record = [ordered]@{
    testedAt = [DateTime]::UtcNow.ToString('o'); packageId = 'com.dishnest.app.qa'; apkSha256 = $hash
    instrumentationApkSha256 = $testHash; installedApksVerified = $true
    controllerSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    notificationHelperSha256 = (Get-FileHash -LiteralPath $notificationHelper -Algorithm SHA256).Hash.ToLowerInvariant()
    processAbsentAfterKill = $processAbsent; requestedTimerSeconds = 15; verificationDelaySeconds = 35
    passed = $passed; observed = if ($passed) { if ($NotificationsBlocked) { 'Saved kitchen and expired/handled timer survive process termination with notifications blocked; no timer notification posted' } else { 'Recipe with notes/Unicode/favourite, named plan, meal, pantry, checked shopping item, template, handled timer and own notification verified in a new instrumentation process' } } else { 'Probe failed; inspect the saved phase results' }
    notificationsBlocked = [bool]$NotificationsBlocked
    notificationSettingBefore = if ($NotificationsBlocked) { $previousNotificationEnabled } else { 'unchanged' }
    kitchenPersistencePassed = $passed
    notCovered = @('Force-stop recovery', 'Doze', 'Reboot', 'Wall-clock changes', 'Exact delivery latency', 'Android 13+ runtime notification permission')
    ordinaryAppModified = $false; serialIdentifierStored = $false; published = $false
}
} finally {
    if ($null -ne $previousNotificationEnabled) {
        $restoreState = if ($previousNotificationEnabled) { 'enabled' } else { 'disabled' }
        & python $notificationHelper --serial $Serial --adb $AdbPath --state $restoreState
        if ($LASTEXITCODE -ne 0) { throw 'Failed to restore the QA notification switch; restore it before further QA.' }
    }
}
$record.notificationSettingRestored = $true
$record.notificationSettingAfter = if ($NotificationsBlocked) { $previousNotificationEnabled } else { 'unchanged' }
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $reportPath -Encoding utf8
$record | ConvertTo-Json -Depth 5
if (-not $passed) { throw 'Background timer probe did not pass; inspect saved phase results.' }

} finally {
    $qaMutex.ReleaseMutex()
    $qaMutex.Dispose()
}
