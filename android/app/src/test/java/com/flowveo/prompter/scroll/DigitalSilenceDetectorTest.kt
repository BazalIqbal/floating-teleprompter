package com.flowveo.prompter.scroll

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DigitalSilenceDetectorTest {

    /** Feeds a level every 100 ms from [fromMs] to [toMs] (inclusive). */
    private fun DigitalSilenceDetector.feed(fromMs: Long, toMs: Long, level: (Long) -> Float): Boolean {
        var verdict = false
        var t = fromMs
        while (t <= toMs) {
            verdict = onLevel(level(t), t)
            t += 100
        }
        return verdict
    }

    @Test
    fun flatFloorForThreeSecondsIsBlocked() {
        val d = DigitalSilenceDetector()
        assertTrue(d.feed(0, 3_000) { -2f })
    }

    @Test
    fun flatFloorForLessThanThreeSecondsIsNotYetBlocked() {
        val d = DigitalSilenceDetector()
        assertFalse(d.feed(0, 2_500) { -2f })
    }

    @Test
    fun quietRoomNoiseIsNotBlocked() {
        val d = DigitalSilenceDetector()
        // A real room wobbles by a dB or two even when nobody talks.
        assertFalse(d.feed(0, 5_000) { t -> if ((t / 100) % 2 == 0L) -1.0f else 0.5f })
    }

    @Test
    fun afterSpeechAPauseNeverCountsAsBlocked() {
        val d = DigitalSilenceDetector()
        d.feed(0, 1_000) { t -> (t % 700) / 100f }
        d.onSpeech()
        // A long quiet pause after talking must not trigger the switch.
        assertFalse(d.feed(1_100, 6_000) { -2f })
    }

    @Test
    fun becomesBlockedWhenNoiseStopsAndLaterUnblocksOnNoise() {
        val d = DigitalSilenceDetector()
        d.feed(0, 1_000) { t -> if ((t / 100) % 2 == 0L) -1f else 1f }
        assertTrue(d.feed(1_100, 4_200) { -2f })
        assertFalse(d.feed(4_300, 4_400) { 3f })
    }

    @Test
    fun resetStartsANewTake() {
        val d = DigitalSilenceDetector()
        d.onSpeech()
        d.reset()
        assertTrue(d.feed(0, 3_000) { -2f })
    }
}
