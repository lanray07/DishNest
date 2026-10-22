# DishNest submission-readiness report

Status: **DEVELOPMENT PREVIEW — NOT READY TO SUBMIT**. Evidence refreshed: 22 October 2026. **App deployment is not authorized. Store submission is conditionally authorized only after every required readiness gate passes; the current build must not be submitted.** Source commits and GitHub pushes are authorized. The approved receipt verifier alone was deployed on Workers Free. No public app release or website deployment was performed; private LAT testing was explicitly authorized.

The remaining release categories, their local work and the evidence needed to close them are tracked in [PENDING_RELEASE_GATES.md](PENDING_RELEASE_GATES.md).

## Completed local continuation

- Filled the missing translation entries without suppressions or a lint baseline. All 12 requested locales now cover 377 translatable strings plus recipe-count plurals. Additional static titles, callbacks and accessibility labels were extracted. Brand names remain unchanged; private recipes were not translated.
- Implemented Amazon purchase initiation, product-price display, pending approval, restoration pagination, account confirmation, receipt persistence, signed server verification, expiry/cancellation handling, bounded offline caching and acknowledgement after verification. The default APK keeps purchases disabled. Approved UK/US prices and localized catalog drafts are now saved; lifetime is the first and primary offer.
- Deployed explicitly approved sandbox and production receipt verifiers to the authenticated Workers Free account, with separate protected keys, no paid bindings and application logging disabled. Local workerd tests and live signed inactive RVS responses pass, including verification by compiled Kotlin and wrong-account rejection. Real purchase/lifecycle QA, load/quota recovery and release approvals remain pending; see `AMAZON_FREE_VERIFIER.md`.
- Saved approved monthly/annual/lifetime UK and US prices in Amazon, with seven localized IAP descriptions. The lifetime-first UI uses Amazon-returned prices. Installed the real console-issued public authentication key; secrets remain outside Git and APKs.
- Added the loopback receipt service, development key/configuration preparation, App Tester fixtures and documented catalog contract. Server-only RVS secrets and private signing keys are excluded from the app and source archive. Enabled builds require Amazon's real app-specific authentication public key as well as the separate verifier public key and HTTPS endpoint.
- Added transactional backup snapshots and empty-kitchen restore guards; bounded photo-inclusive ZIP export/import with rollback cleanup; shared-reference-aware photo deletion and aged draft cleanup; combined recipe filters and direct persisted meal swaps. Fixed complete-command voice matching across all target language families and extracted additional dynamic labels, notifications and speech-service messages into locally authored resources.
- Added named active meal plans, Premium multi-plan shopping, a data-preserving Room v1-to-v2 migration and compatible v1/v2 backups and demo loading into the persisted selected plan, including after default-plan deletion; opt-in generic pantry check-date reminders; an adjustable offline OCR crop preview; audio-focus interruption handling and serialized timer callbacks.
- Added per-destination robots policy checks with bounded HTTPS requests and redirect handling, plus offline policy/transport fixtures. Robots policies do not establish copying rights; live-site and hardware network testing remain pending.
- Configured GitHub project/support/draft privacy URLs and synchronised the local website privacy disclosure with the repository policy. Browser evidence now binds all website inputs and both complete viewport runs.
- Added a separate billing-disabled DishNest QA package so disposable test fixtures cannot reset the ordinary app.
- Added an isolated sandbox purchase-test package and corrected licensing/purchasing lifecycle order. Actual App Tester lifetime purchase and current restore/restart checks passed; an existing account without active access, canceled-checkout and expired-subscription denial passed. Synthetic subscription expiry still blocks subscription approval. Original catalog/routing/settings and ordinary app data were preserved. See `AMAZON_PURCHASE_QA.md`.
- Added 13 Node-to-compiled-Kotlin subscription/profile contract cases and 11 LAT Worker lifecycle fixtures, including production RVS routing, grace/refund/renewal and outage recovery. Prepared a separately signed billing-enabled LAT candidate in an isolated output tree, with a distinct protected verifier key on Workers Free. Private LAT Test 1 is in progress. The Amazon-delivered Fire release passed a monthly purchase, verified Premium, restart/restore and autorenew-off access checks during the authorized temporary test. Full lifecycle coverage remains separate from these fixture checks. See `AMAZON_LAT.md`.
- Prepared optional environment-only publisher signing and build input/output provenance checks, with credentials and generated packages excluded from Git.
- Created the explicitly approved permanent publisher key in an ACL-protected, Git-ignored directory. Signed the current verified release APK and validated certificate, unchanged payload and 16 KB ZIP alignment; portable backup and exact signed-release hardware QA remain pending. See `PUBLISHER_SIGNING.md`.
- Added EXIF-aware, bounded photo decoding; explicit backup exclusions for legacy and modern Android; density-independent food images; an offline third-party notices viewer; resolved dependency inventory and a repeatable preflight.

