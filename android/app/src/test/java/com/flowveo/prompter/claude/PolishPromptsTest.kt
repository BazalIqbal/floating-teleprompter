package com.flowveo.prompter.claude

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolishPromptsTest {

    @Test
    fun userMessageWrapsTheScriptAndStatesTheTask() {
        val m = PolishPrompts.userMessage("  Hello there. //  ", PolishPreset.TIGHTEN)
        assertTrue(m.startsWith("Tighten this script"))
        assertTrue(m.endsWith("<script>\nHello there. //\n</script>"))
    }

    @Test
    fun durationPresetsGiveAWordTarget() {
        assertEquals(150, PolishPrompts.targetWords(60))
        assertTrue(PolishPrompts.userMessage("x", PolishPreset.SEC_30).contains("roughly 75 words"))
    }

    @Test
    fun cleanResponseStripsWrappers() {
        assertEquals("Line one. //\n\nLine two.", PolishPrompts.cleanResponse("```\nLine one. //\n\nLine two.\n```"))
        assertEquals("Body text.", PolishPrompts.cleanResponse("Here's the rewritten script:\nBody text."))
        assertEquals("Quoted.", PolishPrompts.cleanResponse("\"Quoted.\""))
        assertEquals("Tagged.", PolishPrompts.cleanResponse("<script>\nTagged.\n</script>"))
    }

    @Test
    fun cleanResponseKeepsNormalText() {
        val text = "Here is why it matters. //\nKeep going."
        assertEquals(text, PolishPrompts.cleanResponse(text))
    }

    @Test
    fun defaultModelIsTheNewestSonnet() {
        val models = listOf(
            ModelOption("family-a-sonnet-old", "Sonnet old", 100),
            ModelOption("family-b-opus", "Opus", 300),
            ModelOption("family-c-sonnet-new", "Sonnet new", 200),
        )
        assertEquals("family-c-sonnet-new", ModelChoice.pickDefault(models)?.id)
        assertEquals("family-b-opus", ModelChoice.sorted(models).first().id)
    }

    @Test
    fun defaultModelFallsBackToNewestWhenNoSonnet() {
        val models = listOf(ModelOption("x-1", "X1", 1), ModelOption("x-2", "X2", 2))
        assertEquals("x-2", ModelChoice.pickDefault(models)?.id)
        assertNull(ModelChoice.pickDefault(emptyList()))
    }
}
