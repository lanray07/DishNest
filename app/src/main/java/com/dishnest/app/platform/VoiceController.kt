package com.dishnest.app.platform

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.UtteranceProgressListener
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
    private val handler = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private var activeUtterance: String? = null
    private var revision = 0L
    private val speechAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(speechAttributes).setWillPauseWhenDucked(true)
        .setOnAudioFocusChangeListener { change ->
            if (change < 0) { stop(); onStatus(context.getString(R.string.voice_focus_unavailable)) }
        }.build()
    private fun completed(id: String?) {
        handler.post {
            if (activeUtterance == id) { activeUtterance = null; audio.abandonAudioFocusRequest(focus) }
        }
    }

    init {
        tts =
            TextToSpeech(context) { status ->
                ready = status == TextToSpeech.SUCCESS &&
                    (tts?.setLanguage(context.resources.configuration.locales[0]) ?: TextToSpeech.ERROR) >= 0
                tts?.setAudioAttributes(speechAttributes)
            }
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { completed(utteranceId) }
            @Deprecated("Required platform callback")
            override fun onError(utteranceId: String?) { completed(utteranceId) }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { completed(utteranceId) }
        })
    }

    val available
        get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun speak(text: String) {
        if (!ready) { onStatus(context.getString(R.string.voice_output_unavailable)); return }
        if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            onStatus(context.getString(R.string.voice_focus_unavailable)); return
        }
        val id = "dishnest-step-${++revision}"
        activeUtterance = id
        if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            completed(id)
            onStatus(context.getString(R.string.voice_output_unavailable))
        }
    }

    fun listen() {
        stop()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onStatus(context.getString(R.string.copy_66bf733db7)); return
        }
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
        activeUtterance = null
        recognizer?.cancel()
        tts?.stop()
        audio.abandonAudioFocusRequest(focus)
    }

    fun close() {
        stop()
        recognizer?.destroy()
        recognizer = null
        tts?.shutdown()
        tts = null
    }
}
