"""Evidence-based local preflight. Does not publish, upload, sign or deploy."""
import argparse,json,xml.etree.ElementTree as ET
from datetime import datetime,timezone
from pathlib import Path
from build_evidence import verify as verify_build, digest
from website_evidence import verify as verify_website
ROOT=Path(__file__).resolve().parents[1]
checks=[]
def check(name,passed,evidence,remaining=''):
    checks.append({'check':name,'status':'passed' if passed else 'pending','evidence':evidence,'remaining':remaining if not passed else ''})
def read_json(path):
    file=ROOT/path
    return json.loads(file.read_text(encoding='utf-8-sig')) if file.exists() else {}

lint=ROOT/'app/build/reports/lint-results-debug.xml'
issues=ET.parse(lint).getroot().findall('issue') if lint.exists() else []
errors=[i for i in issues if i.get('severity') in ['Error','Fatal']]
check('Android lint',lint.exists() and not errors,str(lint.relative_to(ROOT)),f'{len(errors)} errors; rerun lint on current sources')
qa_lint=ROOT/'app/build/reports/lint-results-qa.xml'
qa_errors=[i for i in ET.parse(qa_lint).getroot().findall('issue') if i.get('severity') in ['Error','Fatal']] if qa_lint.exists() else []
check('Isolated QA lint',qa_lint.exists() and not qa_errors,str(qa_lint.relative_to(ROOT)),'Run QA lint for the exact instrumentation target')
suites=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
tests=sum(int(n.get('tests',0)) for n in suites);failures=sum(int(n.get('failures',0))+int(n.get('errors',0)) for n in suites)
check('Host Android tests',bool(suites) and tests>0 and failures==0,'app/build/test-results/testDebugUnitTest',f'{tests} tests, {failures} failures/errors')
for suffix, name in [('ReadinessDataTest', 'Restore, photo archive and planner data policies'), ('VoiceLocalesTest', 'Target-locale voice command grammar'), ('RecipeQueryTest', 'Combined recipe filters'), ('FeatureScopeTest', 'Named plans, schema migration and reminder/timer policies')]:
    selected = [suite for suite in suites if suite.get('name', '').endswith(suffix)]
    passed = bool(selected) and all(int(s.get('tests', 0)) > 0 and int(s.get('failures', 0)) + int(s.get('errors', 0)) == 0 for s in selected)
    check(name, passed, f'app/build/test-results/testDebugUnitTest/TEST-com.dishnest.app.{suffix}.xml', 'Run the current complete host test suite')
