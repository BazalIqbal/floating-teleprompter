package com.flowveo.prompter.data

/** What a remote key does on the overlay (Settings › Controls). */
enum class KeyAction(val label: String) {
    PLAY_PAUSE("Play / pause"),
    LINE_BACK("Back one line"),
    LINE_FORWARD("Forward one line"),
    SPEED_UP("Speed +"),
    SPEED_DOWN("Speed −"),
}
