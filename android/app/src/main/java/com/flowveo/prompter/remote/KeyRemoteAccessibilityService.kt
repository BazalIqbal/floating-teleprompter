package com.flowveo.prompter.remote

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import com.flowveo.prompter.data.AppSettings
import com.flowveo.prompter.data.KeyAction
import com.flowveo.prompter.data.SettingsRepository
import com.flowveo.prompter.scroll.Command
import com.flowveo.prompter.scroll.ScrollEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Optional Accessibility service (off by default) that lets the volume keys and a Bluetooth
 * clicker control the overlay while the camera app is in front. The camera app would
 * otherwise get the key presses first.
 *
 * It only reads key presses, never screen content, and only acts while the overlay is
 * showing. At any other time every key passes straight through.
 */
@AndroidEntryPoint
class KeyRemoteAccessibilityService : AccessibilityService() {

    @Inject lateinit var engine: ScrollEngine
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var current = AppSettings()

    override fun onServiceConnected() {
        super.onServiceConnected()
        scope.launch { settings.settings.collect { current = it } }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!engine.overlayActive) return false
        val action = RemoteKeys.actionFor(event.keyCode, current.volumeUpAction, current.volumeDownAction) ?: return false
        // Act once per press (key down, no auto-repeat), but swallow the whole press so the
        // camera app doesn't also react (e.g. volume-key shutter or volume change).
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) perform(action)
        return true
    }

    private fun perform(action: KeyAction) {
        when (action) {
            KeyAction.PLAY_PAUSE -> engine.remotePlayPause()
            KeyAction.LINE_BACK -> engine.send(Command.LineBack)
            KeyAction.LINE_FORWARD -> engine.send(Command.LineForward)
            KeyAction.SPEED_UP -> engine.changeSpeed(+10)
            KeyAction.SPEED_DOWN -> engine.changeSpeed(-10)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        /** True when the user has switched the service on in Android's Accessibility settings. */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, KeyRemoteAccessibilityService::class.java)
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
