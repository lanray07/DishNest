# Voice, timers and device compatibility

DishNest targets Android API 26+ with no mandatory Google Play Services. Intended Fire targets are Fire OS 7 and later, subject to physical-device validation. Fire OS 6 and earlier are outside this build's minimum SDK. Android phone layouts are included; Amazon Appstore distribution for ordinary Android mobile devices is no longer supported by Amazon.

## Voice operation

Open a saved recipe and choose **Start cooking**. Use **Read step** for explicit TTS narration, or enable spoken guidance with the speaker control. Use **Tap to speak** for one recognition session. The microphone permission is requested at that point. The app never automatically restarts listening, records continuously or stores raw microphone audio.

DishNest checks `SpeechRecognizer.isRecognitionAvailable`. A microphone alone does not establish recognition support. Fire tablets may lack a compatible recognition service. All steps, ingredients, serving adjustments and timer controls remain available by touch. The status area explains permission denial, a missing service, silence, no match or a recognition error.

The device's recognition provider may require a network connection and may process audio remotely under its own policy. Recognition locale follows the device locale. TTS language and installed voices also vary by device. Universal offline recognition is not promised.

English commands implemented in this preview:

- “Start cooking” — read the current step without advancing it.
- “Read the next step”, “Go back one step”, “Repeat that”.
- “Show ingredients” and “How much flour do I need?” for saved ingredient names.
- “Set a timer for ten minutes” and “Set a timer for 30 seconds”. Single minute or second durations are supported; ambiguous mixed units and unsupported hour phrases are rejected. Maximum timer duration is 24 hours.
- “How much time is left?” — report the available timers.
- “Pause timer” — pause the first running timer; use touch to choose a specific timer.
- “Finish cooking” — leave cooking mode; active timers continue.

Optional spoken responses follow the speaker setting. Repeating an identical command can narrate the same step again. A step-status label does not interrupt narration of the actual instruction.

Partial non-English next-step dictionaries exist for the initial locale set. These are not complete locale-specific voice systems. Full command, response and native-speaker review remain release work. Recipe text is never translated automatically.

## Timers and notifications

Timer records are saved in Room. Active timers use wall-clock deadlines; paused timers preserve remaining milliseconds. Alarms are inexact `setAndAllowWhileIdle` alarms rather than exact-alarm claims. A boot receiver reschedules active records after device restart. The app requests notification permission on Android 13+ when adding a timer in cooking mode.

Power management can delay a background alert. Device clock changes affect wall-clock deadlines. With notifications denied, keep cooking mode open to see the countdown. Touch controls allow independently labelled timers to be paused, resumed or cancelled. Completed notifications can be dismissed, and cancelling a timer cancels its associated notification.

## Validation status

Executed JVM tests cover command parsing, unknown and compound phrases, timer arithmetic and persistence, cooking-screen navigation, and phone/tablet layouts. They do not establish real provider availability, microphone audio quality, TTS pronunciation, OCR accuracy, exact notification latency, doze behaviour or boot delivery.

Before release, test on physical Fire hardware with speech absent, permission denied, notification denied, screen rotation, app backgrounding, process termination, battery restrictions and reboot. Also test large text, TalkBack and Arabic right-to-left layout. Record the exact device model, Fire OS version, installed recognition/TTS providers and network state for each result.