locales=read_json('localisation/validation.json')
check('Evidence matches current build',verify_build(),'artifacts/build-provenance.json','Run full Gradle verification and record the input/output hashes')
check('Locale resource coverage',locales.get('resource_complete') is True,'localisation/validation.json','Missing resources, placeholders or plural categories')
import_suites = [s for s in suites if s.get('name', '').endswith('RecipeWebAccessTest')]
check('Recipe website policy tests',bool(import_suites) and all(int(s.get('tests',0)) >= 7 and int(s.get('skipped',0)) == 0 and int(s.get('failures',0)) + int(s.get('errors',0)) == 0 for s in import_suites),'app/build/test-results/testDebugUnitTest/TEST-com.dishnest.app.RecipeWebAccessTest.xml','Run the bounded import, robots policy and redirect fixture tests')
check('Current desktop and phone website QA',verify_website(),'artifacts/website-verification.json','Run browser verification on all current local pages at both viewport sizes')
check('Native language and full UI approval',locales.get('release_approved') is True,'localisation/REVIEW.md','Native review, remaining interpolations, voice and marketing copy')
server=ROOT/'artifacts/billing-server-tests.xml'
server_xml=ET.parse(server).getroot() if server.exists() else None
server_passed=server_xml is not None and bool(server_xml.findall('.//testcase')) and not server_xml.findall('.//failure') and not server_xml.findall('.//error')
worker=read_json('artifacts/billing-worker.json')
worker_sources=['worker.mjs','verification.mjs','worker.test.mjs','wrangler.json','package-lock.json']
worker_current=worker.get('passed') is True and all((ROOT/'billing-server'/name).is_file() and worker.get('sourceSha256',{}).get(name)==digest(ROOT/'billing-server'/name) for name in worker_sources)
check('Local receipt-service tests',server_passed and worker_current,'artifacts/billing-server-tests.xml; artifacts/billing-worker.json','Run npm ci in billing-server, then node --test --test-reporter=junit billing-server/*.test.mjs; Worker proof must match sources')
live_contract=read_json('artifacts/billing-live-contract.json')
live_paths=['tools/verify_billing_contract.mjs','billing-server/worker.mjs','billing-server/verification.mjs','app/build/tmp/kotlin-classes/debug/com/dishnest/app/platform/EntitlementVerifier.class']
live_current=live_contract.get('passed') is True and all((ROOT/p).is_file() and live_contract.get('sourceSha256',{}).get(p)==digest(ROOT/p) for p in live_paths)
check('Node/Worker/Kotlin signed-receipt contracts',read_json('artifacts/billing-contract.json').get('passed') is True and live_current,'artifacts/billing-contract.json; artifacts/billing-live-contract.json','Verify local signing and live signed inactive responses with compiled Kotlin using node tools/verify_billing_contract.mjs --live')
apk=read_json('artifacts/apk-validation.json');native=read_json('artifacts/native-alignment.json')
apk_paths = {'debug': 'app/build/outputs/apk/debug/app-debug.apk', 'qa': 'app/build/outputs/apk/qa/app-qa.apk', 'release': 'app/build/outputs/apk/release/app-release-unsigned.apk'}
apk_current = all((ROOT/path).is_file() and apk.get(variant,{}).get('sha256') == digest(ROOT/path) for variant,path in apk_paths.items())
native_current = (ROOT/apk_paths['release']).is_file() and native.get('apk_sha256') == digest(ROOT/apk_paths['release'])
check('APK signature and binary alignment',apk_current and native_current and apk.get('debug',{}).get('signatureVerified') is True and apk.get('release',{}).get('zipAlignment16k') is True and native.get('passed') is True and apk.get('qa',{}).get('signatureVerified') is True and apk.get('qa',{}).get('isolatedPackageVerified') is True and apk.get('qa',{}).get('billingDisabled') is True and apk.get('release',{}).get('qaFixtureAbsent') is True,'artifacts/apk-validation.json; artifacts/native-alignment.json','Validate exact output binaries; release signing remains a separate publisher gate')
check('Unconfigured billing build rejection',read_json('artifacts/billing-configuration-guard.json').get('passed') is True,'artifacts/billing-configuration-guard.json','Verify enabled billing rejects missing console authentication key')
check('Incomplete publisher signing rejection',read_json('artifacts/signing-configuration-guard.json').get('passed') is True,'artifacts/signing-configuration-guard.json','Verify partial environment signing configuration is rejected')
check('Amazon catalog and live verification',False,'docs/AMAZON_BILLING.md; marketing/amazon-premium-console.json; artifacts/billing-deployment.json','Approved prices and localized catalog drafts saved; real public authentication key installed and Free-plan verifier deployed with protected keys. App Tester/LAT real purchase, restoration and subscription lifecycle QA remain required; APK purchases disabled')
fire=read_json('artifacts/fire-device-qa.json')
fire_current=fire.get('apkSha256') == digest(ROOT/'app/build/outputs/apk/qa/app-qa.apk') if (ROOT/'app/build/outputs/apk/qa/app-qa.apk').exists() else False
fire_test = ROOT/'app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk'
fire_current = fire_current and fire.get('packageId') == 'com.dishnest.app.qa' and fire_test.exists() and fire.get('instrumentationApkSha256') == digest(fire_test)
check('Physical Fire native components',fire_current and fire.get('passedTests', fire.get('tests', 0) if fire.get('passed') else 0) >= 4,'artifacts/fire-device-qa.json','Run native SQLite, image/archive, OCR fixture and resource tests on the current APK')
check('Physical Fire instrumentation smoke',fire.get('passed') is True and fire_current,'artifacts/fire-device-qa.json','Native tests passed; unlock the secure Fire display to rerun the UI smoke suite' if fire.get('uiBlocker') else 'Run the native/UI smoke suite on the selected device with the current isolated QA APK')
alarm = read_json('artifacts/fire-background-alarm.json')
check('Background Fire timer after process termination', fire_current and alarm.get('passed') is True and alarm.get('processAbsentAfterKill') is True and alarm.get('installedApksVerified') is True and alarm.get('controllerSha256') == digest(ROOT/'tools/run_fire_alarm_probe.ps1') and alarm.get('notificationHelperSha256') == digest(ROOT/'tools/fire_notification_setting.py') and alarm.get('kitchenPersistencePassed') is True and alarm.get('apkSha256') == fire.get('apkSha256') and alarm.get('instrumentationApkSha256') == fire.get('instrumentationApkSha256'), 'artifacts/fire-background-alarm.json', 'Run the two-process kitchen/timer probe on the exact installed QA app and runner APKs')
check('Physical device QA',False,'docs/DEVICE_QA.md','Fire/phone, TalkBack, doze/reboot, permissions, speech and actual OCR checks')
notices=ROOT/'legal/third-party/manifest.json'
check('Native notice source collection',notices.exists(),'legal/third-party/manifest.json','Collect pinned upstream licence files')
inventory=read_json('docs/dependencies/runtime-inventory.json')
check('Resolved dependency inventory',bool(inventory.get('components')) and all(c.get('licenses') for c in inventory['components']),'docs/dependencies/runtime-inventory.json','Generate from exact releaseRuntimeClasspath and resolve missing licence declarations')
check('Licensing/privacy approval',False,'THIRD_PARTY_NOTICES.md','Publisher O. Bankole confirmed; approval of distribution obligations, production service-operation details and privacy disclosures remains pending')
check('Publisher-signed release device test',False,'artifacts/publisher-signing.json; artifacts/publisher-device-compatibility.json','Permanent key created with explicit approval; signature/payload/alignment verified. Secure backup and exact signed APK hardware smoke remain pending; installed ordinary Fire app has a conflicting debug certificate and was preserved.')
check('Final store and website metadata',False,'marketing/amazon-console-draft.json','10 description locales and 7 artwork locales saved in the Amazon draft; native/legal approval, final store URL, approved hardware screenshots and publisher-signed app file pending')
check('Remaining product scope',False,'docs/RELEASE_READINESS.md','Complete the explicitly listed outstanding app features and integration checks')
result={'generatedAt':datetime.now(timezone.utc).isoformat(),'submission_ready':all(c['status']=='passed' for c in checks),
 'publishing_permitted':False,'git_push_permitted':True,'publication':'Store submission conditionally authorized only after every readiness gate passes; app deployment not authorized; source commits and GitHub pushes authorized','checks':checks}
(ROOT/'artifacts/submission-readiness.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({'passed':sum(c['status']=='passed' for c in checks),'pending':sum(c['status']!='passed' for c in checks),'submission_ready':result['submission_ready'],'published':False}))
parser=argparse.ArgumentParser();parser.add_argument('--strict',action='store_true');args=parser.parse_args()
if args.strict and not result['submission_ready']:raise SystemExit(1)
