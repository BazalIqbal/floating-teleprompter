package com.flowveo.prompter.scroll

/**
 * Turns the raw script into what the overlay shows. A `//` pause marker is removed from
 * the visible text and its character position is kept,
 * so the overlay can find which line it sits on and hold the scroll there.
 */
object ScriptText {

    data class Prepared(
        val display: String,
        /** Character offsets in [display] where a pause marker was. */
        val markerOffsets: List<Int>,
        val words: Int,
    )

    fun prepare(raw: String): Prepared {
        val out = StringBuilder()
        val markers = mutableListOf<Int>()
        var i = 0
        while (i < raw.length) {
            if (raw.startsWith(AutoScroller.PAUSE_MARKER, i)) {
                // Drop the marker and any spaces just before it.
                while (out.isNotEmpty() && out.last() == ' ') out.setLength(out.length - 1)
                markers += (out.length - 1).coerceAtLeast(0)
                i += AutoScroller.PAUSE_MARKER.length
            } else {
                out.append(raw[i])
                i++
            }
        }
        return Prepared(out.toString().trimEnd(), markers, AutoScroller.countWords(raw))
    }
}
