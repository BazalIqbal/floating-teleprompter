package com.flowveo.prompter.scroll

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptTextTest {

    @Test
    fun removesMarkersAndRemembersWhereTheyWere() {
        val p = ScriptText.prepare("Hello world. //\nNext line //")
        assertEquals("Hello world.\nNext line", p.display)
        // Markers point at the last visible character before them.
        assertEquals(listOf(11, 21), p.markerOffsets)
        assertEquals('.', p.display[p.markerOffsets[0]])
        assertEquals('e', p.display[p.markerOffsets[1]])
        assertEquals(4, p.words)
    }

    @Test
    fun scriptWithoutMarkersIsUnchanged() {
        val p = ScriptText.prepare("Just words here")
        assertEquals("Just words here", p.display)
        assertEquals(emptyList<Int>(), p.markerOffsets)
        assertEquals(3, p.words)
    }
}
