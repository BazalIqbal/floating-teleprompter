package com.flowveo.prompter.scroll

import com.flowveo.prompter.data.AppSettings
import com.flowveo.prompter.data.ScrollMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The overlay's states. */
enum class PromptState { Ready, Countdown, Prompting, Paused, Finished }

data class ScrollUiState(
    val state: PromptState = PromptState.Ready,
    /** 3, 2, 1 while counting down. */
    val countdown: Int = 0,
    /** Seconds of countdown when a take starts (0 = none). */
    val countdownSeconds: Int = 3,
    val wpm: Int = 140,
    val fontSizeSp: Int = 26,
    val mode: ScrollMode = ScrollMode.VOICE,
    /** Panel ignores touches; everything passes through to the camera. */
    val locked: Boolean = false,
    /** Panel shrunk to a small bubble at the screen edge. */
    val minimized: Boolean = false,
    /** Voice mode, but the mic can't hear us: scroll like Auto (Settings › Auto when mic is blocked). */
    val voiceFallback: Boolean = false,
) {
    /** True when the script should follow speech rather than scroll at a fixed speed. */
    val followsVoice: Boolean get() = mode == ScrollMode.VOICE && !voiceFallback
}

/** One-off requests that need the overlay's text layout (from remote keys). */
enum class Command { LineBack, LineForward }

/**
 * Single owner of the scroll position and prompting state. The overlay UI, its controls, the notification and
 * (later) the voice tracker and remote keys all go through here.
 */
@Singleton
class ScrollEngine @Inject constructor() {

    private val _ui = MutableStateFlow(ScrollUiState())
    val ui: StateFlow<ScrollUiState> = _ui.asStateFlow()

    /**
     * Scroll position in px from the top of the script. Kept apart from [ui] because it
     * changes every frame: the overlay reads it only while placing the text, so a new
     * position moves the text without recomposing the whole panel.
     */
    private val _position = MutableStateFlow(0f)
    val position: StateFlow<Float> = _position.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    /** True while the overlay is on screen; the remote-key service only acts then. */
    @Volatile var overlayActive: Boolean = false

    fun send(command: Command) {
        _commands.tryEmit(command)
    }

    /** New take for a script: back to the top, waiting for a tap. */
    fun configure(wpm: Int, fontSizeSp: Int, mode: ScrollMode, countdownSeconds: Int) {
        _position.value = 0f
        _ui.value = ScrollUiState(countdownSeconds = countdownSeconds, wpm = wpm, fontSizeSp = fontSizeSp, mode = mode)
    }

    /** Tap on the panel: Ready -> countdown (or straight to prompting), countdown -> cancel, prompting <-> paused. */
    fun onTap() = _ui.update {
        when (it.state) {
            PromptState.Ready ->
                if (it.countdownSeconds <= 0) it.copy(state = PromptState.Prompting)
                else it.copy(state = PromptState.Countdown, countdown = it.countdownSeconds)
            PromptState.Countdown -> it.copy(state = PromptState.Ready, countdown = 0)
            PromptState.Prompting -> it.copy(state = PromptState.Paused)
            PromptState.Paused -> it.copy(state = PromptState.Prompting)
            PromptState.Finished -> it
        }
    }

    /** Pause/resume from the notification or the control bar. */
    fun togglePause() = _ui.update {
        when (it.state) {
            PromptState.Prompting -> it.copy(state = PromptState.Paused)
            PromptState.Paused -> it.copy(state = PromptState.Prompting)
            else -> it
        }
    }

    /** Remote play/pause key: also starts a take from Ready (like a tap). */
    fun remotePlayPause() {
        when (_ui.value.state) {
            PromptState.Ready, PromptState.Countdown -> onTap()
            else -> togglePause()
        }
    }

    /** Voice mode starts the moment the speaker says the opening words: no countdown. */
    fun startFromVoice() = _ui.update {
        if (it.state == PromptState.Ready || it.state == PromptState.Countdown) it.copy(state = PromptState.Prompting, countdown = 0) else it
    }

    fun setVoiceFallback(on: Boolean) = _ui.update { if (it.voiceFallback == on) it else it.copy(voiceFallback = on) }

    /** Called once per second by the overlay while counting down. */
    fun countdownTick() = _ui.update {
        if (it.state != PromptState.Countdown) it
        else if (it.countdown <= 1) it.copy(state = PromptState.Prompting, countdown = 0)
        else it.copy(countdown = it.countdown - 1)
    }

    fun setPosition(px: Float) {
        _position.value = px.coerceAtLeast(0f)
    }

    fun finish() = _ui.update { it.copy(state = PromptState.Finished) }

    /** Back to the top, waiting for a tap (another take). Keeps speed, size and mode. */
    fun restart() {
        _position.value = 0f
        _ui.update { it.copy(state = PromptState.Ready, countdown = 0) }
    }

    /** If the script had finished and the user scrolls back, it can continue. */
    fun resumeIfFinished() = _ui.update { if (it.state == PromptState.Finished) it.copy(state = PromptState.Paused) else it }

    fun changeSpeed(deltaWpm: Int) = _ui.update {
        it.copy(wpm = (it.wpm + deltaWpm).coerceIn(AppSettings.WPM_RANGE))
    }

    fun changeFontSize(deltaSp: Int) = _ui.update {
        it.copy(fontSizeSp = (it.fontSizeSp + deltaSp).coerceIn(AppSettings.FONT_RANGE))
    }

    fun toggleMode() = _ui.update {
        it.copy(mode = if (it.mode == ScrollMode.VOICE) ScrollMode.AUTO else ScrollMode.VOICE, voiceFallback = false)
    }

    fun lock() = _ui.update { it.copy(locked = true) }

    fun unlock() = _ui.update { it.copy(locked = false) }

    /** Minimizing pauses the script; restoring leaves it paused until tapped. */
    fun minimize() = _ui.update {
        it.copy(minimized = true, state = if (it.state == PromptState.Prompting) PromptState.Paused else it.state)
    }

    fun restore() = _ui.update { it.copy(minimized = false) }
}
