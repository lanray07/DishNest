# Device QA record

Automated host rendering is useful evidence but does not exercise Fire hardware, Appstore communication, installed speech providers, native OCR accuracy, AlarmManager delivery or TalkBack. Only the targeted Fire smoke results below are passed; the full hardware matrix remains incomplete.

## Devices and evidence

Record model, Fire OS/Android version, API, locale, font scale, app version, APK checksum, test date and tester. Run the compiled instrumentation APK only on an explicitly selected connected device. A KFTRWI Fire tablet running Android 9/API 28 is now connected; the earlier emulator attempts did not provide a working accelerated emulator. Recheck with `tools/collect_device_evidence.ps1` before testing. That script is read-only and does not install, submit or publish anything.

| Scenario | Steps and expected outcome | Status |
| --- | --- | --- |
| Fresh install / offline | Start without internet; kitchen is empty, manual recipe CRUD, planner, pantry and shopping work | Empty launch and recipe CRUD passed on Fire; airplane-mode and full offline flow pending |
| Persistence / process death | Save content and an active timer; force-stop and relaunch; saved content remains, unsaved editor behaviour is checked separately | SQLite close/reopen passed on Fire; process death and timer recovery pending |
| Rotation / window resizing | Rotate every dialog and cooking screen; no clipped controls or unexpected data loss | Not run on hardware |
| Large text / TalkBack | Test largest supported font and display scaling, focus order, labels, buttons, scrollable dialogs and live timer announcements | Not run on hardware |
| Arabic / RTL | Test navigation, dialogs, mixed recipe text, numbers, units, currency and long labels | Not run on hardware |
| Microphone denied / absent provider | Deny permission, revoke it in settings, test without recognition provider; touch controls always remain usable | Not run on hardware |
| Voice and narration | Test locale commands, repeated commands, interruption, screen lock, incoming audio and unavailable TTS language | Not run on hardware |
| Notifications denied | Deny notification access; in-app timer state remains visible; no false promise of background alerts | Not run on hardware |
| Timer background / doze / reboot | Pause/resume/cancel multiple timers; background, sleep, reboot, change wall clock; record actual inexact delivery behaviour | Not run on hardware |
| Camera / image decoding | Test camera absent, permission flow through external camera app, rotated/mirrored JPEGs, huge images and cancellation | Not run on hardware |
| OCR | Test actual printed recipes, low light, skew, handwriting and blank images; user can correct text and required recipe fields | Bundled native engine passed a synthetic printed fixture offline on Fire; real-photo accuracy pending |
| Website import | Test permitted public JSON-LD, redirects, 403/429, malformed metadata and private-network URL rejection; never bypass restricted access | Not run on hardware |
| Backups / deletion | Export JSON and photo-inclusive ZIP to a chosen provider; restore shared photos into an empty kitchen; reject corrupt/oversized/duplicate-entry archives; test cancellation and provider write failures; delete data/photos and confirm alarms are cancelled | Native photo ZIP round-trip passed on Fire; document-provider/cancellation matrix pending. Host tests cover corruption, bounds and rollback policies |
| Billing | Follow every scenario in AMAZON_BILLING.md using App Tester and later LAT; verify both account and entitlement transitions | Not run; local setup only |
| Release signing | Publisher signs with their protected release key, installs exact APK and records signature/checksum; repeat smoke test | Not run; publisher key absent |
| Store images | Capture the final verified hardware screens and inspect each market's composition at actual store dimensions | Draft host renders only |

Attach evidence paths and observed results to each row before changing status. A build, screenshot or fixture test does not count as a successful device scenario.

## Executed Fire smoke

On 18 October 2026, all five instrumentation tests passed on KFTRWI, Android 9/API 28, en-GB. The exact debug APK SHA-256 is recorded in `artifacts/fire-device-qa.json`; raw runner results are in `artifacts/device-instrumentation.txt`. The first UI attempt was blocked by an asleep, securely locked display. The user unlocked it, and the complete rerun passed. No authentication bypass or app-data clear was performed.

Coverage: empty-kitchen launch and real create/edit/delete UI, a separate SQLite persistence database reopened on device, native bitmap photo-backup ZIP round-trip, the bundled offline OCR engine on a synthetic printed fixture, and Arabic/Hindi resource formatting. `artifacts/screens/fire-empty-home.png` is an actual foreground Fire capture, not an approved store screenshot. Microphone/TTS, alarm delivery, accessibility, actual photos, full locale layouts and store purchases remain unverified.

To repeat local QA, build the debug and instrumentation APKs, select a connected device explicitly, unlock its display, and run `tools/run_device_smoke.ps1 -Serial 'YOUR_DEVICE_ID'`. It installs only the local debug/test APKs. It never clears kitchen data. It refuses an existing installation unless `-ReplaceTestInstall` is supplied for a known QA installation; the UI test expects an empty kitchen and creates/deletes its own toast fixture. Device serial identifiers are omitted from saved evidence. No release upload or store submission is involved.
