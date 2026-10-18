package com.dishnest.app.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import java.util.Locale

class VoiceController(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onStatus: (String) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts =
            TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS
                tts?.language = Locale.getDefault()
            }
    }

    val available
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun speak(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dishnest-step")
        else onStatus("Speech output is unavailable. Follow the step on screen.")
    }

    fun listen() {
        if (!available) {
            onStatus(
                "No speech recognition service is installed. All cooking controls work by touch."
            )
            return
        }
        tts?.stop()
        recognizer?.destroy()
        recognizer =
            SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
                sr.setRecognitionListener(
                    object : RecognitionListener {
                        override fun onReadyForSpeech(params: Bundle?) {
                            onStatus("Listening…")
                        }

                        override fun onBeginningOfSpeech() {}

                        override fun onRmsChanged(rmsdB: Float) {}

                        override fun onBufferReceived(buffer: ByteArray?) {}

                        override fun onEndOfSpeech() {
                            onStatus("Processing…")
                        }

                        override fun onError(error: Int) {
                            onStatus(
                                when (error) {
                                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                        "Microphone permission is required. Touch controls are available."
                                    SpeechRecognizer.ERROR_NO_MATCH,
                                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                        "No command heard. Tap the microphone to try again."
                                    else ->
                                        "Recognition unavailable ($error). Try again or use touch controls."
                                }
                            )
                        }

                        override fun onResults(results: Bundle?) {
                            onStatus("")
                            results
                                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                ?.firstOrNull()
                                ?.let(onText)
                        }

                        override fun onPartialResults(partialResults: Bundle?) {}

                        override fun onEvent(eventType: Int, params: Bundle?) {}
                    }
                )
                sr.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                        )
                        .putExtra(
                            RecognizerIntent.EXTRA_LANGUAGE,
                            Locale.getDefault().toLanguageTag(),
                        )
                        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                )
            }
    }

    fun stop() {
        recognizer?.cancel()
        tts?.stop()
    }

    fun close() {
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
