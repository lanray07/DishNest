"""Validate the separate LAT candidate; never approves actual LAT purchases."""
import json, os, subprocess
from pathlib import Path
from build_evidence import ROOT, digest, input_hashes

def verify():
    path=ROOT/'artifacts/lat-candidate.json'
    if not path.is_file():return None
    candidate=json.loads(path.read_text(encoding='utf-8'))
    apk=ROOT/candidate['apk']
    if candidate['sourceSha256']!=input_hashes() or digest(apk)!=candidate['apkSha256']:
        raise ValueError('Stale LAT candidate')
    deployment=json.loads((ROOT/'artifacts/billing-lat-deployment.json').read_text(encoding='utf-8'))
    for field in ['deployed','deploymentAuthorized','freePlanVerified','separateSigningKey','editorBundleMatchedLocalSource']:
        if deployment.get(field) is not True:raise ValueError('Incomplete LAT deployment: '+field)
    if deployment['profile']!='lat' or deployment['public_key']!=candidate['publicKey'] or deployment['endpoint']!=candidate['endpoint']:
        raise ValueError('LAT profile/key mismatch')
    if deployment.get('paidPlanEnabled') or deployment.get('paidBindings') or deployment.get('logsEnabled') or deployment.get('previewUrlsEnabled'):
        raise ValueError('LAT deployment cost/privacy boundary changed')
    if not all(digest(ROOT/p)==value for p,value in deployment['sourceSha256'].items()):raise ValueError('Stale LAT deployment source')
    contract=json.loads((ROOT/'artifacts/billing-live-contract.json').read_text(encoding='utf-8'))
    if not contract.get('passed') or not any(c['profile']=='lat' and c['signed_inactive'] and c['wrong_account_rejected'] for c in contract['checks']):
        raise ValueError('LAT signed contract missing')
    if not all(digest(ROOT/p)==value for p,value in contract['sourceSha256'].items()):raise ValueError('Stale LAT live contract')
    sdk=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/build-tools/35.0.0'
    signed=subprocess.run([str(sdk/'apksigner.bat'),'verify','--print-certs',str(apk)],capture_output=True,text=True,check=True).stdout
    publisher=json.loads((ROOT/'artifacts/publisher-signing.json').read_text(encoding='utf-8-sig'))
    if 'Signer #1 certificate SHA-256 digest: '+publisher['certificateSha256'] not in signed:raise ValueError('LAT publisher signature mismatch')
    metadata=subprocess.run([str(sdk/'aapt.exe'),'dump','badging',str(apk)],capture_output=True,text=True,check=True).stdout
    if "name='com.dishnest.app'" not in metadata or 'application-debuggable' in metadata:raise ValueError('LAT app identity/debug mismatch')
    if candidate['environment']!='lat' or candidate['billingEnabled'] is not True or candidate['productionReady'] is not False:
        raise ValueError('LAT candidate profile mismatch')
    return candidate

if __name__=='__main__':
    result=verify()
    if result is None:raise SystemExit('No LAT candidate')
    print(json.dumps({'currentLatCandidate':True,'signatureVerified':True,'realLatPurchasesVerified':False,'productionReady':False}))