- Added shared Unicode quantity parsing for ingredients and pantry/shopping forms. Ambiguous grouped amounts and invalid fractions remain reviewable instead of changing quantities; common translated metric units consolidate correctly without converting cups or changing ingredient names.
- Added 46 authored runtime messages and labels in every app locale. Stable domain failure codes now resolve to localized messages; unexpected provider exception text is not shown. Shopping categories, default difficulty labels, quantities and exported headings use the active resource locale while preserving user text and parseable export unit codes. Ingredient-quantity voice questions cover all supported language families; ambiguous ingredient matches no longer announce an arbitrary amount. Enabled purchase builds use actual purchase guidance instead of development-preview wording.

- Localized integer totals, serving controls, step numbers, calendar days and date headings using the active resource locale. Pantry invalid-date validation uses the existing translated resource. Stored dates, quantities and user text are unchanged. Screenshot fixtures wait for reset completion while advancing Robolectric's paused main loop; the phone demo flow no longer assumes Monday's hero recipe every day.

- Localized generated duplicate-recipe titles in every app locale. Meal-swap choices and pantry check-date reminders format valid stored dates for the resource locale while retaining invalid legacy/user values verbatim. Recipe content and canonical stored dates remain unchanged; duplicated recipes have a separate identity and an editable translated suffix.

## Executed verification

