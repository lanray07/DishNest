# Permission inventory

The merged development manifest declares:

| Permission | Purpose | User interaction |
| --- | --- | --- |
| `android.permission.INTERNET` | User-requested public recipe URL import; Amazon transport when configured | URL import starts only after the user chooses it. Purchases remain disabled |
| `android.permission.RECORD_AUDIO` | Push-to-talk recognition through the installed provider | Runtime request when the microphone control is tapped; denial preserves touch controls |
| `android.permission.POST_NOTIFICATIONS` | Cooking timer alerts on Android 13+ | Runtime request when adding a timer; the countdown remains visible if denied |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Reschedule persisted active timers after restart | No runtime prompt; device power restrictions still apply |
| `com.dishnest.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | AndroidX's signature-protected internal receiver compatibility | Framework-generated signature permission, not a user data permission |

Microphone and camera hardware are optional in the manifest. Photo selection uses a system content picker and copies the selected image into private app storage. Camera capture delegates to an available camera app. Broad storage permissions, location, contacts, accounts and exact-alarm permissions are not requested.

Keeping the display awake uses the cooking window's screen flag rather than a wake-lock permission. Automatic backup is disabled, with explicit legacy backup configuration and modern cloud/device-transfer exclusions. JSON/text exports are written only to a destination chosen by the user.

The release manifest declares Amazon's IAP and DRM response receivers, each protected by Amazon's corresponding sender permission. Billing-enabled debug builds retain them; the billing-disabled debug preview excludes both for host-JVM compatibility. Package visibility is limited to the Amazon Appstore/App Tester package names and the speech/TTS service intents.

When configured, billing checks can contact Amazon and the HTTPS verification service during startup, resume and restore. Amazon user/receipt identifiers are private billing data, separate from the optional anonymous aggregate usage counts. The default delivery build has no configured billing endpoint or enabled purchases. Deleting kitchen data preserves private purchase records for restoration; clearing app storage or uninstalling removes their local copy. Complete final store privacy labels and real-device permission tests before submission.
