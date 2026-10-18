# DishNest submission-readiness report

Status: **DEVELOPMENT PREVIEW — NOT READY TO SUBMIT**. Evidence date: 18 October 2026. **Publication is prohibited by the user's current instruction.** No store submission, remote deployment or Git push was performed.

## Completed local continuation

- Filled the missing translation entries without suppressions or a lint baseline. All 12 requested locales now cover 262 translatable strings plus recipe-count plurals. Additional static titles, callbacks and accessibility labels were extracted. Brand names remain unchanged; private recipes were not translated.
- Implemented Amazon purchase initiation, product-price display, pending approval, restoration pagination, account confirmation, receipt persistence, signed server verification, expiry/cancellation handling, bounded offline caching and acknowledgement after verification. The default APK keeps purchases disabled. The publisher confirmed there is no existing catalog or verifier and requested local preparation only.
- Added the loopback receipt service, development key/configuration preparation, App Tester fixtures and documented catalog contract. Server-only RVS secrets and private signing keys are excluded from the app and source archive. Enabled builds require Amazon's real app-specific authentication public key as well as the separate verifier public key and HTTPS endpoint.
- Added EXIF-aware, bounded photo decoding; explicit backup exclusions for legacy and modern Android; density-independent food images; an offline third-party notices viewer; resolved dependency inventory and a repeatable preflight.

## Executed verification

| Check | Result | Evidence |
| --- | --- | --- |
| Android host tests | 43 tests; 0 failures/errors | `app/build/test-results/testDebugUnitTest/` |
| Android lint | 0 errors; 44 warnings | `app/build/reports/lint-results-debug.html` |
| Original translation errors | 0 remain | Same lint report; no suppression/baseline |
| Locale resource gate | PASS across 12 locales | `localisation/validation.json` |
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
| Foundation | Compose, Room, DataStore, Flow, adaptive navigation | Hardware performance, process death, TalkBack, large fonts and full rotation QA |
| Recipes | CRUD, favourites, collections, photos, notes, search and quick filter | Broader filters, orphan-photo lifecycle and full accessibility review |
| Planner | Seven days/four slots, servings, tap assignment, previous-week copy, templates and offline dinner suggestions | Separately named active plans, direct two-meal swap and optional drag interactions |
| Shopping/pantry | Compatible consolidation, serving scaling, pantry deduction, manual items, check dates and name-based suggestions | Broader vocabulary and locale-specific units; scheduled pantry reminders; advanced Premium scope |
| Cooking/voice/timers | Touch fallback, step narration, push-to-talk, independent persisted inexact timers | Full locale dictionaries/responses; actual speech providers, audio focus, doze, reboot, permissions and clock-change device QA |
| Import/data | Reviewed text/JSON-LD, local English OCR, EXIF handling, bounded transactional backups and local export | Live-site access-policy/robots validation, cropping and real-photo OCR accuracy; photo-inclusive backup; cloud provider remains optional/unconnected |
| Billing | Local SDK integration and signed RVS service with tested policies | Amazon console key/catalog, approved terms/prices/countries, trusted TLS service, production credentials, App Tester/LAT and full callback/fulfilment QA; further commercial gates/features |
| Localisation | 262 strings and plurals in 12 locales; technical checks pass | Native-speaker review, remaining inline interpolations and domain/status errors, complete voice/category/unit work, localised website/store/screenshot copy and hardware RTL checks |
| Licensing/privacy | Original notices bundled; all resolved POM licences inventoried; backup exclusions and billing disclosures | Publisher legal/licence/privacy approval, operator identity, retention/contact details and final dependency review |
| Marketing/site | Original artwork, draft host-rendered screenshots, local static site and browser checks | Hardware captures, market approvals, real domain/canonical/hreflang/contact/privacy/store URLs and final listing approval |
| Release | Debug APK and unsigned release build; local validation tooling | Publisher signing material, exact signed-APK install/smoke test and actual store compatibility checks |
| Metrics | Optional aggregate counters stored only on device; disabled by default | No analytics provider or complete Premium/trial conversion reporting |

Some commercial feature scope remains unfinished; local receipt policy tests do not make the entire Premium product complete. Existing content remains readable/editable after entitlement expiry. JSON recovery does not discard a former Premium user's larger recipe book. A demo only bypasses Premium gates in billing-disabled development builds.

The translation provider throttled the initial draft generation. Requests were stopped, and missing drafts were completed locally from authored tables. Native review remains pending. The heuristic extraction report still flags 19 candidate inline strings; it is not a complete audit of every user-facing domain error or voice response. Resource coverage and publication approval are separate gates.

## External setup and hardware

No connected device was found in the latest read-only ADB inventory. The earlier local emulator attempts could not provide a working accelerated emulator; compiled instrumentation tests are **not executed**. Physical Fire/Android, actual microphone/TTS providers, alarm delivery, OCR accuracy and Appstore testing remain unverified. Follow `docs/DEVICE_QA.md` and `docs/AMAZON_BILLING.md` and attach observed evidence before marking those cases passed.

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
- [ ] Obtain a new explicit instruction before any publication. Current instruction remains **do not publish anything**.

## Primary references checked

- [Amazon listing and image requirements](https://developer.amazon.com/docs/app-submission/appstore-details.html): draft dimensions/copy constraints were checked locally; confirm the live console before eventual submission.
- [Appstore SDK integration and app-specific public key](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).
- [Amazon IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html) and [RVS semantics](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
- [App Tester setup](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html) and [testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
- [Amazon Android mobile distribution change](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program): Fire support continues; ordinary Android phones need a separate distribution strategy.