| Check | Result | Evidence |
| --- | --- | --- |
| Android host tests | 96 tests; 0 failures/errors | `app/build/test-results/testDebugUnitTest/` |
| Android lint | 0 errors; 47 warnings | `app/build/reports/lint-results-debug.html` |
| Original translation errors | 0 remain | Same lint report; no suppression/baseline |
| Current build provenance | PASS: current input/output hashes verified | `artifacts/build-provenance.json` |
| Locale resource gate | PASS across 12 locales | `localisation/validation.json` |
| Physical Fire smoke tests | 15 tests passed on KFTRWI / API 28 | `artifacts/fire-device-qa.json`, `artifacts/device-instrumentation.txt` |
| Physical Fire native checks | 6 passed on current APK; ordinary data hashes unchanged; UI not exercised | `artifacts/fire-native-qa.json`, `artifacts/fire-native-instrumentation.txt` |
| Forced-doze timer | Passed on the exact current QA app and runner; timer handled before verifier restart while still IDLE; original data unchanged; broader device matrix pending | `artifacts/fire-doze-qa.json` |
| Fire offline and process recovery | PASS: 15 smoke tests plus named-plan check without connectivity; saved kitchen/preferences unchanged after process restart; radio/display settings and ordinary data preserved | `artifacts/fire-offline-qa.json` |
| Fire system text scale and orientation | PASS: recipe, pantry and named-plan flows in portrait and landscape at system font scale 2.0; settings restored and ordinary data unchanged; TalkBack/full dialog matrix pending | `artifacts/fire-layout-qa.json` |
| Fire document-provider backups | PASS: actual local picker JSON/ZIP cancellation, export, nonempty-kitchen rejection and complete backup-record round trips; ordinary data/settings preserved; provider failures/real attachments pending | `artifacts/fire-document-qa.json` |
| Offline notice integrity | 10 original notices and 92 dependency hashes verified; exact asset in debug/QA/release APKs; legal approval pending | `artifacts/packaged-notices-verification.json` |
| Fire background timer probe | PASS: saved kitchen records, handled timer and own notification after QA process termination | `artifacts/fire-background-alarm.json` |
| Fire blocked-notification timer probe | PASS: expired timer and kitchen state retained, no notification posted, QA notification setting restored | `artifacts/fire-blocked-notifications.json` |
| Receipt service | 15 passing policy and local HTTP tests | `artifacts/billing-server-tests.xml` |
| Workers Free verifier | Free plan verified; sandbox/production deployed with approval; signed inactive live-RVS responses accepted by Kotlin and wrong account rejected; real purchases not tested | `artifacts/billing-deployment.json`, `artifacts/billing-live-contract.json` |
| Cross-runtime receipt contract | Actual Node signer and compiled Kotlin verifier pass; wrong-account response rejected | `artifacts/billing-contract.json` |
| Isolated App Tester | Lifetime purchase, restore/restart and denial checks observed; subscription checkouts completed but expiry/lifecycle approval pending | `artifacts/amazon-purchase-device.json`, `artifacts/purchase-test-restoration.json` |
| Configuration guard | Enabled billing rejects missing HTTPS verifier configuration even with the real Amazon public key present | `artifacts/billing-configuration-guard.json` |
| Resolved dependency inventory | 92 artifacts; 0 missing licence declarations | `docs/dependencies/runtime-inventory.json` |
| Native binary and APK alignment/signature | See exact packaged APK evidence and checksums | `artifacts/native-alignment.json`, `artifacts/apk-validation.json`, delivery manifest |
| Website | PASS: all 12 current pages at desktop 1440×1000 and phone 390×844; GitHub links, identity, content, FAQ, console, images and overflow checked | `artifacts/website-verification.json` |
| Assets/listing dimensions | Local validation passes; all store outputs remain drafts | `artifacts/asset-validation.json` |

The Android host suite covers ingredient/quantity logic, planner/backup/voice/timer policies, Room persistence, entitlement signatures and clock handling, photo orientation, tablet/phone UI flows, empty launch, recipe editing across activity recreation and Arabic navigation/RTL. The native bitmap tests verify decoding mechanics; they do not verify OCR recognition accuracy. See individual XML suites for the authoritative cases.

Amazon's obfuscated SDK emits D8 stack-map warnings. Billing-disabled debug manifests exclude the IAP and DRM response receivers for host-JVM tests. Enabled debug and release manifests retain the required receivers; Amazon package visibility is declared. App Tester callbacks and signed sandbox lifetime receipts were exercised in a separate package; production remains untested; actual monthly LAT grant/restart/restore and cancellation observations are recorded in `artifacts/fire-amazon-lat-qa.json`, with the full lifecycle matrix still pending. The API-28 test receiver compatibility rule is isolated from release. No JVM bytecode verifier is disabled.

Android lint warnings remain visible. Dependency-update warnings are not a compatibility or security certification. Any warning changes should be reviewed against the exact latest report.

## Remaining implementation and verification

