param([string]$JavaHome = 'C:\Program Files\Microsoft\jdk-17.0.20.101-hotspot')
$ErrorActionPreference = 'Stop'
$rootTask = Split-Path -Parent $PSScriptRoot
$privateTask = Join-Path $env:LOCALAPPDATA 'DishNest/publisher-private'
$latTask = Join-Path $privateTask '.local-billing/cloudflare'
$signingTask = Join-Path $privateTask '.local-signing'
$sdkTask = Join-Path $env:LOCALAPPDATA 'Android/Sdk/build-tools/35.0.0'
$outputTask = Join-Path $rootTask 'build/lat-candidate'
$sidTask = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
if (-not (Get-Acl -LiteralPath $privateTask).AreAccessRulesProtected) { throw 'Protected NTFS publisher storage required' }
$rulesTask = (Get-Acl -LiteralPath $latTask).GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier])
if (@($rulesTask | Where-Object { $_.IdentityReference.Value -notin @($sidTask,'S-1-5-18') }).Count) { throw 'Unexpected LAT key access' }
foreach ($requiredTask in @('dishnest-release.jks','credentials.clixml')) {
    if (-not (Test-Path -LiteralPath (Join-Path $signingTask $requiredTask))) { throw 'Existing publisher signing identity required; never generate a replacement' }
}
New-Item -ItemType Directory -Path $outputTask -Force | Out-Null
$previousJavaTask = $env:JAVA_HOME
$previousStoreTask = $env:DISHNEST_STORE_PASSWORD
$previousKeyTask = $env:DISHNEST_KEY_PASSWORD
Push-Location $rootTask
try {
    $env:JAVA_HOME = $JavaHome
    # Private material stays on protected NTFS. Only the public key reaches Gradle.
    @'
import {generateKeyPairSync,createPrivateKey,createPublicKey} from 'node:crypto';
import {readFileSync,writeFileSync,existsSync} from 'node:fs';
import {join} from 'node:path';
const folder=join(process.env.LOCALAPPDATA,'DishNest/publisher-private/.local-billing/cloudflare');
const keyPath=join(folder,'lat.signing.pem');
if(!existsSync(keyPath)) {
  const {privateKey}=generateKeyPairSync('ec',{namedCurve:'prime256v1'});
  writeFileSync(keyPath,privateKey.export({type:'pkcs8',format:'pem'}),{flag:'wx'});
}
const key=createPrivateKey(readFileSync(keyPath));
if(key.asymmetricKeyType!=='ec'||key.asymmetricKeyDetails?.namedCurve!=='prime256v1')throw new Error('Unexpected LAT key');
const publicKey=createPublicKey(key).export({type:'spki',format:'der'}).toString('base64');
const deployed=JSON.parse(readFileSync('artifacts/billing-deployment.json','utf8'));
if(deployed.checks.some(profile=>profile.public_key===publicKey))throw new Error('LAT key must be separate from existing profiles');
writeFileSync(join(folder,'lat.public-key.txt'),publicKey);
'@ | node --input-type=module -
    if ($LASTEXITCODE -ne 0) { throw 'LAT key preparation failed' }
    $publicTask = (Get-Content -LiteralPath (Join-Path $latTask 'lat.public-key.txt') -Raw).Trim()
    # Direct every project output to an isolated tree. Normal verified APKs/logs stay intact.
    $initTask = Join-Path $outputTask 'isolated.gradle'
    @'
allprojects {
    layout.buildDirectory.set(new File(rootProject.projectDir, "build/lat-candidate/" + (project.path == ':' ? 'root' : project.path.substring(1).replace(':','/'))))
}
'@ | Set-Content -LiteralPath $initTask -Encoding utf8
    $logTask = Join-Path $outputTask 'build.log'
    & ./gradlew.bat -I $initTask :app:assembleRelease '-Pdishnest.billing.enabled=true' '-Pdishnest.billing.environment=lat' '-Pdishnest.billing.url=https://dishnest-receipt-verifier-lat.lanraybanks.workers.dev/v1/verify' "-Pdishnest.billing.publicKey=$publicTask" --console=plain *> $logTask
    if ($LASTEXITCODE -ne 0) { throw "LAT candidate build failed; inspect $logTask" }
    $unsignedTask = Join-Path $outputTask 'app/outputs/apk/release/app-release-unsigned.apk'
    $signedTask = Join-Path $outputTask 'DishNest-0.1.0-LAT.apk'
    $credentialTask = Import-Clixml -LiteralPath (Join-Path $signingTask 'credentials.clixml')
    if ($credentialTask.UserName -ne 'dishnest-release') { throw 'Unexpected publisher alias' }
    $env:DISHNEST_STORE_PASSWORD = $credentialTask.GetNetworkCredential().Password
    $env:DISHNEST_KEY_PASSWORD = $env:DISHNEST_STORE_PASSWORD
    & (Join-Path $sdkTask 'apksigner.bat') sign --ks (Join-Path $signingTask 'dishnest-release.jks') --ks-key-alias dishnest-release --ks-pass env:DISHNEST_STORE_PASSWORD --key-pass env:DISHNEST_KEY_PASSWORD --out $signedTask $unsignedTask
    if ($LASTEXITCODE -ne 0) { throw 'LAT signing failed' }
    & (Join-Path $sdkTask 'apksigner.bat') verify --print-certs $signedTask *> (Join-Path $outputTask 'signature.txt')
    if ($LASTEXITCODE -ne 0) { throw 'LAT signature verification failed' }
    & (Join-Path $sdkTask 'zipalign.exe') -c -P 16 4 $signedTask *> (Join-Path $outputTask 'alignment.txt')
    if ($LASTEXITCODE -ne 0) { throw 'LAT alignment failed' }
    @'
import hashlib,json,os,re,subprocess,sys,zipfile
from pathlib import Path
sys.path.insert(0,'tools')
from build_evidence import input_hashes,verify,digest
root=Path.cwd();folder=root/'build/lat-candidate';apk=folder/'DishNest-0.1.0-LAT.apk'
assert verify(),'Normal verified build was changed'
sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/build-tools/35.0.0'
metadata=subprocess.check_output([str(sdk/'aapt.exe'),'dump','badging',str(apk)],text=True)
assert "name='com.dishnest.app'" in metadata and 'application-debuggable' not in metadata
cert=re.search(r'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)',(folder/'signature.txt').read_text()).group(1)
assert cert==json.loads((root/'artifacts/publisher-signing.json').read_text(encoding='utf-8-sig'))['certificateSha256']
classes=folder/'app/tmp/kotlin-classes/release/com/dishnest/app'
assert not (classes/'PurchaseStatusActivity.class').exists()
buildconfig=folder/'app/generated/source/buildConfig/release/com/dishnest/app/BuildConfig.java'
config=buildconfig.read_text();assert 'BILLING_ENABLED = true' in config and 'BILLING_ENVIRONMENT = "lat"' in config
public=(Path(os.environ['LOCALAPPDATA'])/'DishNest/publisher-private/.local-billing/cloudflare/lat.public-key.txt').read_text().strip()
assert public in config
deploymentPath=root/'artifacts/billing-lat-deployment.json'
deployment=json.loads(deploymentPath.read_text(encoding='utf-8')) if deploymentPath.exists() else {}
service=deployment.get('deployed') is True and deployment.get('public_key')==public
consolePath=root/'artifacts/amazon-lat-console.json'
console=json.loads(consolePath.read_text(encoding='utf-8')) if consolePath.exists() else {}
uploaded=console.get('appUploaded') is True and console.get('apkSha256')==digest(apk)
submitted=uploaded and console.get('status') in ['Test submitted','In progress','Live']
report=dict(apk='build/lat-candidate/DishNest-0.1.0-LAT.apk',apkSha256=digest(apk),
 certificateSha256=cert,sourceSha256=input_hashes(),environment='lat',billingEnabled=True,
 publicKey=public,endpoint='https://dishnest-receipt-verifier-lat.lanraybanks.workers.dev/v1/verify',
 debuggable=False,signatureVerified=True,zipAlignment16k=True,normalBuildUnchanged=True,
 deviceInstalled=False,uploaded=uploaded,submitted=submitted,serviceDeployed=service,productionReady=False,
 remaining=([] if service else ['Activate separate LAT verifier on Workers Free'])+
 ([] if submitted else ['Complete/submit the certified LAT draft with the current APK'])+
 ['Amazon LAT availability and separate-device purchase lifecycle'])
(root/'artifacts/lat-candidate.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
print(json.dumps({key:report[key] for key in ['apk','signatureVerified','normalBuildUnchanged','submitted','productionReady']}))
'@ | python -
    if ($LASTEXITCODE -ne 0) { throw 'LAT candidate inspection failed' }
} finally {
    $env:JAVA_HOME = $previousJavaTask
    $env:DISHNEST_STORE_PASSWORD = $previousStoreTask
    $env:DISHNEST_KEY_PASSWORD = $previousKeyTask
    Pop-Location
}
