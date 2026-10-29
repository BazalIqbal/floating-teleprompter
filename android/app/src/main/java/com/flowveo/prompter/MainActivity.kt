package com.flowveo.prompter

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.flowveo.prompter.data.ScriptRepository
import com.flowveo.prompter.data.SettingsRepository
import com.flowveo.prompter.overlay.OverlayService
import com.flowveo.prompter.ui.detail.DetailScreen
import com.flowveo.prompter.ui.editor.EditorScreen
import com.flowveo.prompter.ui.library.LibraryScreen
import com.flowveo.prompter.ui.settings.SettingsScreen
import com.flowveo.prompter.ui.setup.SetupScreen
import com.flowveo.prompter.ui.theme.PrompterTheme
import com.flowveo.prompter.util.CameraLauncher
import com.flowveo.prompter.util.Permissions
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Single activity hosting all screens:
 * setup -> library -> (editor | pre-flight) -> overlay + camera; plus settings.
 * Also receives text shared from other apps (Share sheet -> new script).
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var scripts: ScriptRepository
    @Inject lateinit var settings: SettingsRepository

    /** A script just created from shared text; the UI opens it in the editor. */
    private var importedScriptId by mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            PrompterTheme {
                val nav = rememberNavController()
                val start = if (Permissions.canDrawOverlays(this)) Routes.LIBRARY else Routes.SETUP

                // Permissions can be revoked in Settings: send the user back to setup if the overlay one goes.
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    if (!Permissions.canDrawOverlays(this@MainActivity) && nav.currentDestination?.route != Routes.SETUP) {
                        nav.navigate(Routes.SETUP)
                    }
                }
                LaunchedEffect(importedScriptId) {
                    importedScriptId?.let {
                        nav.navigate(Routes.editor(it))
                        importedScriptId = null
                    }
                }

                NavHost(navController = nav, startDestination = start) {
                    composable(Routes.SETUP) {
                        SetupScreen(onContinue = {
                            if (!nav.popBackStack()) nav.navigate(Routes.LIBRARY) { popUpTo(Routes.SETUP) { inclusive = true } }
                        })
                    }
                    composable(Routes.LIBRARY) {
                        LibraryScreen(
                            onOpen = { nav.navigate(Routes.detail(it)) },
                            onEdit = { nav.navigate(Routes.editor(it)) },
                            onStart = ::startPrompting,
                            onSettings = { nav.navigate(Routes.SETTINGS) },
                        )
                    }
                    composable(Routes.EDITOR, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments!!.getLong("id")
                        EditorScreen(
                            onBack = { nav.popBackStack() },
                            onPreview = { nav.navigate(Routes.detail(id)) { popUpTo(Routes.LIBRARY) } },
                        )
                    }
                    composable(Routes.DETAIL, arguments = listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                        val id = entry.arguments!!.getLong("id")
                        DetailScreen(
                            onBack = { nav.popBackStack() },
                            onEdit = { nav.navigate(Routes.editor(id)) },
                            onStart = { startPrompting(id) },
                        )
                    }
                    composable(Routes.SETTINGS) {
                        SettingsScreen(onBack = { nav.popBackStack() }, onPermissions = { nav.navigate(Routes.SETUP) })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    /** Text shared from Notes, Docs, ChatGPT/Claude, etc. becomes a new script. */
    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type?.startsWith("text/") != true) return
        // Any app can share to us: accept plain text only, and cap its size.
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty().take(MAX_SHARED_CHARS)
        if (text.isEmpty()) return
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        lifecycleScope.launch { importedScriptId = scripts.create(subject, text) }
    }

    /**
     * Start shows the overlay first, then opens the camera from here (an Activity), because
     * Android blocks a background service from starting activities.
     */
    private fun startPrompting(scriptId: Long) {
        if (!Permissions.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow \"Display over other apps\" first", Toast.LENGTH_LONG).show()
            return
        }
        lifecycleScope.launch {
            scripts.markUsed(scriptId)
            val s = settings.current()
            OverlayService.start(this@MainActivity, scriptId)
            if (s.openCameraOnStart && !CameraLauncher.open(this@MainActivity, s.cameraPackage)) {
                Toast.makeText(this@MainActivity, "No camera app found. Open your camera manually.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private companion object {
        /** About 15,000 words: far more than any script, small enough to stay fast. */
        const val MAX_SHARED_CHARS = 100_000
    }

    private object Routes {
        const val SETUP = "setup"
        const val LIBRARY = "library"
        const val EDITOR = "editor/{id}"
        const val DETAIL = "detail/{id}"
        const val SETTINGS = "settings"
        fun editor(id: Long) = "editor/$id"
        fun detail(id: Long) = "detail/$id"
    }
}