| Area | Implemented | Remaining |
| --- | --- | --- |
| Foundation | Compose, Room, DataStore, Flow, adaptive navigation | Hardware performance, full process/rotation matrix, TalkBack and system font scaling |
| Recipes | CRUD, favourites, collections, photos, notes, combined search/collection/favourite/time/cuisine/tag/difficulty filters, sorting and shared photo cleanup | Full hardware accessibility review and additional vocabulary |
| Planner | Seven days/four slots, servings, tap assignment, previous-week copy, templates, direct two-meal swap, named selected plans and offline dinner suggestions | Optional drag interactions and broader planning QA |
| Shopping/pantry | Compatible consolidation, serving scaling, pantry deduction, manual items, check dates, optional daily reminders, combined-plan shopping, localized numeric quantity entry and common metric aliases, and name-based suggestions | Expanded vocabulary/unit parsing and native review; remaining advanced Premium scope |
| Cooking/voice/timers | Touch fallback, narration with audio-focus handling, push-to-talk, persisted inexact timers with stale-callback protection, exact commands in all target language families and localised numeric timer grammar (English number words also supported) | Broader natural-language/quantity grammar; actual speech providers, audio focus, natural idle/quotas, reboot, permissions and clock-change device QA |
| Import/data | Reviewed text/JSON-LD with per-destination robots policy checks, local English OCR with crop preview, EXIF handling, transactional JSON and bounded photo-inclusive ZIP backups and local export, multilingual pasted-text section headings and Unicode numeric quantities | Live-site permission/robots interoperability validation and real-photo OCR accuracy; cloud provider remains optional/unconnected |
| Billing | SDK integration, TEST Approved catalog, lifetime-first UI, Free-plan verifiers, real App Tester lifetime checks, and actual Amazon-delivered monthly LAT grant/restart/restore and autorenew-off observations | Full renewal/expiry/annual/lifetime/refund/fulfilment LAT matrix, final market/terms review, load/quota recovery and further commercial scope |
| Localisation | 377 strings and plurals in 12 app locales; technical checks pass; 10 Amazon description locales; 96 localized tablet screenshot compositions and 12 promotional images with translated sample data and captions | Native-speaker review, remaining inline interpolations, expanded voice/unit vocabulary and runtime-copy review, translated website, final listing approval and hardware RTL checks; Amazon artwork slots cover fewer locales than descriptions; see marketing/amazon-artwork-uploads.json |
| Licensing/privacy | Original notices bundled; all resolved POM licences inventoried; backup exclusions and billing disclosures | Publisher O. Bankole confirmed; legal/licence/privacy approval, production service-operation and retention details and final dependency review |
| Marketing/site | Original artwork, host renders, inspected Fire QA captures, local static site and browser checks | Legal/market approval, translated pages, final store URL, approved hardware screenshots and final listing approval; 10 localized console descriptions, 7 artwork sets, support email and GitHub privacy/deletion links are configured without website deployment |
| Release | Permanent publisher key created with approval; verified publisher-signed APK, input/output hashes and binary validation | Secure portable signing-key backup and broader release hardware matrix. Amazon-delivered version 2 launch/smoke/monthly purchases were exercised through an explicitly authorized temporary original-app backup/restore session; see `fire-amazon-lat-qa.json` for rollback completion. |
| Metrics | Optional aggregate counters stored only on device; disabled by default | No analytics provider or complete Premium/trial conversion reporting |

Some commercial feature scope remains unfinished; local receipt policy tests do not make the entire Premium product complete. Existing content remains readable/editable after entitlement expiry. JSON recovery does not discard a former Premium user's larger recipe book. A demo only bypasses Premium gates in billing-disabled development builds.

The translation provider throttled the initial draft generation. Requests were stopped, and missing drafts were completed locally from authored tables. Native review remains pending. The heuristic extraction report flags 6 candidates (including formatted values and user content; HTTP header API names are excluded and error assignments are now scanned); it is not a complete audit of every user-facing domain error or voice response. Resource coverage and publication approval are separate gates.

## External setup and hardware

The latest read-only ADB inventory found 1 connected device(s). The isolated QA APK passed 15 instrumentation tests on KFTRWI (Android 9, API 28). Coverage includes recipe CRUD/editor recreation, selected named-plan recreation, SQLite reopen/migration, native photo ZIP codecs, a cropped synthetic OCR fixture, Arabic/Hindi resources, localized pasted-recipe headings/Unicode step numbering, pantry entry with Arabic decimal digits and metric aliases, denied microphone fallback, opt-in pantry notifications and Arabic planner/native-dialog controls at QA font scale 1.5, translated duplicate titles with original-recipe integrity, and localized meal-swap labels with canonical persisted dates. See the separate background-alarm probe record for its exact result. This smoke pass does not cover real-photo OCR, granted microphone/Fire TTS, doze/reboot/time changes, TalkBack/full font matrix, the full permission/RTL matrix, the complete release/purchase matrix. A separate temporary session tested the actual Amazon-delivered APK and monthly purchases; see `artifacts/fire-amazon-lat-qa.json`. Broader scenarios remain pending. Follow `docs/DEVICE_QA.md` and `docs/AMAZON_BILLING.md` and attach observed evidence before marking those cases passed.

