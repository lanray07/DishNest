package com.dishnest.app

import android.media.AudioManager
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.dishnest.app.platform.VoiceController
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = DishNestApp::class)
class VoiceFocusTest {
    @Test fun deniedFocusDoesNotNarrateAndReportsTouchFallback() {
        val context = ApplicationProvider.getApplicationContext<DishNestApp>()
        var status = ""
        val controller = VoiceController(context, {}, { status = it })
        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        ShadowTextToSpeech.addLanguageAvailability(context.resources.configuration.locales[0])
        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(context.getSystemService(AudioManager::class.java)).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        try {
            controller.speak("Fixture instruction")
            assertNull(tts.lastSpokenText)
            assertEquals(context.getString(R.string.voice_focus_unavailable), status)
        } finally { controller.close() }
    }

    @Test fun competingAudioStopsNarrationAndReleasesFocus() {
        val context = ApplicationProvider.getApplicationContext<DishNestApp>()
        val controller = VoiceController(context, {}, {})
        val tts = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        ShadowTextToSpeech.addLanguageAvailability(context.resources.configuration.locales[0])
        tts.onInitListener.onInit(TextToSpeech.SUCCESS)
        val audio = shadowOf(context.getSystemService(AudioManager::class.java))
        audio.setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        try {
            controller.speak("Fixture instruction")
            assertEquals("Fixture instruction", tts.lastSpokenText)
            val focus = audio.lastAudioFocusRequest.audioFocusRequest
            audio.lastAudioFocusRequest.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)
            assertTrue(tts.isStopped)
            assertEquals(focus, audio.lastAbandonedAudioFocusRequest)
        } finally { controller.close() }
        assertTrue(tts.isShutdown)
    }
}
