param([string]$BuildToolsPath = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactDirectory = Join-Path $projectRoot 'artifacts'
$debugApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$releaseApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$signer = Join-Path $BuildToolsPath 'apksigner.bat'
$aligner = Join-Path $BuildToolsPath 'zipalign.exe'
$aapt = Join-Path $BuildToolsPath 'aapt.exe'
foreach ($required in @($debugApk, $releaseApk, $signer, $aligner, $aapt)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required build output/tool missing: $required" }
}
$debugSignature = & $signer verify --verbose $debugApk 2>&1
$debugSignatureExit = $LASTEXITCODE
# An unsigned release is an intentional delivery artifact; verification fails for it.
$ErrorActionPreference = 'Continue'
$releaseSignature = & $signer verify --verbose $releaseApk 2>&1
$releaseSignatureExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
@('Debug APK:', $debugSignature, 'Release APK:', $releaseSignature) | Out-String | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-signature.txt') -Encoding utf8
$debugAlignment = & $aligner -c -P 16 -v 4 $debugApk 2>&1
$debugAlignmentExit = $LASTEXITCODE
$releaseAlignment = & $aligner -c -P 16 -v 4 $releaseApk 2>&1
$releaseAlignmentExit = $LASTEXITCODE
@('Debug APK:', $debugAlignment, 'Release APK:', $releaseAlignment) | Out-String -Width 240 | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-alignment.txt') -Encoding utf8
$metadata = & $aapt dump badging $releaseApk 2>&1
$metadataExit = $LASTEXITCODE
$metadata | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-metadata.txt') -Encoding utf8
$config = Join-Path $projectRoot 'app\build\generated\source\buildConfig\release\com\dishnest\app\BuildConfig.java'
$billingDisabled = if (Test-Path -LiteralPath $config) { (Get-Content -LiteralPath $config -Raw) -match 'BILLING_ENABLED\s*=\s*false' } else { $null }
$record = [ordered]@{
    verifiedAt = [DateTime]::UtcNow.ToString('o')
    debug = @{ signatureVerified = $debugSignatureExit -eq 0; zipAlignment16k = $debugAlignmentExit -eq 0; sha256 = (Get-FileHash -LiteralPath $debugApk -Algorithm SHA256).Hash.ToLowerInvariant() }
    release = @{ signed = $releaseSignatureExit -eq 0; zipAlignment16k = $releaseAlignmentExit -eq 0; metadataReadable = $metadataExit -eq 0; billingDisabled = $billingDisabled; sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant(); productionReady = $false }
    deviceInstallation = 'Not checked by this static validator; see artifacts/fire-device-qa.json'
    published = $false
}
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-validation.json') -Encoding utf8
$record | ConvertTo-Json -Depth 5
if ($debugSignatureExit -ne 0 -or $debugAlignmentExit -ne 0 -or $releaseAlignmentExit -ne 0 -or $metadataExit -ne 0) { throw 'APK validation failed; inspect artifacts/apk-*.txt.' }