An unsubmitted Amazon app draft now exists; see `marketing/amazon-console-draft.json`. The publisher has identified themself as O. Bankole. The real Amazon public key, approved UK/US prices, localized IAP drafts, protected publisher signing key and separate protected Workers Free verifiers are now prepared. Required release inputs remain final catalog/market/terms validation, real purchase/lifecycle and quota-recovery QA, signing-key recovery backup, exact signed-release hardware QA, native language approval and approved privacy/operator details. Private support/deletion assistance uses the publisher-provided support email. Project, support and draft privacy/deletion links use GitHub; no domain or GitHub Pages deployment was configured. Local development keys are not production keys.

## Checklist

- [x] Remove the 141 MissingTranslation errors and run the resource coverage gate.
- [x] Implement and test local receipt policy, signatures, expiry/cancellation rules and HTTP contract.
- [x] Prepare catalog/App Tester fixtures and local development service configuration.
- [x] Add privacy backup exclusions, EXIF handling, original notices and dependency inventory.
- [x] Run local Android builds/tests/lint and desktop/phone website checks; retain actual reports.
- [ ] Finish the remaining app scope above and approve every target locale.
- [x] Save approved UK/US prices and seven localized IAP drafts, install the real Amazon public key, and deploy the explicitly approved Free-plan test/production verifiers.
- [x] Resolve the catalog/detail status conflict, start private LAT, and observe actual monthly grant/restart/restore and autorenew-off behavior.
- [ ] Complete the remaining actual LAT renewal/expiry/annual/lifetime/refund/fulfilment matrix.
- [x] Submit the separately certified new-icon private Test 2 with accelerated subscriptions; Test 2 is active and its distinct Amazon APK was acquired and verified on the Fire. Reset, clean test data and restart still restore the old monthly receipt; actual annual/lifetime/expiry checks remain blocked. Exact original-app/data rollback and SQLite integrity passed. See `artifacts/fire-test2-qa.json`. See `artifacts/amazon-fresh-lat-console.json`.
- [ ] Execute the hardware/accessibility/offline/permissions/timer/OCR matrix.
- [ ] Approve licensing, privacy disclosures, production service-operation details, remaining marketplace prices/terms and final store/site metadata.
- [x] Create the authorized permanent publisher key and verify the signed APK signature, unchanged payload and alignment.
- [ ] Verify secure key recovery backup, install/test the exact release APK and approve final hardware screenshots.
- [ ] Clear all required readiness gates before acting on the user's conditional permission to submit. App deployment beyond store submission is not authorized. Source commits and GitHub pushes are authorized.

## Primary references checked

- [Amazon listing and image requirements](https://developer.amazon.com/docs/app-submission/appstore-details.html): draft dimensions/copy constraints were checked locally; confirm the live console before eventual submission.
- [Appstore SDK integration and app-specific public key](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html).
- [Amazon IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html) and [RVS semantics](https://developer.amazon.com/docs/in-app-purchasing/iap-rvs-for-android-apps.html).
- [App Tester setup](https://developer.amazon.com/docs/in-app-purchasing/iap-install-and-configure-app-tester.html) and [testing overview](https://developer.amazon.com/docs/in-app-purchasing/iap-testing-overview.html).
- [Amazon Android mobile distribution change](https://developer.amazon.com/apps-and-games/blogs/2025/02/upcoming-changes-to-amazon-appstore-for-android-devices-and-coins-program): Fire support continues; ordinary Android phones need a separate distribution strategy.
