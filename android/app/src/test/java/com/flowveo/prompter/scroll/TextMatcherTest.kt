package com.flowveo.prompter.scroll

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatcherTest {

    // Words 0..6 paragraph 0, 7..19 paragraph 1, 20..23 paragraph 2.
    private val script = """Here's why your videos take twenty takes.

Most people read every word exactly how it's written. And that's the problem.

You're reading, not talking."""

    private val m = TextMatcher.from(script)

    @Test
    fun buildsWordsWithParagraphs() {
        assertEquals(24, m.size)
        assertEquals("heres", m.words[0].norm)
        assertEquals(0, m.words[6].paragraph)
        assertEquals("most", m.words[7].norm)
        assertEquals(1, m.words[7].paragraph)
        assertEquals(2, m.words[20].paragraph)
        assertEquals(script.indexOf("Most"), m.words[7].offset)
    }

    @Test
    fun followsWordByWord() {
        assertEquals(2, m.match("here's why your", 0))
        assertEquals(4, m.match("why your videos take", 2))
        assertEquals(6, m.match("take twenty takes", 4))
    }

    @Test
    fun usesTheTailOfALongPartial() {
        assertEquals(10, m.match("here's why your videos take twenty takes most people read every", 6))
    }

    @Test
    fun toleratesAMisheardWord() {
        // "yur" is wrong, but "why" and "videos" still line up.
        assertEquals(3, m.match("why yur videos", 0))
        // One letter off on a long word.
        assertEquals(3, m.match("why your vidos", 0))
    }

    @Test
    fun ignoresAdLibs() {
        assertNull(m.match("um so like basically you know", 0))
    }

    @Test
    fun neverMovesBackwards() {
        assertNull(m.match("here's why your", 10))
    }

    @Test
    fun neverJumpsMoreThanOneParagraph() {
        // From paragraph 0, paragraph 2 is out of reach...
        assertNull(m.match("you're reading not talking", 2))
        // ...but the next paragraph is fine.
        assertEquals(9, m.match("most people read", 6))
    }

    @Test
    fun tinyWordsAloneAreNotEnough() {
        // "and that's the" has only short words, but "that's" (5) + "the" (3) = 8 chars, so this one is allowed.
        assertEquals(18, m.match("and that's the", 15))
        // Two tiny words are not.
        assertNull(m.match("the a", 15))
    }

    @Test
    fun repeatedPhrasePicksTheNearest() {
        val r = TextMatcher.from("the cat sat down. later the cat sat again.")
        assertEquals(2, r.match("the cat sat", 0))
        assertEquals(7, r.match("the cat sat", 3))
    }

    @Test
    fun detectsTheStart() {
        assertTrue(m.matchesStart("here's why"))
        assertTrue(m.matchesStart("okay here's why your videos"))
        assertFalse(m.matchesStart("most people read"))
        assertFalse(m.matchesStart("hello"))
    }

    @Test
    fun fuzzyRules() {
        assertTrue(TextMatcher.wordsMatch("videos", "video"))
        assertTrue(TextMatcher.wordsMatch("take", "tale"))
        assertFalse(TextMatcher.wordsMatch("the", "she"))
        assertFalse(TextMatcher.wordsMatch("videos", "vids"))
    }
}
