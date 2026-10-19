param([string]$BuildToolsPath = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$artifactDirectory = Join-Path $projectRoot 'artifacts'
$debugApk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$qaApk = Join-Path $projectRoot 'app\build\outputs\apk\qa\app-qa.apk'
$releaseApk = Join-Path $projectRoot 'app\build\outputs\apk\release\app-release-unsigned.apk'
$signer = Join-Path $BuildToolsPath 'apksigner.bat'
$aligner = Join-Path $BuildToolsPath 'zipalign.exe'
$aapt = Join-Path $BuildToolsPath 'aapt.exe'
foreach ($required in @($debugApk, $qaApk, $releaseApk, $signer, $aligner, $aapt)) {
    if (-not (Test-Path -LiteralPath $required)) { throw "Required build output/tool missing: $required" }
}
$qaSignature = & $signer verify --verbose $qaApk 2>&1
$qaSignatureExit = $LASTEXITCODE
$qaAlignment = & $aligner -c -P 16 -v 4 $qaApk 2>&1
$qaAlignmentExit = $LASTEXITCODE
$qaMetadata = & $aapt dump badging $qaApk 2>&1
$qaIdentity = $LASTEXITCODE -eq 0 -and $qaMetadata[0] -match "name='com\.dishnest\.app\.qa'"
$debugSignature = & $signer verify --verbose $debugApk 2>&1
$debugSignatureExit = $LASTEXITCODE
# An unsigned release is an intentional delivery artifact; verification fails for it.
$ErrorActionPreference = 'Continue'
$releaseSignature = & $signer verify --verbose $releaseApk 2>&1
$releaseSignatureExit = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
@('QA APK:', $qaSignature, 'Debug APK:', $debugSignature, 'Release APK:', $releaseSignature) | Out-String | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-signature.txt') -Encoding utf8
$debugAlignment = & $aligner -c -P 16 -v 4 $debugApk 2>&1
$debugAlignmentExit = $LASTEXITCODE
$releaseAlignment = & $aligner -c -P 16 -v 4 $releaseApk 2>&1
$releaseAlignmentExit = $LASTEXITCODE
@('QA APK:', $qaAlignment, 'Debug APK:', $debugAlignment, 'Release APK:', $releaseAlignment) | Out-String -Width 240 | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-alignment.txt') -Encoding utf8
$metadata = & $aapt dump badging $releaseApk 2>&1
$metadataExit = $LASTEXITCODE
$releaseManifest = & $aapt dump xmltree $releaseApk AndroidManifest.xml 2>&1 | Out-String
$qaFixtureAbsent = $LASTEXITCODE -eq 0 -and $releaseManifest -notmatch 'QaConfigurationActivity|PurchaseStatusActivity'
$metadata | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-metadata.txt') -Encoding utf8
$config = Join-Path $projectRoot 'app\build\generated\source\buildConfig\release\com\dishnest\app\BuildConfig.java'
$qaConfig = Join-Path $projectRoot 'app\build\generated\source\buildConfig\qa\com\dishnest\app\BuildConfig.java'
$qaBillingDisabled = (Test-Path -LiteralPath $qaConfig) -and (Get-Content -LiteralPath $qaConfig -Raw) -match 'BILLING_ENABLED\s*=\s*false'
$billingDisabled = if (Test-Path -LiteralPath $config) { (Get-Content -LiteralPath $config -Raw) -match 'BILLING_ENABLED\s*=\s*false' } else { $null }
$record = [ordered]@{
    verifiedAt = [DateTime]::UtcNow.ToString('o')
    debug = @{ signatureVerified = $debugSignatureExit -eq 0; zipAlignment16k = $debugAlignmentExit -eq 0; sha256 = (Get-FileHash -LiteralPath $debugApk -Algorithm SHA256).Hash.ToLowerInvariant() }
    qa = @{ signatureVerified = $qaSignatureExit -eq 0; zipAlignment16k = $qaAlignmentExit -eq 0; isolatedPackageVerified = $qaIdentity; billingDisabled = $qaBillingDisabled; sha256 = (Get-FileHash -LiteralPath $qaApk -Algorithm SHA256).Hash.ToLowerInvariant() }
    release = @{ signed = $releaseSignatureExit -eq 0; zipAlignment16k = $releaseAlignmentExit -eq 0; metadataReadable = $metadataExit -eq 0; billingDisabled = $billingDisabled; sha256 = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant(); qaFixtureAbsent = $qaFixtureAbsent; productionReady = $false }
    deviceInstallation = 'Not checked by this static validator; see artifacts/fire-device-qa.json'
    published = $false
}
$record | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $artifactDirectory 'apk-validation.json') -Encoding utf8
$record | ConvertTo-Json -Depth 5
if (-not $qaFixtureAbsent -or -not $qaBillingDisabled -or $qaSignatureExit -ne 0 -or $qaAlignmentExit -ne 0 -or -not $qaIdentity -or $debugSignatureExit -ne 0 -or $debugAlignmentExit -ne 0 -or $releaseAlignmentExit -ne 0 -or $metadataExit -ne 0) { throw 'APK validation failed; inspect artifacts/apk-*.txt.' }
