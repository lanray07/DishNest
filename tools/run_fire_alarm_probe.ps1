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
$smokePath = Join-Path $projectRoot 'artifacts/fire-device-qa.json'
$smoke = Get-Content -LiteralPath $smokePath -Raw | ConvertFrom-Json
$hash = (Get-FileHash -LiteralPath $qaApk -Algorithm SHA256).Hash.ToLowerInvariant()
if ($smoke.packageId -ne 'com.dishnest.app.qa' -or $smoke.apkSha256 -ne $hash) {
    throw 'Run the isolated QA smoke suite on this exact APK first; UI may be blocked, but the installed package/hash must match.'
}
$devices = & $AdbPath devices
if (-not ($devices | Where-Object { $_ -match "^$([Regex]::Escape($Serial))\s+device\s*$" })) { throw 'Selected device unavailable.' }
$runner = 'com.dishnest.app.qa.test/androidx.test.runner.AndroidJUnitRunner'
$testClass = 'com.dishnest.app.FireBackgroundAlarmTest'
$seed = & $AdbPath -s $Serial shell am instrument -w -r -e class $testClass -e phase seed $runner 2>&1 | Out-String
$seed.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot 'artifacts/fire-background-seed.txt') -Encoding utf8
if ($seed -notmatch 'OK \(1 test\)') { throw 'Timer seed failed.' }
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
$verify.TrimEnd() | Set-Content -LiteralPath (Join-Path $projectRoot 'artifacts/fire-background-verify.txt') -Encoding utf8
$passed = $processAbsent -and $verify -match 'OK \(1 test\)' -and $verify -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED'
$record = [ordered]@{
    testedAt = [DateTime]::UtcNow.ToString('o'); packageId = 'com.dishnest.app.qa'; apkSha256 = $hash
    processAbsentAfterKill = $processAbsent; requestedTimerSeconds = 15; verificationDelaySeconds = 35
    passed = $passed; observed = if ($passed) { 'Persisted handled timer and own notification verified in a new instrumentation process' } else { 'Probe failed; inspect the saved phase results' }
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
