# DishNest

Native Kotlin / Jetpack Compose kitchen companion for Android and intended Fire tablet targets. This repository contains a functional **development preview**, not a submission-ready commercial release.

## Run

Open this folder in Android Studio, select Java 17, install Android SDK 35, and set `sdk.dir` in an untracked `local.properties`. On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease :app:lintDebug
```

The debug APK is installable. Build `:app:assembleQa :app:assembleQaAndroidTest` for the separate DishNest QA package used by [Fire QA](docs/DEVICE_QA.md). Release output is unsigned until the publisher configures their own signing credentials. No signing secrets or developer keys are committed. Core features require Android API 26 or later, no mandatory Google Play Services, and no account.

## Try the app

The first launch is an empty kitchen backed by Room. Save your own recipe, or explicitly choose **Explore a demo kitchen**. Demo content is never silently inserted.

1. Add or edit a recipe with ingredients and one method step per line. Add photos, collections, favourites and personal notes.
2. Assign recipes and servings in the weekly planner. Copy a week, use a template or generate seven dinners from saved recipes.
3. Generate a shopping list. Compatible quantities are combined and compatible pantry quantities deducted. Manual shopping items are preserved when rebuilding.
4. Add pantry quantities and optional check dates. Suggestions show matching and missing ingredient names.
5. Open cooking mode for large steps, TTS, push-to-talk and multiple persistent timers. Touch controls work when speech recognition is unavailable.
6. Import pasted text, permitted public Recipe JSON-LD pages, or an English recipe photo. Every import opens an editable review screen.
7. Export JSON or text in Settings. JSON backups omit photos; the ZIP backup includes privately stored photos (64 MB total limit). Both exclude timers and purchase records. Backup v2 includes named plans and selection; legacy v1 backups migrate to the default plan. Room schema v1 upgrades transactionally to v2 without discarding recipes, notes, meals or templates. Import validates every record and restores only into an empty kitchen under a database transaction.

## Layout

- `domain/`: typed models, ingredient parser, conversions, shopping engine, deterministic planner, voice parser.
- `data/`: Room entities/DAO/converters, validated backup format, recipe metadata extraction, explicit demo fixtures.
- `platform/`: Android timers and notifications, speech/TTS, Amazon purchasing and signed entitlement verification, bounded image decoding, optional local aggregate usage counters.
- `billing-server/`: local Node receipt-verification service, signed responses, policy/HTTP tests and explicitly labelled App Tester fixtures.
- `ui/`: adaptive navigation, dashboard, recipe CRUD, planner, shopping, pantry, cooking, import and settings.
- `tools/`: localisation extraction/validation and optional translation-service client, asset rendering, screenshot composition, static website generation.
- `marketing/`: original assets, source screenshot captions, listing draft and screenshot manifests.
- `website/`: 12 crawlable static English pages; local-only, noindex until a real domain and approved release are configured.

## Verification and limitations

See [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md) for the current scope, evidence and outstanding work. `artifacts/` contains logs, APK copies and real Compose screen renders. JVM screen renders are useful layout and interaction evidence. Physical Fire evidence is recorded in `artifacts/fire-device-qa.json`, with separate background-alarm probe evidence. QA uses `com.dishnest.app.qa` (DishNest QA) and leaves the ordinary app intact. Read [docs/DEVICE_QA.md](docs/DEVICE_QA.md) for the exact covered and pending cases.

Billing uses Amazon Appstore SDK 3.0.9 with purchase initiation, restoration, account checks, pending approval handling, signed receipt verification, expiry/cancellation handling, bounded offline caching and acknowledgement after durable verification. The local server and sandbox configuration are prepared, but the delivered APK keeps purchases disabled because no catalog or trusted TLS verifier exists yet. See [docs/AMAZON_BILLING.md](docs/AMAZON_BILLING.md). Real store integration and device tests remain mandatory; local fixture tests are not purchase evidence. Further commercial feature scope remains in the readiness report.

Twelve locale directories cover 330 translatable string resources and recipe-count plurals. `python tools/localize.py --strict` checks resource coverage, placeholders and plural categories; release approval is a separate field. Remaining domain errors, broader natural-language voice grammar, marketing translations and native-speaker review are tracked in [localisation/REVIEW.md](localisation/REVIEW.md). URL imports check robots policies on each destination with bounded responses and redirects; disallowed or unavailable policies stop automatic imports. Site access permission and copying rights still need individual review.

Public app copy alone was used for draft translation, never private recipes. The bootstrap navigation generator now preserves existing resource files.

No public deployment or store submission has been performed.

## Supporting tools

Generate the local website and listing text, then keep the loopback server running in a separate terminal. This serves files locally; it does not deploy them:

```powershell
python -m pip install -r tools/requirements.txt
python tools/build_website.py
python tools/export_store_listing.py
python -m http.server 8765 --bind 127.0.0.1 --directory website
```

To reproduce the complete local verification and resolved dependency evidence, use another terminal:

```powershell
python tools/compile_readiness_resources.py
python tools/localize.py --strict
python tools/build_evidence.py --begin
.\gradlew.bat --no-daemon --init-script tools/dependency-inventory.init.gradle :app:testDebugUnitTest :app:lintDebug :app:lintQa :app:assembleDebug :app:assembleRelease :app:assembleQa :app:assembleQaAndroidTest :app:writeRuntimeInventory > artifacts/delivery-verification.log 2>&1
node --test --test-reporter=junit billing-server/*.test.mjs > artifacts/billing-server-tests.xml
node tools/verify_billing_contract.mjs
python tools/dependency_inventory.py --fetch-metadata
python tools/build_evidence.py --record
python tools/local_settings_evidence.py
python tools/validate_native_alignment.py
.\tools\validate_apks.ps1
node tools/verify_website.cjs
python tools/website_evidence.py
python -m unittest discover -s tools -p test_website_evidence.py
python tools/validate_assets.py
python tools/submission_readiness.py
python tools/readiness_report.py
python tools/package_deliverables.py
```

The cross-runtime check uses temporary fixture keys/receipts and the actual compiled Kotlin verifier; it does not call Amazon or grant an app entitlement. The source archive excludes development keys, environment secrets, caches and build directories. The preflight intentionally remains not submission-ready while publisher setup, hardware and scope approvals are missing. Packaging rejects stale Android or website evidence; website checks cover all 12 pages at desktop and phone sizes and bind their results to exact source hashes. The paste-ready listing must match its JSON source.

Open `http://127.0.0.1:8765` during website verification. Configure `DISHNEST_NODE_MODULES` and optionally `DISHNEST_BROWSER_EXECUTABLE` for your installed Playwright runtime; use `DISHNEST_QA_DIR` for screenshots outside the repository. Stop the loopback server after verification.

Publisher signing is optional and reads `DISHNEST_KEYSTORE`, `DISHNEST_KEY_ALIAS`, `DISHNEST_STORE_PASSWORD`, and `DISHNEST_KEY_PASSWORD` from the process environment. Provide all four or none; keep the publisher keystore outside this repository. With none, release remains unsigned. Signing does not upload or submit anything. The local deliverable pipeline requires an unsigned verification build and current input/output hashes (`python tools/build_evidence.py --begin` immediately before the full Gradle build, then `--record` after success). Run `python tools/build_evidence.py` to reject stale evidence. Source pushes are authorized; app deployment is not authorized, and store submission is conditionally authorized only after every required readiness gate passes.

Local Fire/device smoke instructions and the exact tested APK checksum are in [docs/DEVICE_QA.md](docs/DEVICE_QA.md). Keep the device unlocked during UI tests; the tool resets only disposable QA fixtures and leaves the ordinary app's kitchen intact. Generated APKs/source archives and private development configuration remain local and are not committed to Git.

Additional scope: named active plans, Premium combined-plan shopping, optional daily pantry check-date reminders, an adjustable OCR crop preview, and audio-focus-aware narration. Reminder delivery is approximate and subject to device power/notification settings. The QA variant always disables purchases; its instrumentation resets only its disposable kitchen.

Amazon console preparation: descriptions, search terms, category, free installation with Premium upgrades, support details, all-region adult audience, rating questionnaire and draft assets have been entered. See [saved copy](marketing/listing-en-US.txt). Private LAT builds have been uploaded and submitted with the publisher's specific certification. Public submission remains on hold; privacy, export certification for the eventual public submission and all required release gates remain separate.

Listing localization: the console has complete description drafts for en-US, en-GB, de-DE, es-ES, fr-FR, it-IT, nl-NL, pl-PL, pt-BR and pt-PT. All nine additions were checked against their local JSON source after a reload, including titles, descriptions, feature bullets and search terms. Arabic and Hindi description slots are not offered. Visual drafts now cover all 12 app locales: 96 tablet screenshots and 12 promotional images with actual translated Compose screens, sample recipes and captions. Amazon artwork upload slots cover seven of those locales; see [upload evidence](marketing/amazon-artwork-uploads.json) and [visual manifest](marketing/screenshots/localized/manifest.json). Native-language and final hardware approval remain pending. App resource coverage and the console's supported-app-language declaration are separate from translated listing copy. Run `python tools/export_store_listing.py` and `python tools/validate_assets.py` to refresh and validate every local listing.

To reproduce localized visual drafts, run `python tools/prepare_visual_copy.py`, the `LocalizedScreenshotsTest` host suite, then `python tools/prepare_visual_renderer.py`. Serve only `build/visual-localization` on loopback and use the visible Export PNG links in its browser UI. It shapes Arabic and Hindi text and exports exact store dimensions independently of viewport scaling. Run `python tools/verify_visual_localization.py` after recording the export manifest. Sample translations are test-only; no user recipes or databases are translated or uploaded. Host captures are not physical-device screenshots or release approval.

The visual export manifest binds every PNG to its exact icon, renderer, captions and Compose capture. In the Amazon console, wait until image validation finishes and the saved thumbnail appears before editing another asset or changing locale. A completed file picker is not a completed upload. Reload and verify all eight screenshots, both icon sizes and the promotional image for each offered artwork locale before recording persisted upload evidence.

### Local language and usage preferences

Settings offers the existing 12 translated app languages or the device language. Android 13 and later use the system per-app language selection; older versions persist the choice locally. Recipes and canonical dates are not translated or rewritten. Receivers and success/error messages use the selected language too. Seven additional priority language bundles and native reviews remain pending.

Usage counts stay off by default and have no network destination. Re-selecting enabled consent preserves the cohort/counters; opting out clears them. First-use, exact day-7/day-30 return and Premium conversion are once per consent period; engagement counts saturate at the integer limit. Premium conversion requires a successful purchase callback followed by verified access in the production profile; restore, sandbox and LAT do not count as a paid conversion. The trial event is reserved and not emitted because no trial is configured. No identifiers or recipe content are stored in usage counters. These local counts are not a revenue dashboard or a claim of tested production billing.

The usage switch exposes its translated accessible name. A real host regression failed before that fix and passed afterward. Six current-APK Fire settings checks pass: French/Arabic cold-start persistence, localized dialog cancellation, unchanged QA kitchen during language changes, device-language reset, opt-in persistence, and deletion-driven opt-out reflected by the actual switch. Its accessible name was observed in English, French and Arabic. `python tools/run_fire_settings_checks.py` repeats these checks only in the disposable QA app, verifies ordinary APK/private-file hashes and restores the language/keep-awake settings. Evidence is `artifacts/fire-settings-qa.json`; full TalkBack, other locales and native-language approval remain separate.

Artwork verification reports `local_assets_passed` separately from `amazon_uploads_match_current_assets`. Refreshing local captures/exports does not rewrite historical upload receipts or imply an Amazon upload. The validator leaves its aggregate result false and exits nonzero while console assets differ; synchronize them only in a separately authorized Amazon session. Native-language, physical-device and release approvals remain false for host artwork.

30 October artwork sync: all seven offered Amazon draft locales were updated, reloaded and checked byte-for-byte against the current 56 screenshots, 14 icons and seven promotional images. Screenshot order is 01–08 in every locale. [Saved browser evidence](artifacts/amazon-artwork-sync-20261030.json) binds the current receipt, visual manifest and proof screenshot; prior receipts remain in `marketing/history/`. This is draft preparation, not public submission, native-language approval or final hardware artwork approval. Amazon support case 22703564641 remains pending Amazon action with no reply after the 29 October publisher response.

The host settings proof is reproducible with `python tools/local_settings_evidence.py` after the complete build/provenance command above. It requires all ten named language/privacy cases, fresh French/Arabic captures and exact current build hashes; `--verify` rejects stale evidence. Kitchen deletion immediately opts out of local counts, and the settings switch observes the canonical preference instead of retaining stale UI state. This does not replace Fire or production-purchase QA.

The later 30 October accessibility build has separate current evidence in `artifacts/fire-accessibility-readiness-20261030.json`. Its ordinary app was neither launched nor replaced. Normal, blocked-notification and forced-idle timer probes pass on this APK. The full document-provider rerun remains pending after Fire hierarchy/picker failures; earlier APK passes are not reused. The seven broad release categories remain pending, and public submission is held.
