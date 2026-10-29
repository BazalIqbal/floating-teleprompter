package com.flowveo.prompter.claude

import com.flowveo.prompter.data.ScriptStats

/** What "Polish with Claude" should do to the script. */
enum class PolishPreset(val label: String) {
    POLISH("Polish for speaking"),
    TIGHTEN("Tighten"),
    HOOK("Hook first line"),
    SEC_30("Make it 30 seconds"),
    SEC_60("Make it 60 seconds"),
    SEC_90("Make it 90 seconds"),
}

/** Prompt text and response clean-up for the polish feature. Pure, unit-tested. */
object PolishPrompts {

    const val SYSTEM = """You rewrite video scripts so they sound natural when read aloud from a teleprompter.

Rules:
- Keep the speaker's meaning, facts, names and key phrases. Do not add claims that are not in the original.
- Use short sentences and a natural spoken rhythm. Plain words over formal ones.
- Insert a pause marker "//" (two slashes) at natural breaks, usually at the end of a sentence that deserves a beat. Keep any existing "//" markers that still make sense.
- Keep paragraph breaks as blank lines.
- Reply with the rewritten script only: no title, no introduction, no notes, no quotation marks around it, no markdown."""

    /** The user message: what to do, then the script between tags. */
    fun userMessage(script: String, preset: PolishPreset): String {
        val task = when (preset) {
            PolishPreset.POLISH -> "Polish this script for speaking."
            PolishPreset.TIGHTEN -> "Tighten this script: cut filler and repetition, keep every key point."
            PolishPreset.HOOK -> "Rewrite only the opening so the first line is a strong hook that makes viewers keep watching. Keep the rest close to the original, polished for speaking."
            PolishPreset.SEC_30 -> durationTask(30)
            PolishPreset.SEC_60 -> durationTask(60)
            PolishPreset.SEC_90 -> durationTask(90)
        }
        return "$task\n\n<script>\n${script.trim()}\n</script>"
    }

    /** About [ScriptStats.READ_WPM] words per minute of speaking. */
    fun targetWords(seconds: Int): Int = seconds * ScriptStats.READ_WPM / 60

    private fun durationTask(seconds: Int) =
        "Rewrite this script so it takes about $seconds seconds to say: roughly ${targetWords(seconds)} words. " +
            "Keep the most important points and the call to action, drop the rest."

    /**
     * Removes wrappers a model sometimes adds despite the instructions: code fences,
     * `<script>` tags, a "Here's the rewritten script:" line, or quotes around the whole text.
     */
    fun cleanResponse(raw: String): String {
        var t = raw.trim()
        t = t.removePrefix("```text").removePrefix("```markdown").removePrefix("```").removeSuffix("```").trim()
        t = t.removePrefix("<script>").removeSuffix("</script>").trim()
        val firstLine = t.lineSequence().firstOrNull().orEmpty()
        if (firstLine.endsWith(":") && Regex("^(here|sure|okay|ok)\\b", RegexOption.IGNORE_CASE).containsMatchIn(firstLine)) {
            t = t.substringAfter('\n', "").trim()
        }
        if (t.length > 1 && ((t.startsWith('"') && t.endsWith('"')) || (t.startsWith('“') && t.endsWith('”')))) {
            t = t.substring(1, t.length - 1).trim()
        }
        return t
    }
}
