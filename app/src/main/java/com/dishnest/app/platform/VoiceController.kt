package com.dishnest.app.platform

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.*
import android.speech.tts.TextToSpeech
import com.dishnest.app.R

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
                ready = status == TextToSpeech.SUCCESS &&
                    (tts?.setLanguage(context.resources.configuration.locales[0]) ?: TextToSpeech.ERROR) >= 0
            }
    }

    val available
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun speak(text: String) {
        if (ready) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "dishnest-step")
        else onStatus(context.getString(R.string.voice_output_unavailable))
    }

    fun listen() {
        if (!available) {
            onStatus(
                context.getString(R.string.voice_unavailable)
            )
            return
        }
        tts?.stop()
        recognizer?.destroy()
        try {
            recognizer =
                SpeechRecognizer.createSpeechRecognizer(context).also { sr ->
                    sr.setRecognitionListener(
                        object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                onStatus(context.getString(R.string.voice_listening))
                            }

                            override fun onBeginningOfSpeech() {}

                            override fun onRmsChanged(rmsdB: Float) {}

                            override fun onBufferReceived(buffer: ByteArray?) {}

                            override fun onEndOfSpeech() {
                                onStatus(context.getString(R.string.voice_processing))
                            }

                            override fun onError(error: Int) {
                                onStatus(
                                    when (error) {
                                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                                            context.getString(R.string.copy_66bf733db7)
                                        SpeechRecognizer.ERROR_NO_MATCH,
                                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                            context.getString(R.string.voice_no_match)
                                        else ->
                                            context.getString(R.string.voice_unavailable)
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
                                context.resources.configuration.locales[0].toLanguageTag(),
                            )
                            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    )
                }
        } catch (failure: RuntimeException) {
            recognizer?.destroy()
            recognizer = null
            onStatus(context.getString(R.string.voice_unavailable))
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
