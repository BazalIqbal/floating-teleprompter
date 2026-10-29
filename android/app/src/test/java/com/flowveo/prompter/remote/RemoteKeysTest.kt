package com.flowveo.prompter.remote

import android.view.KeyEvent
import com.flowveo.prompter.data.KeyAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteKeysTest {

    private fun action(key: Int) = RemoteKeys.actionFor(key, KeyAction.PLAY_PAUSE, KeyAction.LINE_BACK)

    @Test
    fun volumeKeysFollowSettings() {
        assertEquals(KeyAction.PLAY_PAUSE, action(KeyEvent.KEYCODE_VOLUME_UP))
        assertEquals(KeyAction.LINE_BACK, action(KeyEvent.KEYCODE_VOLUME_DOWN))
        assertEquals(KeyAction.SPEED_UP, RemoteKeys.actionFor(KeyEvent.KEYCODE_VOLUME_UP, KeyAction.SPEED_UP, KeyAction.SPEED_DOWN))
    }

    @Test
    fun clickerKeysHaveFixedMeanings() {
        assertEquals(KeyAction.PLAY_PAUSE, action(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(KeyAction.PLAY_PAUSE, action(KeyEvent.KEYCODE_ENTER))
        assertEquals(KeyAction.LINE_FORWARD, action(KeyEvent.KEYCODE_PAGE_DOWN))
        assertEquals(KeyAction.LINE_FORWARD, action(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(KeyAction.LINE_BACK, action(KeyEvent.KEYCODE_PAGE_UP))
        assertEquals(KeyAction.LINE_BACK, action(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun otherKeysPassThrough() {
        assertNull(action(KeyEvent.KEYCODE_A))
        assertNull(action(KeyEvent.KEYCODE_POWER))
        assertNull(action(KeyEvent.KEYCODE_BACK))
    }
}
