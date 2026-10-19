from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
from build_evidence import verify as verify_build
from website_evidence import verify as verify_website
from publisher_evidence import verify as verify_publisher
from purchase_evidence import verify as verify_purchase
ROOT=Path(__file__).resolve().parents[1];OUT=ROOT/'artifacts/deliverables';OUT.mkdir(exist_ok=True)
if not verify_build():raise SystemExit('Current sources/output hashes do not match build evidence. Rebuild and run tools/build_evidence.py --record.')
if not verify_website():raise SystemExit('Website evidence is stale or incomplete. Run tools/verify_website.cjs against the current local pages before packaging.')
build_log=ROOT/'artifacts/delivery-verification.log'
if not build_log.exists() or 'BUILD SUCCESSFUL' not in build_log.read_text(encoding='utf-8',errors='replace'):
    raise SystemExit('Complete the current full Gradle verification before packaging.')
lint=ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot()
if any(n.attrib.get('severity') in {'Error','Fatal'} for n in lint.findall('issue')):raise SystemExit('Android lint errors remain.')
for file in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite=ET.parse(file).getroot()
    if int(suite.attrib.get('failures',0))+int(suite.attrib.get('errors',0)):raise SystemExit('Android test failures remain.')
manifest=[]
for source,name in [(ROOT/'app/build/outputs/apk/debug/app-debug.apk','DishNest-0.1.0-debug.apk'),(ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk','DishNest-0.1.0-release-unsigned.apk'),(ROOT/'app/build/outputs/apk/qa/app-qa.apk','DishNest-0.1.0-qa.apk')]:
    if source.exists():
        target=OUT/name;shutil.copy2(source,target);manifest.append({'file':name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'signed':name.endswith(('-debug.apk','-qa.apk')),'production_ready':False})
publisher=verify_publisher()
if publisher:
    target=OUT/'DishNest-0.1.0-release-publisher.apk'
    shutil.copy2(ROOT/publisher['signedApk'],target)
    manifest.append({'file':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'signed':True,'certificateSha256':publisher['certificateSha256'],'production_ready':False})
purchase_blocker = None
try:
    purchase=verify_purchase()
except (ValueError, KeyError, FileNotFoundError):
    # Optional debug purchase evidence must never block delivery of unrelated local
    # fixes or be silently promoted to current evidence after a source change.
    purchase=None
    purchase_blocker='Retained purchase evidence is incomplete or does not match current sources/APKs; rerun required.'
if purchase:
    target=OUT/'DishNest-0.1.0-purchase-test.apk'
    shutil.copy2(ROOT/'app/build/outputs/apk/purchaseTest/app-purchaseTest.apk',target)
    manifest.append({'file':target.name,'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'signed':True,'environment':'sandbox','kind':'isolated debug App Tester only','production_ready':False})
else:
    previous=OUT/'DishNest-0.1.0-purchase-test.apk'
    if previous.is_file():
        previous_hash=hashlib.sha256(previous.read_bytes()).hexdigest()
        history=OUT/'history'/previous_hash
        history.mkdir(parents=True,exist_ok=True)
        shutil.move(str(previous),str(history/previous.name))
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
def archive_source():
    evidence={'build-provenance.json','signing-configuration-guard.json','signing-configuration-guard.log','fire-device-qa.json','device-instrumentation.txt','fire-background-alarm.json','fire-background-seed.txt','fire-background-verify.txt','qa-summary.json','submission-readiness.json','delivery-verification.log','billing-server-tests.xml','billing-server-tests.txt','billing-contract.json','billing-configuration-guard.json','billing-configuration-guard.log','device-evidence.json','device-inventory.txt','runtime-components.json','native-alignment.json','apk-validation.json','apk-signature.txt','apk-alignment.txt','apk-metadata.txt','asset-validation.json','website-verification.json'}
    evidence.update({'visual-localization.json','fire-blocked-notifications.json','fire-blocked-notifications-seed.txt','fire-blocked-notifications-verify.txt','fire-notification-appops-failure.txt','fire-notification-appops-diagnostic.json','fire-notification-helper-tests.json','fire-notification-helper-tests.txt'})
    source_zip=OUT/'DishNest-source.zip'
    evidence.add('billing-worker.json')
    evidence.update({'publisher-signing.json','publisher-signature.txt','publisher-device-compatibility.json','private-storage-audit.json'})
    evidence.update({'amazon-purchase-device.json','amazon-sandbox-receipt-review.json','purchase-test-restoration.json','purchase-test-configuration-guard.json','purchase-test-configuration-guard.log','purchase-test-controller-guard.json','purchase-test-build.log'})
    evidence.update({'billing-deployment.json','billing-live-contract.json','amazon-authentication-key.json','website-http-log.txt'})
    evidence.update({'fire-native-qa.json','fire-native-instrumentation.txt'})
    with zipfile.ZipFile(source_zip,'w',zipfile.ZIP_DEFLATED) as archive:
        for file in ROOT.rglob('*'):
            if not file.is_file():continue
            relative=file.relative_to(ROOT)
            if any(part in {'.git','.gradle','.kotlin','.idea','build','__pycache__','.local-billing','.local-signing','draft-cache','node_modules','.wrangler'} for part in relative.parts):continue
            if relative.name == '.dev.vars' or relative.name.startswith('.dev.vars.'):continue
            if relative.parts[:2]==('localisation','cache'):continue
            if relative.name in {'local.properties','.env'} or relative.name.startswith('.env.') and relative.name != '.env.example':continue
            if relative.suffix in {'.jks','.keystore','.pem','.p12','.pfx','.key','.pk8','.p8'} and relative.as_posix()!='app/src/main/assets/AppstoreAuthenticationKey.pem':continue
            if relative.parts[0]=='artifacts' and relative.parts[1:2]!=('screens',) and not (len(relative.parts)==2 and relative.name in evidence):continue
            archive.write(file,relative.as_posix())
    with zipfile.ZipFile(source_zip) as archive:
        assert archive.testzip() is None
        assert not any('.local-billing' in name or '.local-signing' in name or name.endswith('/signing.pem') for name in archive.namelist())
    return source_zip
fire_path=ROOT/'artifacts/fire-device-qa.json'
fire=json.loads(fire_path.read_text(encoding='utf-8-sig')) if fire_path.exists() else {}
suites=[]
for file in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite=ET.parse(file).getroot();suites.append({k:suite.attrib.get(k) for k in ['name','tests','failures','errors','skipped','timestamp']})
server_xml=ET.parse(ROOT/'artifacts/billing-server-tests.xml').getroot()
server_tests=len(server_xml.findall('.//testcase'))
if server_xml.findall('.//failure') or server_xml.findall('.//error'):raise SystemExit('Receipt-service test failures remain.')
(ROOT/'artifacts/qa-summary.json').write_text(json.dumps({'jvm_suites':suites,'device_instrumentation':fire,'physical_fire_qa':'Partial smoke coverage only; full hardware matrix pending','billing_qa':f'Local SDK integration and signed-verification service prepared; {server_tests} server fixture/HTTP tests pass. App Tester/LAT not executed; default APK purchases disabled.','localisation_release_gate':'Resource coverage complete across 12 locales; native review and remaining interpolation/voice/marketing work pending. See localisation/validation.json.','store_submission':'not performed; conditional submission authorized only after all required readiness gates pass','website_deployment':'not performed; local checks only'},indent=2),encoding='utf-8')
summary_path=ROOT/'artifacts/qa-summary.json'
summary=json.loads(summary_path.read_text(encoding='utf-8'))
summary['billing_qa']=f'{server_tests} service tests pass; '+('current isolated App Tester lifetime/restore and denial cases pass; subscription synthetic-expiry and LAT lifecycle remain pending.' if purchase else 'current App Tester evidence pending.')+' Default release purchases remain disabled.'
summary['isolated_app_tester']={'current':purchase is not None,'production_purchases_approved':False,'evidence':'artifacts/amazon-purchase-device.json','remaining':'Subscription synthetic-expiry limitation, full lifecycle and LAT; all release approvals remain required'}
summary['isolated_app_tester']['current_evidence_blocker']=purchase_blocker
native_path=ROOT/'artifacts/fire-native-qa.json'
if native_path.is_file():
    native=json.loads(native_path.read_text(encoding='utf-8'))
    summary['fire_native_qa']=native
    summary['fire_native_qa_current']=native.get('passed') is True and all(
        (ROOT/path).is_file() and hashlib.sha256((ROOT/path).read_bytes()).hexdigest()==native.get(key)
        for path,key in [('app/build/outputs/apk/qa/app-qa.apk','apkSha256'),
                         ('app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk','instrumentationApkSha256'),
                         ('tools/run_fire_native_checks.py','controllerSha256')])
qa_source=ROOT/'app/build/outputs/apk/qa/app-qa.apk'
test_source=ROOT/'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
summary['device_instrumentation_current']=all(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==fire.get(key)
    for path,key in [(qa_source,'apkSha256'),(test_source,'instrumentationApkSha256')])
if not summary['device_instrumentation_current']:
    summary['physical_fire_qa']='Pending current APK rerun; retained device results are historical. Full hardware matrix also pending.'
summary['visual_localization']=json.loads((ROOT/'artifacts/visual-localization.json').read_text(encoding='utf-8'))
lint_file=ROOT/'app/build/reports/lint-results-debug.xml'
if lint_file.exists():
    issues=ET.parse(lint_file).getroot().findall('issue')
    errors=[i for i in issues if i.attrib.get('severity') in ['Error','Fatal']]
    summary['android_lint']={'errors':len(errors),'error_categories':sorted({i.attrib.get('id') for i in errors}),'passed':not errors,'report':'app/build/reports/lint-results-debug.html'}
for name in ['asset-validation','website-verification','native-alignment','device-evidence','fire-device-qa','fire-blocked-notifications','submission-readiness','billing-contract','billing-worker','billing-deployment','billing-live-contract','amazon-authentication-key','billing-configuration-guard','publisher-signing','publisher-device-compatibility']:
    report=ROOT/f'artifacts/{name}.json'
    if report.exists():summary[name]=json.loads(report.read_text(encoding='utf-8-sig'))
blocked=summary.get('fire-blocked-notifications', {})
summary['blocked_notifications_current']=summary['device_instrumentation_current'] and blocked.get('apkSha256')==fire.get('apkSha256') and blocked.get('instrumentationApkSha256')==fire.get('instrumentationApkSha256') and blocked.get('notificationSettingRestored') is True and blocked.get('notificationsBlocked') is True and blocked.get('passed') is True and blocked.get('controllerSha256')==hashlib.sha256((ROOT/'tools/run_fire_alarm_probe.ps1').read_bytes()).hexdigest() and blocked.get('notificationHelperSha256')==hashlib.sha256((ROOT/'tools/fire_notification_setting.py').read_bytes()).hexdigest()
summary_path.write_text(json.dumps(summary,indent=2),encoding='utf-8')
source_zip=archive_source()
manifest.append({'file':source_zip.name,'bytes':source_zip.stat().st_size,'sha256':hashlib.sha256(source_zip.read_bytes()).hexdigest(),'kind':'source archive with verification evidence','production_ready':False})
(OUT/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps({'apks_packaged':sum(n['file'].endswith('.apk') for n in manifest),'jvm_suites':len(suites),'server_tests':server_tests,'source_archive_crc_valid':True,'published':False}))
