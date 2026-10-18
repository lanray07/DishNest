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

Amazon console preparation: the English (U.S.) descriptions, search terms, category, free pricing, support details, all-region adult audience, rating questionnaire and draft image assets have been entered. See [draft status](marketing/amazon-console-draft.json) and [saved copy](marketing/listing-en-US.txt). Privacy answers are saved for publisher review. No APK has been uploaded; export-compliance certification and all release gates remain pending. The app has not been submitted.

Listing localization: the console now has complete description drafts for en-US, en-GB, de-DE, es-ES, fr-FR, it-IT, nl-NL, pl-PL, pt-BR and pt-PT. All nine additions were checked against their local JSON source after a reload, including titles, descriptions, feature bullets and search terms. Arabic and Hindi are not offered in the console's localization dialog. Native-language approval and localized screenshots remain pending; English artwork is the current fallback. App resource coverage and the console's supported-app-language declaration are separate from translated listing copy. Run `python tools/export_store_listing.py` and `python tools/validate_assets.py` to refresh and validate every local listing.
