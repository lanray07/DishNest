# DishNest submission-readiness report

Status: **DEVELOPMENT PREVIEW — NOT READY TO SUBMIT**. Evidence date: 18 October 2026. **App deployment and store submission are prohibited.** Source commits and GitHub pushes are authorized. No app deployment or store submission was performed.

## Completed local continuation

- Filled the missing translation entries without suppressions or a lint baseline. All 12 requested locales now cover 330 translatable strings plus recipe-count plurals. Additional static titles, callbacks and accessibility labels were extracted. Brand names remain unchanged; private recipes were not translated.
- Implemented Amazon purchase initiation, product-price display, pending approval, restoration pagination, account confirmation, receipt persistence, signed server verification, expiry/cancellation handling, bounded offline caching and acknowledgement after verification. The default APK keeps purchases disabled. The publisher confirmed there is no existing catalog or verifier and requested local preparation only.
- Added the loopback receipt service, development key/configuration preparation, App Tester fixtures and documented catalog contract. Server-only RVS secrets and private signing keys are excluded from the app and source archive. Enabled builds require Amazon's real app-specific authentication public key as well as the separate verifier public key and HTTPS endpoint.
- Added transactional backup snapshots and empty-kitchen restore guards; bounded photo-inclusive ZIP export/import with rollback cleanup; shared-reference-aware photo deletion and aged draft cleanup; combined recipe filters and direct persisted meal swaps. Fixed complete-command voice matching across all target language families and extracted additional dynamic labels, notifications and speech-service messages into locally authored resources.
- Added named active meal plans, Premium multi-plan shopping, a data-preserving Room v1-to-v2 migration and compatible v1/v2 backups and demo loading into the persisted selected plan, including after default-plan deletion; opt-in generic pantry check-date reminders; an adjustable offline OCR crop preview; audio-focus interruption handling and serialized timer callbacks.
- Added a separate billing-disabled DishNest QA package so disposable test fixtures cannot reset the ordinary app.
- Prepared optional environment-only publisher signing and build input/output provenance checks, with credentials and generated packages excluded from Git.
- Added EXIF-aware, bounded photo decoding; explicit backup exclusions for legacy and modern Android; density-independent food images; an offline third-party notices viewer; resolved dependency inventory and a repeatable preflight.

## Executed verification

| Check | Result | Evidence |
| --- | --- | --- |
| Android host tests | 66 tests; 0 failures/errors | `app/build/test-results/testDebugUnitTest/` |
| Android lint | 0 errors; 46 warnings | `app/build/reports/lint-results-debug.html` |
| Original translation errors | 0 remain | Same lint report; no suppression/baseline |
| Current build provenance | Input sources and exact test/lint/APK outputs hashed and verified before packaging | `artifacts/build-provenance.json` |
| Locale resource gate | PASS across 12 locales | `localisation/validation.json` |
| Physical Fire smoke tests | 10 tests passed on KFTRWI / API 28 | `artifacts/fire-device-qa.json`, `artifacts/device-instrumentation.txt` |
| Fire background timer probe | PASS: handled state and own notification after QA process termination | `artifacts/fire-background-alarm.json` |
| Receipt service | 11 passing policy and local HTTP tests | `artifacts/billing-server-tests.xml` |
| Cross-runtime receipt contract | Actual Node signer and compiled Kotlin verifier pass; wrong-account response rejected | `artifacts/billing-contract.json` |
| Configuration guard | Enabled billing rejects a missing Amazon console authentication key before packaging | `artifacts/billing-configuration-guard.json` |
| Resolved dependency inventory | 92 artifacts; 0 missing licence declarations | `docs/dependencies/runtime-inventory.json` |
| Native binary and APK alignment/signature | See exact packaged APK evidence and checksums | `artifacts/native-alignment.json`, `artifacts/apk-validation.json`, delivery manifest |
| Website | Desktop 1440×1000 and phone 390×844 pass page identity, HTTP/content, FAQ interaction, console, image and overflow checks | `artifacts/website-verification.json` |
| Assets/listing dimensions | Local validation passes; all store outputs remain drafts | `artifacts/asset-validation.json` |

The Android host suite covers ingredient/quantity logic, planner/backup/voice/timer policies, Room persistence, entitlement signatures and clock handling, photo orientation, tablet/phone UI flows, empty launch, recipe editing across activity recreation and Arabic navigation/RTL. The native bitmap tests verify decoding mechanics; they do not verify OCR recognition accuracy. See individual XML suites for the authoritative cases.

