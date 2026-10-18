# Device QA record

Automated host rendering is useful evidence but does not exercise Fire hardware, Appstore communication, installed speech providers, native OCR accuracy, AlarmManager delivery or TalkBack. Only the targeted Fire smoke results below are passed; the full hardware matrix remains incomplete.

The visual-localization continuation rebuilt the app with locale-aware date and shopping-contribution formatting. The Fire tablet was disconnected at the final check, so the retained smoke and background-timer results refer to an earlier APK. They must be rerun against the current QA package before submission; the readiness report checks both APK hashes and does not treat historical results as current passes. The 12-locale screenshot suite is host evidence only.

## Devices and evidence

Record model, Fire OS/Android version, API, locale, font scale, app version, APK checksum, test date and tester. Run the compiled instrumentation APK only on an explicitly selected connected device. A KFTRWI Fire tablet running Android 9/API 28 is now connected; the earlier emulator attempts did not provide a working accelerated emulator. Recheck with `tools/collect_device_evidence.ps1` before testing. That script is read-only and does not install, submit or publish anything.

| Scenario | Steps and expected outcome | Status |
| --- | --- | --- |
| Fresh install / offline | Start without internet; kitchen is empty, manual recipe CRUD, planner, pantry and shopping work | Empty launch and recipe CRUD passed on Fire; airplane-mode and full offline flow pending |
| Persistence / process death | Save content and an active timer; terminate the background QA process and relaunch; saved content remains (force-stop alarm behaviour is separate) | SQLite close/reopen and v1-to-v2 migration passed; see the separate background timer probe for process termination. Full recipe/process recovery remains pending |
| Rotation / window resizing | Rotate every dialog and cooking screen; no clipped controls or unexpected data loss | Recipe draft and selected plan survived activity recreation on Fire; full physical rotation/resizing matrix pending |
| Large text / TalkBack | Test largest supported font and display scaling, focus order, labels, buttons, scrollable dialogs and live timer announcements | Arabic planner/dialog controls passed in a QA activity at 1.5 font scale; full system scaling and TalkBack pending |
| Arabic / RTL | Test navigation, dialogs, mixed recipe text, numbers, units, currency and long labels | Arabic planner and native dialog labels passed with the QA activity configured RTL; full mixed-content/locale matrix pending |
| Microphone denied / absent provider | Deny permission, revoke it in settings, test without recognition provider; touch controls always remain usable | Denied microphone returned the touch fallback on Fire without starting recognition; provider absence/granted permission pending |
| Voice and narration | Test locale commands, repeated commands, interruption, screen lock, incoming audio and unavailable TTS language | Not run on hardware |
| Notifications denied | Deny notification access; in-app timer state remains visible; no false promise of background alerts | Not run on hardware |
| Timer background / doze / reboot | Pause/resume/cancel multiple timers; background, sleep, reboot, change wall clock; record actual inexact delivery behaviour | Background timer delivery after QA process termination passed with own notification and persisted handled state; doze/reboot/clock changes remain pending |
| Camera / image decoding | Test camera absent, permission flow through external camera app, rotated/mirrored JPEGs, huge images and cancellation | Not run on hardware |
| OCR | Test actual printed recipes, low light, skew, handwriting and blank images; user can correct text and required recipe fields | Bundled native engine passed a synthetic printed fixture offline on Fire; real-photo accuracy pending |
| Website import | Test permitted public JSON-LD, redirects, 403/429, malformed metadata and private-network URL rejection; never bypass restricted access | Not run on hardware |
| Backups / deletion | Export JSON and photo-inclusive ZIP to a chosen provider; restore shared photos into an empty kitchen; reject corrupt/oversized/duplicate-entry archives; test cancellation and provider write failures; delete data/photos and confirm alarms are cancelled | Native photo ZIP round-trip passed on Fire; document-provider/cancellation matrix pending. Host tests cover corruption, bounds and rollback policies |
| Billing | Follow every scenario in AMAZON_BILLING.md using App Tester and later LAT; verify both account and entitlement transitions | Not run; local setup only |
| Release signing | Publisher signs with their protected release key, installs exact APK and records signature/checksum; repeat smoke test | Not run; publisher key absent |
| Store images | Capture the final verified hardware screens and inspect each market's composition at actual store dimensions | Host drafts plus two inspected Fire QA captures; final market/store compositions pending |

Attach evidence paths and observed results to each row before changing status. A build, screenshot or fixture test does not count as a successful device scenario.

## Isolated Fire QA

The repeatable smoke suite installs `com.dishnest.app.qa` (**DishNest QA**) and its `.qa.test` runner. Purchases are always disabled in this variant. Its UI fixtures deliberately reset only the disposable QA kitchen; every destructive fixture checks the package ID first. The ordinary `com.dishnest.app` app and its saved data are left intact. APK package identity is checked before installation.

Build `:app:assembleQa :app:assembleQaAndroidTest`, select a connected device explicitly, unlock it, then run:

```powershell
.\tools\run_device_smoke.ps1 -Serial 'YOUR_DEVICE_ID'
# For a subsequent run against the disposable QA installation:
.\tools\run_device_smoke.ps1 -Serial 'YOUR_DEVICE_ID' -ReplaceTestInstall
.\tools\run_fire_alarm_probe.ps1 -Serial 'YOUR_DEVICE_ID'
```

The smoke suite checks empty launch, recipe CRUD and editor recreation, selected named-plan recreation, an independent SQLite reopen fixture, migration of a real v1 SQLite fixture with notes/meals/templates, native photo ZIP codecs, cropped synthetic printed OCR, Arabic/Hindi formatting, denied-microphone fallback, optional pantry notification/duplicate suppression, and Arabic planner controls at an app-simulated 1.5 font scale/RTL. The RTL/font test uses the production UI in a QA-only activity whose base Android configuration also reaches native dialog windows; it does not change device settings or establish TalkBack/full locale approval. The microphone fixture records no audio.

The scripts share a local mutex to prevent their database fixtures from overlapping. Run them sequentially. The separate two-process alarm probe schedules a 15-second timer, backgrounds and kills only the QA process using ordinary `am kill`, waits 35 seconds, then verifies persisted handled state and the app's own notification in a fresh instrumentation process. It records whether the QA process was absent after the kill. This does not test doze, reboot, force-stop or exact latency. Force-stop intentionally changes Android alarm behaviour and is not substituted for process termination.

`artifacts/fire-device-qa.json` and raw `device-instrumentation.txt` contain the authoritative current smoke result and exact QA APK hash. `artifacts/fire-background-alarm.json` and the phase logs contain the separate probe result. A failed or stale report is not a pass. Device serial identifiers are excluded from evidence. All APKs remain local; no deployment or store submission is involved.

Remaining hardware cases include real-photo/camera accuracy, granted microphone and installed speech providers, live TTS/audio interruption, notification denial, full font/rotation/TalkBack coverage, actual system RTL, doze/reboot/time changes, publisher-signed release and Appstore purchases. Host audio-focus tests exercise denial/interruption policies but do not prove the Fire speech service works.

Earlier Fire captures from the previous verified APK (retained as historical UI evidence, not recaptured by every smoke run): `artifacts/screens/fire-qa-named-plan.png` and `artifacts/screens/fire-qa-arabic-large-font.png`. Both were inspected for visible controls and clipping; they remain QA evidence rather than approved store imagery.
