package com.flowveo.prompter.scroll

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Listens with the system SpeechRecognizer for Voice mode.
 *
 * The system recognizer keeps hearing while the camera app records, even though our own
 * mic capture is silenced. It stops after each phrase or silence, so
 * it is restarted continuously; [State.text] is the latest partial or final text of the
 * current phrase, which the overlay feeds to [TextMatcher].
 *
 * [State.blocked] means "can't hear": flat digital silence before any speech, no
 * recognizer on the phone, no mic permission, or repeated audio errors. The overlay then
 * falls back to Auto if the setting allows it.
 *
 * Must be created and used on the main thread (SpeechRecognizer requirement).
 */
class VoiceTracker(private val context: Context) {

    data class State(
        val listening: Boolean = false,
        /** Recognizer RMS, roughly -2..10. */
        val level: Float = 0f,
        /** Latest recognized text of the current phrase (partial or final). */
        val text: String = "",
        /** Bumped on every new [text], so the same words said twice still count as an update. */
        val textVersion: Int = 0,
        val restarts: Int = 0,
        val heardSpeech: Boolean = false,
        val blocked: Boolean = false,
        val onDevice: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private val detector = DigitalSilenceDetector()
    private var recognizer: SpeechRecognizer? = null
    /** Set when the on-device recognizer can't serve the language; use the standard one instead. */
    private var avoidOnDevice = false
    private var hardErrorsInARow = 0

    fun start() {
        stop()
        detector.reset()
        hardErrorsInARow = 0
        _state.value = State()
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            _state.update { it.copy(blocked = true, error = "Microphone permission not granted") }
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.update { it.copy(blocked = true, error = "No speech recognizer on this phone") }
            return
        }
        // Prefer the on-device recognizer: offline, private and no start beep.
        val onDevice = !avoidOnDevice && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        val r = if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else SpeechRecognizer.createSpeechRecognizer(context)
        r.setRecognitionListener(listener)
        recognizer = r
        _state.update { it.copy(listening = true, onDevice = onDevice) }
        listen()
    }

    fun stop() {
        main.removeCallbacksAndMessages(null)
        recognizer?.run {
            cancel()
            destroy()
        }
        recognizer = null
        _state.update { it.copy(listening = false) }
    }

    /** New take: forget earlier speech so a block is detected again. */
    fun resetTake() {
        detector.reset()
        _state.update { it.copy(heardSpeech = false, blocked = false, text = "") }
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        recognizer?.startListening(intent)
    }

    /** The recognizer stops after each phrase or silence; restart it shortly after. */
    private fun scheduleRestart() {
        if (recognizer == null) return
        main.removeCallbacksAndMessages(null)
        main.postDelayed({
            if (recognizer != null) {
                _state.update { it.copy(restarts = it.restarts + 1) }
                listen()
            }
        }, RESTART_DELAY_MS)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            val flat = detector.onLevel(rmsdB, SystemClock.elapsedRealtime())
            _state.update { it.copy(level = rmsdB, blocked = flat || it.blocked && !it.heardSpeech) }
        }

        override fun onPartialResults(partialResults: Bundle?) = showText(partialResults)

        override fun onResults(results: Bundle?) {
            showText(results)
            scheduleRestart()
        }

        override fun onError(error: Int) {
            // On-device model missing for this language: switch to the standard recognizer once.
            if (_state.value.onDevice && (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED ||
                    error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
            ) {
                avoidOnDevice = true
                main.post { start() }
                return
            }
            val hard = error == SpeechRecognizer.ERROR_AUDIO ||
                error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ||
                error == SpeechRecognizer.ERROR_CLIENT
            hardErrorsInARow = if (hard) hardErrorsInARow + 1 else 0
            _state.update {
                it.copy(error = errorName(error), blocked = it.blocked || hardErrorsInARow >= HARD_ERRORS_TO_BLOCK)
            }
            // "No match" and "speech timeout" are normal when nobody talks; just restart.
            scheduleRestart()
        }
    }

    private fun showText(bundle: Bundle?) {
        val text = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
        if (text.isBlank()) return
        detector.onSpeech()
        hardErrorsInARow = 0
        _state.update {
            it.copy(text = text, textVersion = it.textVersion + 1, heardSpeech = true, blocked = false, error = null)
        }
    }

    private fun errorName(code: Int) = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "audio error"
        SpeechRecognizer.ERROR_NO_MATCH -> "no match"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "speech timeout"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "recognizer busy"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "no permission"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "network"
        SpeechRecognizer.ERROR_CLIENT -> "client error"
        else -> "error $code"
    }

    private companion object {
        const val RESTART_DELAY_MS = 250L
        const val HARD_ERRORS_TO_BLOCK = 3
    }
}