Amazon's obfuscated SDK emits D8 stack-map warnings. Billing-disabled debug manifests exclude the IAP and DRM response receivers for host-JVM tests. Enabled debug and release manifests retain the required receivers; Amazon package visibility is declared. The SDK mode and licensing path is compiled, but App Tester/real store callbacks have not been exercised. No JVM bytecode verifier is disabled.

Android lint warnings remain visible. Dependency-update warnings are not a compatibility or security certification. Any warning changes should be reviewed against the exact latest report.

## Remaining implementation and verification

| Area | Implemented | Remaining |
| --- | --- | --- |
| Foundation | Compose, Room, DataStore, Flow, adaptive navigation | Hardware performance, full process/rotation matrix, TalkBack and system font scaling |
| Recipes | CRUD, favourites, collections, photos, notes, combined search/collection/favourite/time/cuisine/tag/difficulty filters, sorting and shared photo cleanup | Full hardware accessibility review and additional vocabulary |
| Planner | Seven days/four slots, servings, tap assignment, previous-week copy, templates, direct two-meal swap, named selected plans and offline dinner suggestions | Optional drag interactions and broader planning QA |
| Shopping/pantry | Compatible consolidation, serving scaling, pantry deduction, manual items, check dates, optional daily reminders, combined-plan shopping and name-based suggestions | Broader vocabulary and locale-specific units; remaining advanced Premium scope |
| Cooking/voice/timers | Touch fallback, narration with audio-focus handling, push-to-talk, persisted inexact timers with stale-callback protection, exact commands in all target language families and localised numeric timer grammar (English number words also supported) | Broader natural-language/quantity grammar; actual speech providers, audio focus, doze, reboot, permissions and clock-change device QA |
| Import/data | Reviewed text/JSON-LD, local English OCR with crop preview, EXIF handling, transactional JSON and bounded photo-inclusive ZIP backups and local export | Live-site access-policy/robots validation and real-photo OCR accuracy; cloud provider remains optional/unconnected |
| Billing | Local SDK integration and signed RVS service with tested policies | Amazon console key/catalog, approved terms/prices/countries, trusted TLS service, production credentials, App Tester/LAT and full callback/fulfilment QA; further commercial gates/features |
| Localisation | 330 strings and plurals in 12 locales; technical checks pass | Native-speaker review, remaining inline interpolations and domain/status errors, complete voice/category/unit work, localised website/store/screenshot copy and hardware RTL checks |
| Licensing/privacy | Original notices bundled; all resolved POM licences inventoried; backup exclusions and billing disclosures | Publisher legal/licence/privacy approval, operator identity, retention/contact details and final dependency review |
| Marketing/site | Original artwork, host renders, inspected Fire QA captures, local static site and browser checks | Approved final hardware captures, market approvals, real domain/canonical/hreflang/contact/privacy/store URLs and final listing approval |
| Release | Debug APK and unsigned release build; optional publisher environment signing, input/output hash verification and local validation tooling | Publisher signing material, exact signed-APK install/smoke test and actual store compatibility checks |
| Metrics | Optional aggregate counters stored only on device; disabled by default | No analytics provider or complete Premium/trial conversion reporting |

Some commercial feature scope remains unfinished; local receipt policy tests do not make the entire Premium product complete. Existing content remains readable/editable after entitlement expiry. JSON recovery does not discard a former Premium user's larger recipe book. A demo only bypasses Premium gates in billing-disabled development builds.

The translation provider throttled the initial draft generation. Requests were stopped, and missing drafts were completed locally from authored tables. Native review remains pending. The heuristic extraction report flags 9 candidates (including dates, numbers, user content and an HTTP header); it is not a complete audit of every user-facing domain error or voice response. Resource coverage and publication approval are separate gates.

## External setup and hardware

The latest read-only ADB inventory found 1 connected device(s). The isolated QA APK passed 10 instrumentation tests on KFTRWI (Android 9, API 28). Coverage includes recipe CRUD/editor recreation, selected named-plan recreation, SQLite reopen/migration, native photo ZIP codecs, a cropped synthetic OCR fixture, Arabic/Hindi resources, denied microphone fallback, opt-in pantry notifications and Arabic planner/native-dialog controls at QA font scale 1.5. See the separate background-alarm probe record for its exact result. This smoke pass does not cover real-photo OCR, granted microphone/Fire TTS, doze/reboot/time changes, TalkBack/full font matrix, the full permission/RTL matrix, a publisher-signed APK or Appstore purchases. Those remain pending. Follow `docs/DEVICE_QA.md` and `docs/AMAZON_BILLING.md` and attach observed evidence before marking those cases passed.

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
