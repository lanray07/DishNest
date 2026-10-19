"""Verify isolated App Tester evidence; never approves production purchases."""
import json,os,subprocess
from pathlib import Path
from build_evidence import digest,input_hashes
ROOT=Path(__file__).resolve().parents[1]
def verify():
    report_path=ROOT/'artifacts/amazon-purchase-device.json'
    if not report_path.exists():return None
    report=json.loads(report_path.read_text(encoding='utf-8'))
    restored=json.loads((ROOT/'artifacts/purchase-test-restoration.json').read_text(encoding='utf-8'))
    apk=ROOT/'app/build/outputs/apk/purchaseTest/app-purchaseTest.apk'
    if report['sourceSha256']!=input_hashes() or digest(apk)!=report['apkSha256'] or restored['apkSha256']!=report['apkSha256']:raise ValueError('Stale App Tester source/APK evidence')
    if restored['controllerSha256']!=digest(ROOT/'tools/purchase_test_device.py'):raise ValueError('Stale purchase restoration controller')
    for flag in ['catalogRestored','sandboxPropertyRestored','testerUserRestored','testerSubscriptionSettingsRestored','temporaryAllowanceRemoved','ordinaryDataHashesUnchanged']:
        if restored.get(flag) is not True:raise ValueError('Incomplete purchase-test restoration: '+flag)
    cases={case['case']:case for case in report['cases']}
    for name,active in [('fresh-account-no-access',False),('canceled-checkout-no-grant',False),('expired-subscription-fails-closed',False),('lifetime-account-restoration',True),('lifetime-after-process-restart',True)]:
        case=cases[name];status=case['status']
        if not case['passed'] or status['premium']!=active or status['sdkMode']!='SANDBOX' or status['environment']!='sandbox' or not all(status[key] for key in ['sandboxEndpoint','debug','billingEnabled','keyValid']):raise ValueError('Incomplete App Tester case: '+name)
    tools=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/build-tools/35.0.0'
    subprocess.run([str(tools/'apksigner.bat'),'verify',str(apk)],capture_output=True,check=True)
    metadata=subprocess.run([str(tools/'aapt.exe'),'dump','badging',str(apk)],capture_output=True,text=True,check=True).stdout
    if "name='com.dishnest.app.purchasetest'" not in metadata or 'application-debuggable' not in metadata:raise ValueError('Purchase-test APK identity/debug flag mismatch')
    return report
if __name__=='__main__':
    report=verify()
    if report is None:raise SystemExit('No App Tester device evidence')
    print(json.dumps({'currentIsolatedPurchaseEvidence':True,'cases':len(report['cases']),'productionPurchasesApproved':False}))
