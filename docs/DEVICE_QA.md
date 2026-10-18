# Device QA record

Automated host rendering is useful evidence but does not exercise Fire hardware, Appstore communication, installed speech providers, native OCR accuracy, AlarmManager delivery or TalkBack. No physical-device result is marked passed here.

## Devices and evidence

Record model, Fire OS/Android version, API, locale, font scale, app version, APK checksum, test date and tester. Run the compiled instrumentation APK only on an explicitly selected connected device. The local workstation currently has no connected device and no working accelerated emulator. Recheck with `tools/collect_device_evidence.ps1` before testing. That script is read-only and does not install, submit or publish anything.

| Scenario | Steps and expected outcome | Status |
| --- | --- | --- |
| Fresh install / offline | Start without internet; kitchen is empty, manual recipe CRUD, planner, pantry and shopping work | Not run on hardware |
| Persistence / process death | Save content and an active timer; force-stop and relaunch; saved content remains, unsaved editor behaviour is checked separately | Not run on hardware |
| Rotation / window resizing | Rotate every dialog and cooking screen; no clipped controls or unexpected data loss | Not run on hardware |
| Large text / TalkBack | Test largest supported font and display scaling, focus order, labels, buttons, scrollable dialogs and live timer announcements | Not run on hardware |
| Arabic / RTL | Test navigation, dialogs, mixed recipe text, numbers, units, currency and long labels | Not run on hardware |
| Microphone denied / absent provider | Deny permission, revoke it in settings, test without recognition provider; touch controls always remain usable | Not run on hardware |
| Voice and narration | Test locale commands, repeated commands, interruption, screen lock, incoming audio and unavailable TTS language | Not run on hardware |
| Notifications denied | Deny notification access; in-app timer state remains visible; no false promise of background alerts | Not run on hardware |
| Timer background / doze / reboot | Pause/resume/cancel multiple timers; background, sleep, reboot, change wall clock; record actual inexact delivery behaviour | Not run on hardware |
| Camera / image decoding | Test camera absent, permission flow through external camera app, rotated/mirrored JPEGs, huge images and cancellation | Not run on hardware |
| OCR | Test actual printed recipes, low light, skew, handwriting and blank images; user can correct text and required recipe fields | Not run on hardware |
| Website import | Test permitted public JSON-LD, redirects, 403/429, malformed metadata and private-network URL rejection; never bypass restricted access | Not run on hardware |
| Backups / deletion | Export to chosen provider, verify JSON content, restore into empty kitchen, reject corrupt backup, delete data/photos and confirm alarms are cancelled | Not run on hardware |
| Billing | Follow every scenario in AMAZON_BILLING.md using App Tester and later LAT; verify both account and entitlement transitions | Not run; local setup only |
| Release signing | Publisher signs with their protected release key, installs exact APK and records signature/checksum; repeat smoke test | Not run; publisher key absent |
| Store images | Capture the final verified hardware screens and inspect each market's composition at actual store dimensions | Draft host renders only |

Attach evidence paths and observed results to each row before changing status. A build, screenshot or fixture test does not count as a successful device scenario.
