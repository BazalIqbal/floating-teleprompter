package com.flowveo.prompter.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.flowveo.prompter.MainActivity
import com.flowveo.prompter.R
import com.flowveo.prompter.data.ScriptRepository
import com.flowveo.prompter.data.SettingsRepository
import com.flowveo.prompter.data.ScrollMode
import com.flowveo.prompter.scroll.PromptState
import com.flowveo.prompter.scroll.ScrollEngine
import com.flowveo.prompter.scroll.VoiceTracker
import com.flowveo.prompter.ui.theme.PrompterTheme
import com.flowveo.prompter.util.Permissions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the floating script alive while the camera app is in
 * front. It owns the overlay window, applies lock/minimize to it, remembers its position
 * per orientation, and runs the voice tracker while the script is in Voice mode.
 *
 * A ComposeView outside an Activity needs a lifecycle and a saved-state owner, so the
 * service provides both.
 */
@AndroidEntryPoint
class OverlayService : LifecycleService(), SavedStateRegistryOwner {

    @Inject lateinit var engine: ScrollEngine
    @Inject lateinit var scripts: ScriptRepository
    @Inject lateinit var settings: SettingsRepository

    private val savedStateController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var window: OverlayWindowManager
    private var voiceTracker: VoiceTracker? = null
    /** The tracker's state for the panel; null while not in Voice mode. */
    private val voiceState = MutableStateFlow<StateFlow<VoiceTracker.State>?>(null)
    private var autoFallback = true
    private val config = MutableStateFlow<OverlayConfig?>(null)
    private var scriptId: Long = -1
    private var watchJobs = emptyList<Job>()

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        window = OverlayWindowManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(EXTRA_SCRIPT_ID, -1))
            ACTION_TOGGLE -> engine.togglePause()
            ACTION_UNLOCK -> engine.unlock()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun start(id: Long) {
        // Must be called right away (within 5 s of startForegroundService), before any loading,
        // and while the app is still on screen: Android 14 only grants the microphone type then.
        // Claiming it up front means Voice mode can also be switched on later from the panel.
        startInForeground(useMic = Permissions.hasMic(this))
        lifecycleScope.launch {
            val script = scripts.get(id)
            if (script == null) {
                stopSelf()
                return@launch
            }
            val s = settings.current()
            scriptId = id
            autoFallback = s.autoFallback
            engine.configure(script.speedWpm, script.fontSizeSp, script.mode, s.countdownSec)
            config.value = OverlayConfig(
                text = script.body,
                font = s.font,
                lineSpacing = s.lineSpacing,
                opacityPct = s.panelOpacityPct,
                readingLinePct = s.readingLinePct,
                fadeLines = s.fadeLines,
                mirror = s.mirror,
                pauseSec = s.pauseSec,
                devStrip = s.micTestStrip,
            )

            if (!window.isShowing) window.show(createView(), settings.overlayGeometry(window.isLandscape))
            engine.overlayActive = true
            window.setLocked(false)
            watchEngine()
        }
    }

    /** Applies lock/minimize to the window, keeps the notification in sync, saves per-script changes. */
    private fun watchEngine() {
        watchJobs.forEach { it.cancel() }
        watchJobs = listOf(
            lifecycleScope.launch {
                engine.ui.map { it.locked }.distinctUntilChanged().collect { locked ->
                    window.setLocked(locked)
                    updateNotification(locked)
                }
            },
            lifecycleScope.launch {
                engine.ui.map { it.minimized }.distinctUntilChanged().collect { minimized ->
                    if (minimized) window.minimize() else window.restore()
                }
            },
            lifecycleScope.launch {
                // Voice mode needs the tracker; Auto mode doesn't use the mic at all.
                engine.ui.map { it.mode }.distinctUntilChanged().collect { mode ->
                    if (mode == ScrollMode.VOICE) startVoice() else stopVoice()
                }
            },
            lifecycleScope.launch {
                // Every new take starts listening afresh (so a blocked mic is detected again).
                engine.ui.map { it.state }.distinctUntilChanged().collect { state ->
                    if (state == PromptState.Ready) voiceTracker?.resetTake()
                }
            },
            lifecycleScope.launch {
                // Each script remembers its own speed, font size and mode.
                engine.ui.map { Triple(it.wpm, it.fontSizeSp, it.mode) }.distinctUntilChanged().drop(1).collect { (wpm, size, mode) ->
                    scripts.get(scriptId)?.let { scripts.save(it.copy(speedWpm = wpm, fontSizeSp = size, mode = mode)) }
                }
            },
        )
    }

    /** Starts listening (needs the mic permission; otherwise Voice falls back to Auto). */
    private fun startVoice() {
        if (voiceTracker != null) return
        val tracker = VoiceTracker(this).also { it.start() }
        voiceTracker = tracker
        voiceState.value = tracker.state
        voiceJob?.cancel()
        voiceJob = lifecycleScope.launch {
            // Can't hear us: scroll like Auto instead, if the setting allows.
            tracker.state.map { it.blocked }.distinctUntilChanged().collect { blocked ->
                engine.setVoiceFallback(blocked && autoFallback)
            }
        }
    }

    private fun stopVoice() {
        voiceJob?.cancel()
        voiceJob = null
        voiceTracker?.stop()
        voiceTracker = null
        voiceState.value = null
        engine.setVoiceFallback(false)
    }

    private var voiceJob: Job? = null

    private fun createView() = ComposeView(this).apply {
        setViewTreeLifecycleOwner(this@OverlayService)
        setViewTreeSavedStateRegistryOwner(this@OverlayService)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            val cfg by config.collectAsState()
            val ui by engine.ui.collectAsState()
            val position = engine.position.collectAsState()
            val voice by voiceState.collectAsState()
            val c = cfg ?: return@setContent
            PrompterTheme {
                OverlayContent(
                    config = c,
                    ui = ui,
                    position = { position.value },
                    voice = voice,
                    commands = engine.commands,
                    actions = OverlayActions(
                        onTap = engine::onTap,
                        onStartFromVoice = engine::startFromVoice,
                        onCountdownTick = engine::countdownTick,
                        onPosition = engine::setPosition,
                        onFinished = engine::finish,
                        onResumeIfFinished = engine::resumeIfFinished,
                        onTogglePause = engine::togglePause,
                        onRestart = engine::restart,
                        onClose = ::stopSelf,
                        onSpeed = engine::changeSpeed,
                        onFontSize = engine::changeFontSize,
                        onToggleMode = engine::toggleMode,
                        onLock = engine::lock,
                        onMinimize = engine::minimize,
                        onRestore = engine::restore,
                        onMove = window::moveBy,
                        onResize = window::resizeBy,
                        onDragEnd = ::saveGeometry,
                    ),
                )
            }
        }
    }

    /** Position and size are remembered per orientation. */
    private fun saveGeometry() {
        val g = window.geometry() ?: return
        val landscape = window.isLandscape
        lifecycleScope.launch { settings.saveOverlayGeometry(landscape, g) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!window.isShowing) return
        lifecycleScope.launch { window.reposition(settings.overlayGeometry(window.isLandscape)) }
    }

    override fun onDestroy() {
        engine.overlayActive = false
        watchJobs.forEach { it.cancel() }
        stopVoice()
        window.remove()
        engine.restart()
        engine.unlock()
        engine.restore()
        super.onDestroy()
    }

    private fun startInForeground(useMic: Boolean) {
        val notification = buildNotification(locked = engine.ui.value.locked)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val special = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            try {
                startForeground(NOTIFICATION_ID, notification, special or (if (useMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0))
            } catch (e: SecurityException) {
                // Microphone type refused (app not visible): keep the overlay; Voice may then fall back to Auto.
                startForeground(NOTIFICATION_ID, notification, special)
            }
        } else {
            // Android 12/13 have no "specialUse" type; the manifest types apply.
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(locked: Boolean) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(locked))
    }

    private fun buildNotification(locked: Boolean): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(if (locked) R.string.notif_title_locked else R.string.notif_title))
            .setContentText(getString(if (locked) R.string.notif_text_locked else R.string.notif_text))
            .setContentIntent(openApp)
            .setOngoing(true)
        // Unlock lives here because a locked panel ignores all touches.
        if (locked) builder.addAction(action(R.string.notif_action_unlock, ACTION_UNLOCK, 3))
        builder.addAction(action(R.string.notif_action_toggle, ACTION_TOGGLE, 1))
        builder.addAction(action(R.string.notif_action_close, ACTION_STOP, 2))
        return builder.build()
    }

    private fun action(label: Int, action: String, requestCode: Int) =
        Notification.Action.Builder(null, getString(label), servicePending(action, requestCode)).build()

    private fun servicePending(action: String, requestCode: Int) = PendingIntent.getService(
        this, requestCode, Intent(this, OverlayService::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val CHANNEL_ID = "overlay"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.flowveo.prompter.START"
        const val ACTION_TOGGLE = "com.flowveo.prompter.TOGGLE"
        const val ACTION_UNLOCK = "com.flowveo.prompter.UNLOCK"
        const val ACTION_STOP = "com.flowveo.prompter.STOP"
        const val EXTRA_SCRIPT_ID = "script_id"

        fun start(context: Context, scriptId: Long) {
            context.startForegroundService(
                Intent(context, OverlayService::class.java).setAction(ACTION_START).putExtra(EXTRA_SCRIPT_ID, scriptId),
            )
        }
    }
}
