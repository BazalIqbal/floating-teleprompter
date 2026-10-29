package com.flowveo.prompter.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptStatsTest {

    @Test
    fun readTimeRoundsUp() {
        assertEquals(60, ScriptStats.readTimeSec(150))
        assertEquals(1, ScriptStats.readTimeSec(1))
        assertEquals(0, ScriptStats.readTimeSec(0))
    }

    @Test
    fun formatsDurations() {
        assertEquals("~57 s", ScriptStats.formatDuration(57))
        assertEquals("~2 min 45 s", ScriptStats.formatDuration(165))
        assertEquals("~3 min", ScriptStats.formatDuration(180))
    }

    @Test
    fun summaryIgnoresPauseMarkers() {
        assertEquals("3 words · ~2 s", ScriptStats.summary("One two // three //"))
        assertEquals("1 word · ~1 s", ScriptStats.summary("Hello"))
    }

    @Test
    fun firstLineSkipsBlankLinesAndMarkers() {
        assertEquals("Hello there.", ScriptStats.firstLine("\n\n  Hello there. //\nNext"))
        assertEquals("abcdefghi…", ScriptStats.firstLine("abcdefghijklmnop", maxChars = 10))
    }

    @Test
    fun titleFallsBackToFirstLineThenUntitled() {
        assertEquals("My title", ScriptStats.titleFor("  My title ", "Body"))
        assertEquals("Body line", ScriptStats.titleFor(null, "Body line\nMore"))
        assertEquals("Untitled", ScriptStats.titleFor("", "   "))
    }
}
