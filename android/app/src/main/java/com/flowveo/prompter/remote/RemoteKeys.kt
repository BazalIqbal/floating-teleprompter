package com.flowveo.prompter.remote

import android.view.KeyEvent
import com.flowveo.prompter.data.KeyAction

/**
 * Which overlay action a hardware key triggers. Pure, unit-tested.
 *
 * Volume keys follow the user's choice in Settings › Controls. Bluetooth clickers usually
 * send volume, media, page or arrow keys; those have fixed meanings.
 */
object RemoteKeys {

    fun actionFor(keyCode: Int, volumeUp: KeyAction, volumeDown: KeyAction): KeyAction? = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> volumeUp
        KeyEvent.KEYCODE_VOLUME_DOWN -> volumeDown
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DPAD_CENTER,
        -> KeyAction.PLAY_PAUSE
        KeyEvent.KEYCODE_MEDIA_NEXT,
        KeyEvent.KEYCODE_PAGE_DOWN,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_RIGHT,
        -> KeyAction.LINE_FORWARD
        KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        KeyEvent.KEYCODE_PAGE_UP,
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_LEFT,
        -> KeyAction.LINE_BACK
        else -> null
    }
}
