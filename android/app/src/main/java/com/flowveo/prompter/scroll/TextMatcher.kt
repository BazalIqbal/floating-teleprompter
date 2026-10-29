package com.flowveo.prompter.scroll

/**
 * Finds where the speaker is in the script from the speech recognizer's text
 *. Pure Kotlin so it is tuned with unit tests, not on the phone.
 *
 * Rules:
 *  - Look at the last few spoken words and line them up against the script, in a window
 *    of [WINDOW] words ahead of the current position.
 *  - Words match exactly, or with one letter different for words of 4+ letters
 *    (small recognition errors).
 *  - Only move when confident: at least 2 of the last 3 spoken words line up, and the
 *    matched words aren't all tiny ("and the").
 *  - Never move backwards, and never jump past the end of the next paragraph.
 *  - Anything else (ad-libs, chit-chat) returns null: the script stays where it is.
 */
class TextMatcher(val words: List<Word>) {

    /** One script word: normalized text, where it starts in the display text, its paragraph. */
    data class Word(val norm: String, val offset: Int, val paragraph: Int)

    val size get() = words.size

    /**
     * Index of the script word the speaker has just said, or null if not confident.
     * [current] is the index of the word currently on the reading line.
     */
    fun match(spoken: String, current: Int, window: Int = WINDOW): Int? {
        val said = normalizeAll(spoken).takeLast(TAIL)
        if (said.size < 2 || words.isEmpty()) return null
        val from = current.coerceIn(0, words.size - 1)
        val maxParagraph = words[from].paragraph + 1
        val to = minOf(words.size - 1, from + window)

        var best: Int? = null
        var bestScore = 0
        for (j in from..to) {
            if (words[j].paragraph > maxParagraph) break
            val last3 = alignedMatches(said, j, 3)
            if (last3.count < 2 || last3.chars < MIN_MATCHED_CHARS) continue
            val score = alignedMatches(said, j, said.size).count
            // Strictly better wins; ties keep the nearest (earliest) candidate.
            if (score > bestScore) {
                best = j
                bestScore = score
            }
        }
        return best
    }

    /** True when the speaker is saying the opening words of the script (Voice mode starts on speech). */
    fun matchesStart(spoken: String): Boolean = match(spoken, 0, window = START_WINDOW) != null

    private data class Score(val count: Int, val chars: Int)

    /** Lines up the last [n] spoken words so the final one sits on script word [end]. */
    private fun alignedMatches(said: List<String>, end: Int, n: Int): Score {
        var count = 0
        var chars = 0
        for (i in 0 until minOf(n, said.size)) {
            val w = end - i
            if (w < 0) break
            val s = said[said.size - 1 - i]
            if (wordsMatch(s, words[w].norm)) {
                count++
                chars += words[w].norm.length
            }
        }
        return Score(count, chars)
    }

    companion object {
        /** How far ahead (in words) a match may land. */
        const val WINDOW = 40
        private const val START_WINDOW = 8
        /** Spoken words considered from the end of the recognizer's text. */
        private const val TAIL = 5
        /** Matched words must add up to at least this many letters ("and the" isn't enough). */
        private const val MIN_MATCHED_CHARS = 6

        /** Builds the word list from the overlay's display text (pause markers already removed). */
        fun from(display: String): TextMatcher {
            val out = mutableListOf<Word>()
            var paragraph = 0
            var lastEnd = 0
            Regex("\\S+").findAll(display).forEach { m ->
                val gap = display.substring(lastEnd, m.range.first)
                if (out.isNotEmpty() && Regex("\\n\\s*\\n").containsMatchIn(gap)) paragraph++
                lastEnd = m.range.last + 1
                val norm = normalize(m.value)
                if (norm.isNotEmpty()) out += Word(norm, m.range.first, paragraph)
            }
            return TextMatcher(out)
        }

        /** Lowercase, letters and digits only ("Here's" -> "heres"). */
        fun normalize(word: String): String = word.lowercase().filter { it.isLetterOrDigit() }

        fun normalizeAll(text: String): List<String> =
            text.split(Regex("\\s+")).map(::normalize).filter { it.isNotEmpty() }

        fun wordsMatch(a: String, b: String): Boolean =
            a == b || (minOf(a.length, b.length) >= 4 && editDistanceAtMostOne(a, b))

        /** True when [a] and [b] differ by at most one insert, delete or substitution. */
        private fun editDistanceAtMostOne(a: String, b: String): Boolean {
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0
            var j = 0
            var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) {
                    i++
                    j++
                    continue
                }
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> {
                        i++
                        j++
                    }
                }
            }
            return edits + (a.length - i) + (b.length - j) <= 1
        }
    }
}
