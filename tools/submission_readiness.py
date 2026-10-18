"""Evidence-based local preflight. Does not publish, upload, sign or deploy."""
import argparse,json,xml.etree.ElementTree as ET
from datetime import datetime,timezone
from pathlib import Path
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
suites=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
tests=sum(int(n.get('tests',0)) for n in suites);failures=sum(int(n.get('failures',0))+int(n.get('errors',0)) for n in suites)
check('Host Android tests',bool(suites) and failures==0,'app/build/test-results/testDebugUnitTest',f'{tests} tests, {failures} failures/errors')
locales=read_json('localisation/validation.json')
check('Locale resource coverage',locales.get('resource_complete') is True,'localisation/validation.json','Missing resources, placeholders or plural categories')
check('Native language and full UI approval',locales.get('release_approved') is True,'localisation/REVIEW.md','Native review, remaining interpolations, voice and marketing copy')
server=ROOT/'artifacts/billing-server-tests.xml'
server_xml=ET.parse(server).getroot() if server.exists() else None
server_passed=server_xml is not None and bool(server_xml.findall('.//testcase')) and not server_xml.findall('.//failure') and not server_xml.findall('.//error')
check('Local receipt-service tests',server_passed,'artifacts/billing-server-tests.xml','Run node --test --test-reporter=junit billing-server/*.test.mjs')
check('Node/Kotlin signed-receipt contract',read_json('artifacts/billing-contract.json').get('passed') is True,'artifacts/billing-contract.json','Verify the actual Node signer with the compiled Kotlin verifier')
apk=read_json('artifacts/apk-validation.json');native=read_json('artifacts/native-alignment.json')
check('APK signature and binary alignment',apk.get('debug',{}).get('signatureVerified') is True and apk.get('release',{}).get('zipAlignment16k') is True and native.get('passed') is True,'artifacts/apk-validation.json; artifacts/native-alignment.json','Validate exact output binaries; release signing remains a separate publisher gate')
check('Unconfigured billing build rejection',read_json('artifacts/billing-configuration-guard.json').get('passed') is True,'artifacts/billing-configuration-guard.json','Verify enabled billing rejects missing console authentication key')
check('Amazon catalog and live verification',False,'docs/AMAZON_BILLING.md','Catalog, trusted TLS verifier, production secrets/key, App Tester and LAT evidence; publisher requested local preparation only')
check('Physical device QA',False,'docs/DEVICE_QA.md','Fire/phone, TalkBack, doze/reboot, permissions, speech and actual OCR checks')
notices=ROOT/'legal/third-party/manifest.json'
check('Native notice source collection',notices.exists(),'legal/third-party/manifest.json','Collect pinned upstream licence files')
inventory=read_json('docs/dependencies/runtime-inventory.json')
check('Resolved dependency inventory',bool(inventory.get('components')),'docs/dependencies/runtime-inventory.json','Generate from exact releaseRuntimeClasspath')
check('Licensing/privacy approval',False,'THIRD_PARTY_NOTICES.md','Publisher reviews all distribution obligations, support identity and privacy disclosures')
check('Publisher-signed release device test',False,'artifacts/deliverables/manifest.json','Release signing key and exact signed APK hardware smoke test')
check('Final store and website metadata',False,'marketing/listing-en-GB.json','Approved translations, contact/privacy/domain/store URLs and hardware screenshots')
check('Remaining product scope',False,'docs/RELEASE_READINESS.md','Complete the explicitly listed outstanding app features and integration checks')
result={'generatedAt':datetime.now(timezone.utc).isoformat(),'submission_ready':all(c['status']=='passed' for c in checks),
 'publishing_permitted':False,'publication':'Not performed; user explicitly prohibits publication','checks':checks}
(ROOT/'artifacts/submission-readiness.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps({'passed':sum(c['status']=='passed' for c in checks),'pending':sum(c['status']!='passed' for c in checks),'submission_ready':result['submission_ready'],'published':False}))
parser=argparse.ArgumentParser();parser.add_argument('--strict',action='store_true');args=parser.parse_args()
if args.strict and not result['submission_ready']:raise SystemExit(1)
