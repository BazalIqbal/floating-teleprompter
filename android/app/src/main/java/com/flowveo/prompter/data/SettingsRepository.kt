package com.flowveo.prompter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class FontChoice { MONO, SANS }

/** Every setting with its working default. */
data class AppSettings(
    // Overlay
    val panelOpacityPct: Int = 70,
    val fontSizeSp: Int = 26,
    val font: FontChoice = FontChoice.MONO,
    val lineSpacing: Float = 1.4f,
    val readingLinePct: Int = 30,
    val fadeLines: Boolean = true,
    val mirror: Boolean = false,
    /** 0 = off, 3 or 5 seconds. */
    val countdownSec: Int = 3,
    // Scrolling
    val defaultMode: ScrollMode = ScrollMode.VOICE,
    val autoWpm: Int = 140,
    val pauseSec: Float = 1.5f,
    val autoFallback: Boolean = true,
    // Controls (the Accessibility service itself is switched on in Android settings)
    val volumeUpAction: KeyAction = KeyAction.PLAY_PAUSE,
    val volumeDownAction: KeyAction = KeyAction.LINE_BACK,
    // Camera
    val openCameraOnStart: Boolean = true,
    /** Null = the phone's default camera. Otherwise a package name. */
    val cameraPackage: String? = null,
    // Claude (the API key itself is in SecretStore, encrypted)
    /** Model id chosen from the user's model list; null until a key is set. */
    val claudeModel: String? = null,
    // Developer
    val micTestStrip: Boolean = false,
) {
    companion object {
        val OPACITY_RANGE = 30..95
        val FONT_RANGE = 16..48
        val WPM_RANGE = 60..250
        val READING_LINE_RANGE = 15..50
        val LINE_SPACING_RANGE = 1.0f..2.0f
        val PAUSE_RANGE = 0.5f..5f
        val COUNTDOWN_OPTIONS = listOf(0, 3, 5)
    }
}

/** Saved overlay position and size for one orientation (px). */
data class OverlayGeometry(val x: Int, val y: Int, val width: Int, val height: Int)

/** Settings in DataStore. Local only, no cloud. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val s = transform(prefs.toSettings())
            prefs[K.opacity] = s.panelOpacityPct
            prefs[K.fontSize] = s.fontSizeSp
            prefs[K.font] = s.font.name
            prefs[K.lineSpacing] = s.lineSpacing
            prefs[K.readingLine] = s.readingLinePct
            prefs[K.fade] = s.fadeLines
            prefs[K.mirror] = s.mirror
            prefs[K.countdown] = s.countdownSec
            prefs[K.mode] = s.defaultMode.name
            prefs[K.wpm] = s.autoWpm
            prefs[K.pause] = s.pauseSec
            prefs[K.fallback] = s.autoFallback
            prefs[K.volUp] = s.volumeUpAction.name
            prefs[K.volDown] = s.volumeDownAction.name
            prefs[K.openCamera] = s.openCameraOnStart
            if (s.cameraPackage == null) prefs.remove(K.cameraPackage) else prefs[K.cameraPackage] = s.cameraPackage
            prefs[K.micTest] = s.micTestStrip
            if (s.claudeModel == null) prefs.remove(K.claudeModel) else prefs[K.claudeModel] = s.claudeModel
        }
    }

    // ---- overlay position, remembered per orientation

    suspend fun overlayGeometry(landscape: Boolean): OverlayGeometry? {
        val p = store.data.first()
        val keys = geometryKeys(landscape)
        val values = keys.map { p[it] ?: return null }
        return OverlayGeometry(values[0], values[1], values[2], values[3])
    }

    suspend fun saveOverlayGeometry(landscape: Boolean, g: OverlayGeometry) {
        val keys = geometryKeys(landscape)
        store.edit {
            it[keys[0]] = g.x
            it[keys[1]] = g.y
            it[keys[2]] = g.width
            it[keys[3]] = g.height
        }
    }

    // ---- one-time sample script

    suspend fun consumeFirstRun(): Boolean {
        var first = false
        store.edit {
            if (it[K.seeded] != true) {
                first = true
                it[K.seeded] = true
            }
        }
        return first
    }

    private fun geometryKeys(landscape: Boolean): List<Preferences.Key<Int>> {
        val o = if (landscape) "land" else "port"
        return listOf("x", "y", "w", "h").map { intPreferencesKey("overlay_${o}_$it") }
    }

    private fun Preferences.toSettings(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            panelOpacityPct = this[K.opacity] ?: d.panelOpacityPct,
            fontSizeSp = this[K.fontSize] ?: d.fontSizeSp,
            font = enumOr(this[K.font], d.font),
            lineSpacing = this[K.lineSpacing] ?: d.lineSpacing,
            readingLinePct = this[K.readingLine] ?: d.readingLinePct,
            fadeLines = this[K.fade] ?: d.fadeLines,
            mirror = this[K.mirror] ?: d.mirror,
            countdownSec = this[K.countdown] ?: d.countdownSec,
            defaultMode = enumOr(this[K.mode], d.defaultMode),
            autoWpm = this[K.wpm] ?: d.autoWpm,
            pauseSec = this[K.pause] ?: d.pauseSec,
            autoFallback = this[K.fallback] ?: d.autoFallback,
            volumeUpAction = enumOr(this[K.volUp], d.volumeUpAction),
            volumeDownAction = enumOr(this[K.volDown], d.volumeDownAction),
            openCameraOnStart = this[K.openCamera] ?: d.openCameraOnStart,
            cameraPackage = this[K.cameraPackage],
            micTestStrip = this[K.micTest] ?: d.micTestStrip,
            claudeModel = this[K.claudeModel],
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default

    private object K {
        val opacity = intPreferencesKey("panel_opacity")
        val fontSize = intPreferencesKey("font_size")
        val font = stringPreferencesKey("font")
        val lineSpacing = floatPreferencesKey("line_spacing")
        val readingLine = intPreferencesKey("reading_line")
        val fade = booleanPreferencesKey("fade_lines")
        val mirror = booleanPreferencesKey("mirror")
        val countdown = intPreferencesKey("countdown")
        val mode = stringPreferencesKey("default_mode")
        val wpm = intPreferencesKey("auto_wpm")
        val pause = floatPreferencesKey("pause_sec")
        val fallback = booleanPreferencesKey("auto_fallback")
        val volUp = stringPreferencesKey("volume_up_action")
        val volDown = stringPreferencesKey("volume_down_action")
        val openCamera = booleanPreferencesKey("open_camera")
        val cameraPackage = stringPreferencesKey("camera_package")
        val micTest = booleanPreferencesKey("mic_test_strip")
        val claudeModel = stringPreferencesKey("claude_model")
        val seeded = booleanPreferencesKey("seeded_sample")
    }
}
