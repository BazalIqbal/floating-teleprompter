package com.flowveo.prompter.scroll

import com.flowveo.prompter.data.ScrollMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollEngineTest {

    private fun engine(countdown: Int = 3) = ScrollEngine().apply { configure(140, 26, ScrollMode.AUTO, countdown) }

    @Test
    fun tapRunsCountdownThenPrompting() {
        val e = engine()
        e.onTap()
        assertEquals(PromptState.Countdown, e.ui.value.state)
        assertEquals(3, e.ui.value.countdown)
        repeat(3) { e.countdownTick() }
        assertEquals(PromptState.Prompting, e.ui.value.state)
        e.onTap()
        assertEquals(PromptState.Paused, e.ui.value.state)
    }

    @Test
    fun countdownOffStartsImmediately() {
        val e = engine(countdown = 0)
        e.onTap()
        assertEquals(PromptState.Prompting, e.ui.value.state)
    }

    @Test
    fun speedAndFontStayInRange() {
        val e = engine()
        repeat(50) { e.changeSpeed(+10) }
        assertEquals(250, e.ui.value.wpm)
        repeat(50) { e.changeFontSize(-2) }
        assertEquals(16, e.ui.value.fontSizeSp)
    }

    @Test
    fun minimizePausesAndRestoreKeepsPaused() {
        val e = engine(countdown = 0)
        e.onTap()
        e.minimize()
        assertTrue(e.ui.value.minimized)
        assertEquals(PromptState.Paused, e.ui.value.state)
        e.restore()
        assertFalse(e.ui.value.minimized)
        assertEquals(PromptState.Paused, e.ui.value.state)
    }

    @Test
    fun restartGoesBackToTopAndKeepsSpeed() {
        val e = engine(countdown = 0)
        e.changeSpeed(+20)
        e.onTap()
        e.setPosition(500f)
        e.finish()
        e.restart()
        assertEquals(0f, e.position.value, 0f)
        assertEquals(PromptState.Ready, e.ui.value.state)
        assertEquals(160, e.ui.value.wpm)
    }

    @Test
    fun lockAndModeToggle() {
        val e = engine()
        e.lock()
        assertTrue(e.ui.value.locked)
        e.unlock()
        assertFalse(e.ui.value.locked)
        e.toggleMode()
        assertEquals(ScrollMode.VOICE, e.ui.value.mode)
    }

    @Test
    fun voiceStartSkipsTheCountdown() {
        val e = ScrollEngine().apply { configure(140, 26, ScrollMode.VOICE, 3) }
        assertTrue(e.ui.value.followsVoice)
        e.startFromVoice()
        assertEquals(PromptState.Prompting, e.ui.value.state)
    }

    @Test
    fun fallbackMakesVoiceScrollLikeAuto() {
        val e = ScrollEngine().apply { configure(140, 26, ScrollMode.VOICE, 3) }
        e.setVoiceFallback(true)
        assertFalse(e.ui.value.followsVoice)
        e.toggleMode() // to Auto clears the fallback flag
        assertFalse(e.ui.value.voiceFallback)
    }

    @Test
    fun remotePlayPauseStartsThenToggles() {
        val e = engine(countdown = 0)
        e.remotePlayPause()
        assertEquals(PromptState.Prompting, e.ui.value.state)
        e.remotePlayPause()
        assertEquals(PromptState.Paused, e.ui.value.state)
    }
}
