# Execution milestones

| Milestone | Main files / deliverables | Verification / blockers |
| --- | --- | --- |
| Repository and baseline | Root Gradle Kotlin DSL, wrapper, manifest, GitHub-connected source repository | Java 17 / SDK 35 installed; wrapper and debug build executed. Preserve the untracked `local.properties` on this machine |
| Native foundation | `DishNestApp.kt`, `KitchenViewModel.kt`, `data/Database.kt`, `ui/Theme.kt`, `ui/KitchenApp.kt` | Room schema exported; first-launch kitchen empty; adaptive phone/tablet UI rendered |
| Recipes and import | `ui/RecipeScreens.kt`, `ui/PhotoImport.kt`, `data/RecipeImport.kt`, `data/BackupCodec.kt` | Domain parsing/validation and Room tests; manual create/edit/delete UI test. OCR accuracy and live-site import still need device/network QA |
| Plans, shopping and pantry | `domain/Models.kt`, `ui/PlanningScreens.kt` | Conversion, serving scaling, separation of incompatible forms, deductions, deterministic filters and missing-ingredient unit tests |
| Cooking, voice and timers | `platform/VoiceController.kt`, `platform/Timers.kt`, `domain/VoiceCommands.kt` | Parser/timer tests and cooking UI navigation; real microphone, doze, reboot and notification testing pending |
| Commercial integration | `platform/AmazonBilling.kt`, `Entitlements.kt`, `EntitlementClock.kt`, `RestoreGuard.kt`, `data/RecipeWriter.kt`, `billing-server/`, Premium dialog | Local SDK/verification integration, signed/cache/restore-state/clock policies and atomic recipe creation cap implemented and tested. Catalog, console key, trusted operated verifier, App Tester/LAT and further commercial scope remain pending; delivery purchases disabled |
| Localisation | `localisation/`, extraction/assembly/validation tools, 12 draft locale directories | 307 string resources and recipe plurals covered; technical gate passes and the 141 MissingTranslation errors are resolved. Full UI/voice/marketing and native approval remain pending |
| Visual assets and store drafts | `marketing/assets/`, `marketing/screenshots/`, `marketing/listing-en-GB.json` | Original visuals; 16 dimension-checked compositions from real production Compose renders. Human and hardware approval pending |
| Website | `website/`, `tools/build_website.py`, `tools/verify_website.cjs` | Desktop and phone browser checks passed; no public deployment. Canonical domain and translated pages pending |
| Packaging and readiness | `tools/package_deliverables.py`, `docs/RELEASE_READINESS.md` | Installable debug APK, unsigned release APK and source ZIP. Exact final test/lint evidence is recorded in `artifacts/qa-summary.json` and Gradle logs |

Executed Gradle targets include `wrapper`, `:app:assembleDebug`, `:app:assembleRelease`, `:app:testDebugUnitTest`, `:app:lintDebug` and `:app:assembleDebugAndroidTest`. A target being listed here does not mean it passed: consult the final logs and report. Device instrumentation could not execute without a working emulator or connected device.

The next release priorities are the explicitly recorded remaining product scope, publisher-owned Amazon/verifier configuration, native language approval, physical Fire/accessibility tests and approved hardware screenshots. Current authoritative evidence and blockers are generated in `docs/RELEASE_READINESS.md` and `artifacts/submission-readiness.json`. Publication and public website deployment are prohibited by the current user instruction.

Local readiness continuation adds photo-inclusive ZIP backups, transactional restore/export, reference-aware photo cleanup, combined recipe filters, persisted meal swaps, exact voice command dictionaries and environment-only signing preparation. Source commits and GitHub pushes are authorized; app deployment and store submission remain prohibited. See the generated readiness report for executed evidence and remaining scope.
