package com.flowveo.prompter.scroll

/**
 * Detects a *blocked* microphone, as opposed to a quiet room.
 *
 * Since Android 10, when another app records with a privacy-sensitive source
 * (a camera app recording video), our capture keeps running but receives
 * digital silence: the level sits perfectly flat at the floor. A real room,
 * even a quiet one, always has a little noise, so the level wobbles.
 *
 * Rule: report "blocked" when every level seen in the last [windowMs] stays
 * within [flatRangeDb] of each other, and no speech has been heard yet in this
 * take. Once speech has been heard the mic clearly works, so a later pause in
 * talking must never flip the mode.
 *
 * Pure Kotlin, unit-tested on the JVM.
 */
class DigitalSilenceDetector(
    private val windowMs: Long = 3_000,
    private val flatRangeDb: Float = 0.5f,
) {
    private val samples = ArrayDeque<Pair<Long, Float>>()
    private var speechHeard = false

    /** True once speech was detected in this take. */
    val hasHeardSpeech: Boolean get() = speechHeard

    /** Call when the recognizer returns any text (or the level clearly shows a voice). */
    fun onSpeech() {
        speechHeard = true
    }

    /** Feeds one level reading (dB, any scale) taken at [timeMs]; returns the current verdict. */
    fun onLevel(levelDb: Float, timeMs: Long): Boolean {
        samples.addLast(timeMs to levelDb)
        // Keep just enough history to cover the window.
        while (samples.size > 1 && timeMs - samples[1].first >= windowMs) {
            samples.removeFirst()
        }
        return isBlocked(timeMs)
    }

    /** Current verdict without adding a sample. */
    fun isBlocked(nowMs: Long): Boolean {
        if (speechHeard || samples.isEmpty()) return false
        val coveredMs = nowMs - samples.first().first
        if (coveredMs < windowMs) return false
        val min = samples.minOf { it.second }
        val max = samples.maxOf { it.second }
        return max - min <= flatRangeDb
    }

    /** Starts a new take. */
    fun reset() {
        samples.clear()
        speechHeard = false
    }
}
