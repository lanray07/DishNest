# Permission inventory

The merged development manifest declares:

| Permission | Purpose | User interaction |
| --- | --- | --- |
| `android.permission.INTERNET` | User-requested public recipe URL import; Amazon transport when configured | URL import starts only after the user chooses it. Purchases remain disabled |
| `android.permission.RECORD_AUDIO` | Push-to-talk recognition through the installed provider | Runtime request when the microphone control is tapped; denial preserves touch controls |
| `android.permission.POST_NOTIFICATIONS` | Cooking timer and optional pantry alerts on Android 13+ | Runtime request when adding a timer or enabling pantry reminders; the countdown remains visible if denied |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule persisted active timers and opted-in pantry reminders after restart | No runtime prompt; device power restrictions still apply |
| `com.dishnest.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX's signature-protected internal receiver compatibility | Framework-generated signature permission, not a user data permission |

Microphone and camera hardware are optional in the manifest. Photo selection uses a system content picker and copies the selected image into private app storage. Camera capture delegates to an available camera app. Broad storage permissions, location, contacts, accounts and exact-alarm permissions are not requested.

Keeping the display awake uses the cooking window's screen flag rather than a wake-lock permission. Automatic backup is disabled, with explicit legacy backup configuration and modern cloud/device-transfer exclusions. JSON, text and photo-inclusive ZIP exports are written only to a destination chosen by the user. ZIP restoration validates entry names, aggregate/per-entry sizes, images, record references and the empty-kitchen condition; failed imports remove their staged images. Recipe-photo imports re-encode bounded, oriented pixels without source EXIF metadata. Saved/shared photo references are preserved during cleanup; unreferenced drafts older than seven days can be removed.

The release manifest declares Amazon's IAP and DRM response receivers, each protected by Amazon's corresponding sender permission. Billing-enabled debug builds retain them; the billing-disabled debug preview excludes both for host-JVM compatibility. Package visibility is limited to the Amazon Appstore/App Tester package names and the speech/TTS service intents.

When configured, billing checks can contact Amazon and the HTTPS verification service during startup, resume and restore. Amazon user/receipt identifiers are private billing data, separate from the optional anonymous aggregate usage counts. The default delivery build has no configured billing endpoint or enabled purchases. Deleting kitchen data preserves private purchase records for restoration; clearing app storage or uninstalling removes their local copy. Complete final store privacy labels and real-device permission tests before submission.

Pantry reminders are off by default. When enabled, an inexact daily alarm checks stored dates at approximately 09:00 local time and posts a generic notification for dates within three days, without ingredient names. Time/timezone changes and reboot reschedule alarms. Turning reminders off or deleting kitchen data cancels the pantry alarm/notification. Notification access and device power restrictions can delay or suppress delivery. OCR crop inputs are private temporary cache files, excluded from backups, removed after successful recognition/cancel or after one day when abandoned.
