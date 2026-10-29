package com.flowveo.prompter.scroll

/**
 * Pure auto-scroll maths (no Android types, so it is unit-tested on the JVM).
 *
 * The script is laid out as one tall block of text. Auto mode moves it up at a
 * constant speed derived from words per minute: if the whole script is
 * [contentHeightPx] tall and has [totalWords] words, one word is on average
 * `contentHeightPx / totalWords` pixels of scroll, so
 * `px/s = (wpm / 60) * (contentHeightPx / totalWords)`.
 *
 * A `//` pause marker in the script holds the scroll for a fixed time when the
 * reading line reaches it.
 */
object AutoScroller {

    /** Scroll speed in pixels per second. Returns 0 when there is nothing to scroll. */
    fun pixelsPerSecond(wpm: Int, totalWords: Int, contentHeightPx: Float): Float {
        if (wpm <= 0 || totalWords <= 0 || contentHeightPx <= 0f) return 0f
        return (wpm / 60f) * (contentHeightPx / totalWords)
    }

    /** Where the scroll is, plus how long it still has to hold at a pause marker. */
    data class Position(
        val px: Float,
        val holdRemainingSec: Float = 0f,
        /** Index into the sorted marker list of the next marker not yet passed. */
        val nextMarker: Int = 0,
    )

    /**
     * Advances the scroll by [dtSec] seconds.
     *
     * @param markersPx scroll positions (sorted ascending) at which a `//` marker
     *   sits on the reading line.
     * @param holdSec how long each marker holds the scroll.
     * @param maxPx the end of the script; the position never goes past it.
     */
    fun step(
        pos: Position,
        dtSec: Float,
        speedPxPerSec: Float,
        markersPx: List<Float>,
        holdSec: Float,
        maxPx: Float,
    ): Position {
        var remainingDt = dtSec
        var hold = pos.holdRemainingSec
        var px = pos.px
        var next = pos.nextMarker

        // 1. Spend time on any hold that is still running.
        if (hold > 0f) {
            val used = minOf(hold, remainingDt)
            hold -= used
            remainingDt -= used
        }

        // 2. Move, stopping at the next marker if we reach it during this step.
        if (remainingDt > 0f && speedPxPerSec > 0f) {
            val target = px + speedPxPerSec * remainingDt
            val marker = markersPx.getOrNull(next)
            if (marker != null && marker in px..target) {
                px = marker
                next += 1
                // The rest of this step's time counts towards the hold.
                val timeToMarker = (marker - pos.px).coerceAtLeast(0f) / speedPxPerSec
                val leftover = (remainingDt - timeToMarker).coerceAtLeast(0f)
                hold = (holdSec - leftover).coerceAtLeast(0f)
            } else {
                px = target
            }
        }

        return Position(px.coerceAtMost(maxPx), hold, next)
    }

    /** Counts words the way a reader would: whitespace-separated, ignoring `//` markers. */
    fun countWords(script: String): Int =
        script.split(Regex("\\s+")).count { it.isNotBlank() && it != PAUSE_MARKER }

    const val PAUSE_MARKER = "//"
}
