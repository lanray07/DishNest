"""Regenerate the human readiness report from completed local evidence."""
import json,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def data(path):return json.loads((ROOT/path).read_text(encoding='utf-8-sig'))
suites=[ET.parse(p).getroot() for p in (ROOT/'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
tests=sum(int(n.get('tests',0)) for n in suites)
failed=sum(int(n.get('failures',0))+int(n.get('errors',0)) for n in suites)
issues=ET.parse(ROOT/'app/build/reports/lint-results-debug.xml').getroot().findall('issue')
errors=[n for n in issues if n.get('severity') in ['Error','Fatal']]
warnings=[n for n in issues if n.get('severity')=='Warning']
locale=data('localisation/validation.json');inventory=data('docs/dependencies/runtime-inventory.json')
device=data('artifacts/device-evidence.json')
fire=data('artifacts/fire-device-qa.json') if (ROOT/'artifacts/fire-device-qa.json').exists() else {}
# Brand-only resources marked non-translatable are excluded from coverage.
strings=sum(n.get('translatable')!='false' for n in ET.parse(ROOT/'app/src/main/res/values/strings.xml').getroot().findall('string'))
server_xml=ET.parse(ROOT/'artifacts/billing-server-tests.xml').getroot()
server_tests=len(server_xml.findall('.//testcase'))
report=f'''# DishNest submission-readiness report

Status: **DEVELOPMENT PREVIEW — NOT READY TO SUBMIT**. Evidence date: 18 October 2026. **App deployment and store submission are prohibited.** Source commits and GitHub pushes are authorized. No app deployment or store submission was performed.

## Completed local continuation

- Filled the missing translation entries without suppressions or a lint baseline. All 12 requested locales now cover {strings} translatable strings plus recipe-count plurals. Additional static titles, callbacks and accessibility labels were extracted. Brand names remain unchanged; private recipes were not translated.
- Implemented Amazon purchase initiation, product-price display, pending approval, restoration pagination, account confirmation, receipt persistence, signed server verification, expiry/cancellation handling, bounded offline caching and acknowledgement after verification. The default APK keeps purchases disabled. The publisher confirmed there is no existing catalog or verifier and requested local preparation only.
- Added the loopback receipt service, development key/configuration preparation, App Tester fixtures and documented catalog contract. Server-only RVS secrets and private signing keys are excluded from the app and source archive. Enabled builds require Amazon's real app-specific authentication public key as well as the separate verifier public key and HTTPS endpoint.
- Added transactional backup snapshots and empty-kitchen restore guards; bounded photo-inclusive ZIP export/import with rollback cleanup; shared-reference-aware photo deletion and aged draft cleanup; combined recipe filters and direct persisted meal swaps. Fixed complete-command voice matching across all target language families and extracted additional dynamic labels, notifications and speech-service messages into locally authored resources.
- Prepared optional environment-only publisher signing and build input/output provenance checks, with credentials and generated packages excluded from Git.
- Added EXIF-aware, bounded photo decoding; explicit backup exclusions for legacy and modern Android; density-independent food images; an offline third-party notices viewer; resolved dependency inventory and a repeatable preflight.

## Executed verification

| Check | Result | Evidence |
| --- | --- | --- |
| Android host tests | {tests} tests; {failed} failures/errors | `app/build/test-results/testDebugUnitTest/` |
| Android lint | {len(errors)} errors; {len(warnings)} warnings | `app/build/reports/lint-results-debug.html` |
| Original translation errors | {sum(n.get('id')=='MissingTranslation' for n in errors)} remain | Same lint report; no suppression/baseline |
| Current build provenance | Input sources and exact test/lint/APK outputs hashed and verified before packaging | `artifacts/build-provenance.json` |
| Locale resource gate | {'PASS' if locale.get('resource_complete') else 'FAIL'} across 12 locales | `localisation/validation.json` |
| Physical Fire smoke tests | {str(fire.get('tests', 0)) + ' tests passed on ' + str(fire.get('model')) + ' / API ' + str(fire.get('api')) if fire.get('passed') else str(fire.get('passedTests', 0)) + '/' + str(fire.get('tests', 0)) + ' passed; UI blocked by secure keyguard' if fire else 'Not yet executed'} | `artifacts/fire-device-qa.json`, `artifacts/device-instrumentation.txt` |
| Receipt service | {server_tests} passing policy and local HTTP tests | `artifacts/billing-server-tests.xml` |
| Cross-runtime receipt contract | Actual Node signer and compiled Kotlin verifier pass; wrong-account response rejected | `artifacts/billing-contract.json` |
| Configuration guard | Enabled billing rejects a missing Amazon console authentication key before packaging | `artifacts/billing-configuration-guard.json` |
| Resolved dependency inventory | {len(inventory['components'])} artifacts; {sum(not n['licenses'] for n in inventory['components'])} missing licence declarations | `docs/dependencies/runtime-inventory.json` |
| Native binary and APK alignment/signature | See exact packaged APK evidence and checksums | `artifacts/native-alignment.json`, `artifacts/apk-validation.json`, delivery manifest |
| Website | Desktop 1440×1000 and phone 390×844 pass page identity, HTTP/content, FAQ interaction, console, image and overflow checks | `artifacts/website-verification.json` |
| Assets/listing dimensions | Local validation passes; all store outputs remain drafts | `artifacts/asset-validation.json` |

The Android host suite covers ingredient/quantity logic, planner/backup/voice/timer policies, Room persistence, entitlement signatures and clock handling, photo orientation, tablet/phone UI flows, empty launch, recipe editing across activity recreation and Arabic navigation/RTL. The native bitmap tests verify decoding mechanics; they do not verify OCR recognition accuracy. See individual XML suites for the authoritative cases.

Amazon's obfuscated SDK emits D8 stack-map warnings. Billing-disabled debug manifests exclude the IAP and DRM response receivers for host-JVM tests. Enabled debug and release manifests retain the required receivers; Amazon package visibility is declared. The SDK mode and licensing path is compiled, but App Tester/real store callbacks have not been exercised. No JVM bytecode verifier is disabled.

Android lint warnings remain visible. Dependency-update warnings are not a compatibility or security certification. Any warning changes should be reviewed against the exact latest report.

## Remaining implementation and verification

| Area | Implemented | Remaining |
| --- | --- | --- |
| Foundation | Compose, Room, DataStore, Flow, adaptive navigation | Hardware performance, process death, TalkBack, large fonts and full rotation QA |
| Recipes | CRUD, favourites, collections, photos, notes, combined search/collection/favourite/time/cuisine/tag/difficulty filters, sorting and shared photo cleanup | Full hardware accessibility review and additional vocabulary |
| Planner | Seven days/four slots, servings, tap assignment, previous-week copy, templates, direct two-meal swap and offline dinner suggestions | Separately named active plans and optional drag interactions |
| Shopping/pantry | Compatible consolidation, serving scaling, pantry deduction, manual items, check dates and name-based suggestions | Broader vocabulary and locale-specific units; scheduled pantry reminders; advanced Premium scope |
| Cooking/voice/timers | Touch fallback, narration, push-to-talk, persisted inexact timers, exact commands in all target language families and localised numeric timer grammar (English number words also supported) | Broader natural-language/quantity grammar; actual speech providers, audio focus, doze, reboot, permissions and clock-change device QA |
| Import/data | Reviewed text/JSON-LD, local English OCR, EXIF handling, transactional JSON and bounded photo-inclusive ZIP backups and local export | Live-site access-policy/robots validation, cropping and real-photo OCR accuracy; cloud provider remains optional/unconnected |
| Billing | Local SDK integration and signed RVS service with tested policies | Amazon console key/catalog, approved terms/prices/countries, trusted TLS service, production credentials, App Tester/LAT and full callback/fulfilment QA; further commercial gates/features |
| Localisation | {strings} strings and plurals in 12 locales; technical checks pass | Native-speaker review, remaining inline interpolations and domain/status errors, complete voice/category/unit work, localised website/store/screenshot copy and hardware RTL checks |
| Licensing/privacy | Original notices bundled; all resolved POM licences inventoried; backup exclusions and billing disclosures | Publisher legal/licence/privacy approval, operator identity, retention/contact details and final dependency review |
| Marketing/site | Original artwork, draft host-rendered screenshots, local static site and browser checks | Hardware captures, market approvals, real domain/canonical/hreflang/contact/privacy/store URLs and final listing approval |
| Release | Debug APK and unsigned release build; optional publisher environment signing, input/output hash verification and local validation tooling | Publisher signing material, exact signed-APK install/smoke test and actual store compatibility checks |
| Metrics | Optional aggregate counters stored only on device; disabled by default | No analytics provider or complete Premium/trial conversion reporting |

Some commercial feature scope remains unfinished; local receipt policy tests do not make the entire Premium product complete. Existing content remains readable/editable after entitlement expiry. JSON recovery does not discard a former Premium user's larger recipe book. A demo only bypasses Premium gates in billing-disabled development builds.

The translation provider throttled the initial draft generation. Requests were stopped, and missing drafts were completed locally from authored tables. Native review remains pending. The heuristic extraction report flags {locale['hardcoded_text_count']} candidates (including dates, numbers, user content and an HTTP header); it is not a complete audit of every user-facing domain error or voice response. Resource coverage and publication approval are separate gates.

## External setup and hardware

The latest read-only ADB inventory found {device.get('connectedDeviceCount', 0)} connected device(s). {'The local debug APK passed ' + str(fire.get('tests')) + ' instrumentation tests on ' + str(fire.get('model')) + ' (Android ' + str(fire.get('androidVersion')) + ', API ' + str(fire.get('api')) + '). Coverage includes empty launch, real recipe CRUD, SQLite reopen, native image/archive codecs, a synthetic printed OCR fixture and Arabic/Hindi resource formatting.' if fire.get('passed') else str(fire.get('passedTests', 0)) + ' native tests passed on the current debug APK (SQLite reopen, image/archive codecs, synthetic OCR and resource formatting). The UI test was blocked because the tablet was asleep behind a secure keyguard. User unlock was requested; no authentication bypass was attempted.' if fire else 'Device instrumentation has not passed on the current APK.'} This smoke pass does not cover real-photo OCR, microphone/TTS, alarms/doze/reboot, TalkBack/large fonts, the full permission/RTL matrix, a publisher-signed APK or Appstore purchases. Those remain pending. Follow `docs/DEVICE_QA.md` and `docs/AMAZON_BILLING.md` and attach observed evidence before marking those cases passed.

Required publisher inputs are an Amazon app draft and its authentication public key; approved catalog/prices/territories; service operating identity and protected production keys/secrets; a trusted verification endpoint; publisher signing configuration; representative hardware; native language approval; and support/privacy/domain details. The local sandbox keys are development-only. Do not copy them into production.

## Checklist

- [x] Remove the 141 MissingTranslation errors and run the resource coverage gate.
- [x] Implement and test local receipt policy, signatures, expiry/cancellation rules and HTTP contract.
- [x] Prepare catalog/App Tester fixtures and local development service configuration.
- [x] Add privacy backup exclusions, EXIF handling, original notices and dependency inventory.
- [x] Run local Android builds/tests/lint and desktop/phone website checks; retain actual reports.
- [ ] Finish the remaining app scope above and approve every target locale.
- [ ] Configure Amazon and the operated verifier, then execute App Tester/LAT lifecycle cases.
- [ ] Execute the hardware/accessibility/offline/permissions/timer/OCR matrix.
- [ ] Approve publisher identity, licensing, privacy disclosures, pricing and final store/site metadata.
- [ ] Sign and install the exact release APK, capture final hardware screenshots and record device smoke tests.
- [ ] Obtain a new explicit instruction before app deployment or store submission. Source commits and GitHub pushes are authorized.

## Primary references checked

- [Amazon listing and image requirements](https://developer.amazon.com/docs/app-submission/appstore-details.html): draft dimensions/copy constraints were checked locally; confirm the live console before eventual submission.
- [Appstore SDK integration and app-specific public key](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).
- [Amazon IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html) and [RVS semantics](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
- [App Tester setup](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html) and [testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
- [Amazon Android mobile distribution change](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program): Fire support continues; ordinary Android phones need a separate distribution strategy.
'''
(ROOT/'docs/RELEASE_READINESS.md').write_text(report,encoding='utf-8')
print(json.dumps({'android_tests':tests,'test_failures':failed,'lint_errors':len(errors),'lint_warnings':len(warnings),'submission_ready':False,'published':False}))
