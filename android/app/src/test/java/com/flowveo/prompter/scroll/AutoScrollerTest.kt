package com.flowveo.prompter.scroll

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoScrollerTest {

    @Test
    fun speedScalesWithWpm() {
        // 120 words in 1200 px = 10 px per word. 120 wpm = 2 words/s = 20 px/s.
        assertEquals(20f, AutoScroller.pixelsPerSecond(120, 120, 1200f), 0.001f)
        assertEquals(40f, AutoScroller.pixelsPerSecond(240, 120, 1200f), 0.001f)
    }

    @Test
    fun speedIsZeroForEmptyScript() {
        assertEquals(0f, AutoScroller.pixelsPerSecond(140, 0, 1200f), 0f)
        assertEquals(0f, AutoScroller.pixelsPerSecond(140, 10, 0f), 0f)
    }

    @Test
    fun stepMovesAtConstantSpeedWithoutMarkers() {
        val p = AutoScroller.step(AutoScroller.Position(0f), 0.5f, 20f, emptyList(), 1.5f, 1000f)
        assertEquals(10f, p.px, 0.001f)
    }

    @Test
    fun markerStopsScrollAndHolds() {
        // Marker at 10 px; speed 20 px/s; a 1 s step reaches it after 0.5 s,
        // so 0.5 s of the 1.5 s hold is already used.
        val p1 = AutoScroller.step(AutoScroller.Position(0f), 1f, 20f, listOf(10f), 1.5f, 1000f)
        assertEquals(10f, p1.px, 0.001f)
        assertEquals(1.0f, p1.holdRemainingSec, 0.001f)
        assertEquals(1, p1.nextMarker)

        // Still holding after another 0.6 s.
        val p2 = AutoScroller.step(p1, 0.6f, 20f, listOf(10f), 1.5f, 1000f)
        assertEquals(10f, p2.px, 0.001f)
        assertEquals(0.4f, p2.holdRemainingSec, 0.001f)

        // Hold ends after 0.4 s, then 0.1 s of movement = 2 px.
        val p3 = AutoScroller.step(p2, 0.5f, 20f, listOf(10f), 1.5f, 1000f)
        assertEquals(12f, p3.px, 0.001f)
        assertEquals(0f, p3.holdRemainingSec, 0.001f)
    }

    @Test
    fun neverScrollsPastTheEnd() {
        val p = AutoScroller.step(AutoScroller.Position(990f), 1f, 20f, emptyList(), 1.5f, 1000f)
        assertEquals(1000f, p.px, 0.001f)
    }

    @Test
    fun countWordsIgnoresPauseMarkers() {
        assertEquals(5, AutoScroller.countWords("One two three. //\n\nFour  five //"))
    }
}
