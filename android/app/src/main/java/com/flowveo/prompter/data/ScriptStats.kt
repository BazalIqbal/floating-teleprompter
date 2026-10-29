package com.flowveo.prompter.data

import com.flowveo.prompter.scroll.AutoScroller

/** Small pure helpers for script cards and the editor (unit-tested). */
object ScriptStats {

    /** Read-time estimates use ~150 words per minute. */
    const val READ_WPM = 150

    fun words(body: String): Int = AutoScroller.countWords(body)

    fun readTimeSec(words: Int, wpm: Int = READ_WPM): Int =
        if (words <= 0 || wpm <= 0) 0 else (words * 60 + wpm - 1) / wpm

    /** "~57 s", "~2 min 45 s", "~3 min". */
    fun formatDuration(seconds: Int): String {
        if (seconds < 60) return "~$seconds s"
        val min = seconds / 60
        val sec = seconds % 60
        return if (sec == 0) "~$min min" else "~$min min $sec s"
    }

    /** "142 words · ~57 s". */
    fun summary(body: String): String {
        val w = words(body)
        return "$w ${if (w == 1) "word" else "words"} · ${formatDuration(readTimeSec(w))}"
    }

    /** First non-blank line without pause markers, used as a default title and as the card preview. */
    fun firstLine(body: String, maxChars: Int = 80): String {
        val line = body.lineSequence()
            .map { it.replace(AutoScroller.PAUSE_MARKER, "").trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
        return if (line.length <= maxChars) line else line.take(maxChars - 1).trimEnd() + "…"
    }

    /** Title for a new or imported script: the given title if any, else the first line, else "Untitled". */
    fun titleFor(given: String?, body: String): String {
        val t = given?.trim().orEmpty()
        if (t.isNotEmpty()) return t.take(80)
        return firstLine(body, maxChars = 60).ifEmpty { "Untitled" }
    }
}
