param(
    [string]$JavaHome = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot',
    [string]$BuildToolsPath = "$env:LOCALAPPDATA\Android\Sdk\build-tools\35.0.0"
)
$ErrorActionPreference = 'Stop'
$rootTask = Split-Path -Parent $PSScriptRoot
$privateTask = Join-Path $env:LOCALAPPDATA 'DishNest\publisher-private\.local-signing'
$keyTask = Join-Path $privateTask 'dishnest-release.jks'
$credentialTask = Join-Path $privateTask 'credentials.clixml'
$certificateTask = Join-Path $privateTask 'publisher.der'
$unsignedTask = Join-Path $rootTask 'app/build/outputs/apk/release/app-release-unsigned.apk'
$signedTask = Join-Path $rootTask 'app/build/outputs/apk/release/app-release-publisher.apk'
$keytoolTask = Join-Path $JavaHome 'bin/keytool.exe'
$signerTask = Join-Path $BuildToolsPath 'apksigner.bat'
$alignerTask = Join-Path $BuildToolsPath 'zipalign.exe'
foreach ($requiredTask in @($unsignedTask,$keytoolTask,$signerTask,$alignerTask)) {
    if (-not (Test-Path -LiteralPath $requiredTask)) { throw "Missing required input: $requiredTask" }
}
Push-Location $rootTask
try {
    & git -c "safe.directory=$($rootTask.Replace('\','/'))" check-ignore -q '.local-signing/credentials.clixml'
    if ($LASTEXITCODE -ne 0) { throw 'Signing directory must be ignored before creating secrets.' }
    & python tools/build_evidence.py
    if ($LASTEXITCODE -ne 0) { throw 'Current verified unsigned build required.' }
} finally { Pop-Location }
$userSidTask = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if (-not (Test-Path -LiteralPath $privateTask)) {
    New-Item -ItemType Directory -Path $privateTask -Force | Out-Null
    & icacls.exe $privateTask /inheritance:r /grant:r "*$($userSidTask):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Cannot restrict signing directory permissions.' }
}
if (-not (Get-Acl -LiteralPath $privateTask).AreAccessRulesProtected) { throw 'Private key storage must support protected Windows ACLs (NTFS required).' }
$rulesTask = (Get-Acl -LiteralPath $privateTask).GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier])
if ($rulesTask.Count -ne 2 -or @($rulesTask | Where-Object { $_.IdentityReference.Value -notin @($userSidTask,'S-1-5-18') }).Count) { throw 'Private key ACL contains unexpected access.' }
$hasKeyTask = Test-Path -LiteralPath $keyTask
$hasCredentialsTask = Test-Path -LiteralPath $credentialTask
if ($hasKeyTask -ne $hasCredentialsTask) { throw 'Partial key setup exists. Recover it; never replace the publisher identity.' }
$previousStoreTask = $env:DISHNEST_STORE_PASSWORD
$previousKeyTask = $env:DISHNEST_KEY_PASSWORD
$secureTask = $null
try {
    if ($hasCredentialsTask) {
        $credentialObjectTask = Import-Clixml -LiteralPath $credentialTask
        if ($credentialObjectTask.UserName -ne 'dishnest-release') { throw 'Unexpected publisher alias.' }
        $plainTask = $credentialObjectTask.GetNetworkCredential().Password
    } else {
        $randomTask = New-Object byte[] 48
        $rngTask = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        try { $rngTask.GetBytes($randomTask) } finally { $rngTask.Dispose() }
        $plainTask = [Convert]::ToBase64String($randomTask)
        $secureTask = ConvertTo-SecureString $plainTask -AsPlainText -Force
        $credentialObjectTask = [System.Management.Automation.PSCredential]::new('dishnest-release',$secureTask)
        $credentialObjectTask | Export-Clixml -LiteralPath $credentialTask
    }
    $env:DISHNEST_STORE_PASSWORD = $plainTask
    $env:DISHNEST_KEY_PASSWORD = $plainTask
    if (-not $hasKeyTask) {
        & $keytoolTask -genkeypair -noprompt -keystore $keyTask -storetype JKS -alias dishnest-release -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=O. Bankole' -storepass:env DISHNEST_STORE_PASSWORD -keypass:env DISHNEST_KEY_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Publisher key creation failed. Preserve partial setup for recovery.' }
    }
    & $keytoolTask -exportcert -keystore $keyTask -alias dishnest-release -storepass:env DISHNEST_STORE_PASSWORD -file $certificateTask
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read publisher certificate.' }
    $certificateHashTask = (Get-FileHash -LiteralPath $certificateTask -Algorithm SHA256).Hash.ToLowerInvariant()
    $priorReportTask = Join-Path $rootTask 'artifacts/publisher-signing.json'
    if (Test-Path -LiteralPath $priorReportTask) {
        $priorTask = Get-Content -LiteralPath $priorReportTask -Raw | ConvertFrom-Json
        if ($priorTask.certificateSha256 -ne $certificateHashTask) { throw 'Publisher identity changed. Restore the original key; never replace it silently.' }
    }
    & $signerTask sign --ks $keyTask --ks-key-alias dishnest-release --ks-pass env:DISHNEST_STORE_PASSWORD --key-pass env:DISHNEST_KEY_PASSWORD --out $signedTask $unsignedTask
    if ($LASTEXITCODE -ne 0) { throw 'APK signing failed.' }
} finally {
    $env:DISHNEST_STORE_PASSWORD = $previousStoreTask
    $env:DISHNEST_KEY_PASSWORD = $previousKeyTask
    $plainTask = $null
    if ($secureTask) { $secureTask.Dispose() }
}
$signatureTask = & $signerTask verify --verbose --print-certs $signedTask 2>&1
if ($LASTEXITCODE -ne 0) { throw 'Publisher signature verification failed.' }
$certificateHashTask = (Get-FileHash -LiteralPath $certificateTask -Algorithm SHA256).Hash.ToLowerInvariant()
if (-not (($signatureTask | Out-String) -match "certificate SHA-256 digest: $certificateHashTask")) { throw 'APK certificate does not match permanent publisher key.' }
& $alignerTask -c -P 16 4 $signedTask
if ($LASTEXITCODE -ne 0) { throw 'Publisher APK alignment failed.' }
$signatureTask | Set-Content -LiteralPath (Join-Path $rootTask 'artifacts/publisher-signature.txt') -Encoding utf8
$publicCertificateTask = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($certificateTask)
# APK signing may add META-INF entries and a signing block, but must preserve every original payload byte.
Add-Type -AssemblyName System.IO.Compression.FileSystem
$inputZipTask = [System.IO.Compression.ZipFile]::OpenRead($unsignedTask)
$outputZipTask = [System.IO.Compression.ZipFile]::OpenRead($signedTask)
try {
    $shaTask = [System.Security.Cryptography.SHA256]::Create()
    foreach ($entryTask in $inputZipTask.Entries) {
        $matchTask = $outputZipTask.GetEntry($entryTask.FullName)
        if (-not $matchTask) { throw "Signed APK lost payload: $($entryTask.FullName)" }
        $leftTask=$entryTask.Open(); $rightTask=$matchTask.Open()
        try {
            if ([Convert]::ToBase64String($shaTask.ComputeHash($leftTask)) -ne [Convert]::ToBase64String($shaTask.ComputeHash($rightTask))) { throw 'Signed APK payload differs from verified build.' }
        } finally { $leftTask.Dispose(); $rightTask.Dispose() }
    }
    foreach ($entryTask in $outputZipTask.Entries) {
        if (-not $inputZipTask.GetEntry($entryTask.FullName) -and $entryTask.FullName -notmatch '^META-INF/[^/]+\.(SF|RSA|DSA|EC|MF)$') { throw 'Unexpected new APK payload.' }
    }
} finally { $inputZipTask.Dispose(); $outputZipTask.Dispose(); if ($shaTask) { $shaTask.Dispose() } }
$reportTask = [ordered]@{
    generatedAt=[DateTime]::UtcNow.ToString('o'); publisher='O. Bankole'; alias='dishnest-release'
    keyAlgorithm='RSA'; keySize=3072; certificateSha256=$certificateHashTask
    certificateSubject=$publicCertificateTask.Subject
    certificateNotBefore=$publicCertificateTask.NotBefore.ToUniversalTime().ToString('o')
    certificateNotAfter=$publicCertificateTask.NotAfter.ToUniversalTime().ToString('o')
    unsignedApk='app/build/outputs/apk/release/app-release-unsigned.apk'
    unsignedSha256=(Get-FileHash -LiteralPath $unsignedTask -Algorithm SHA256).Hash.ToLowerInvariant()
    signedApk='app/build/outputs/apk/release/app-release-publisher.apk'
    signedSha256=(Get-FileHash -LiteralPath $signedTask -Algorithm SHA256).Hash.ToLowerInvariant()
    scriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
    signatureVerified=$true; certificateMatched=$true; zipAlignment16k=$true; payloadUnchanged=$true
    exactSignedApkHardwareTest=$false; productionReady=$false; submitted=$false
    credentialProtection='Windows DPAPI for current user/machine; signing directory ACL limited to current user and SYSTEM'
    backupRequired='Back up keystore and portable credentials securely before release. DPAPI XML alone cannot recover credentials on another machine.'
}
$reportTask | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $rootTask 'artifacts/publisher-signing.json') -Encoding utf8
$reportTask | ConvertTo-Json -Depth 5
