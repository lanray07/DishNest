param(
    [Parameter(Mandatory=$true)][string]$Serial,
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
$seed = & $AdbPath -s $Serial shell am instrument -w -r -e class $testClass -e phase seed $runner 2>&1 | Out-String
$seedExit = $LASTEXITCODE
$seed.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot 'artifacts/fire-background-seed.txt') -Encoding utf8
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
$verify = & $AdbPath -s $Serial shell am instrument -w -r -e class $testClass -e phase verify $runner 2>&1 | Out-String
$verifyExit = $LASTEXITCODE
$verify.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot 'artifacts/fire-background-verify.txt') -Encoding utf8
$passed = $verifyExit -eq 0 -and $processAbsent -and $verify -match 'OK \(1 test\)' -and $verify -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed'
$record = [ordered]@{
    testedAt = [DateTime]::UtcNow.ToString('o'); packageId = 'com.dishnest.app.qa'; apkSha256 = $hash
    instrumentationApkSha256 = $testHash; installedApksVerified = $true
    processAbsentAfterKill = $processAbsent; requestedTimerSeconds = 15; verificationDelaySeconds = 35
    passed = $passed; observed = if ($passed) { 'Recipe with notes/Unicode/favourite, named plan, meal, pantry, checked shopping item, template, handled timer and own notification verified in a new instrumentation process' } else { 'Probe failed; inspect the saved phase results' }
    kitchenPersistencePassed = $passed
    notCovered = @('Force-stop recovery', 'Doze', 'Reboot', 'Wall-clock changes', 'Exact delivery latency')
    ordinaryAppModified = $false; serialIdentifierStored = $false; published = $false
}
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $projectRoot 'artifacts/fire-background-alarm.json') -Encoding utf8
$record | ConvertTo-Json -Depth 5
if (-not $passed) { throw 'Background timer probe did not pass; inspect saved phase results.' }

} finally {
    $qaMutex.ReleaseMutex()
    $qaMutex.Dispose()
}
