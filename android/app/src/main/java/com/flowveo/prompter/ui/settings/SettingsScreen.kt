package com.flowveo.prompter.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.flowveo.prompter.claude.ClaudeClient
import com.flowveo.prompter.claude.ModelChoice
import com.flowveo.prompter.claude.ModelOption
import com.flowveo.prompter.claude.SecretStore
import com.flowveo.prompter.data.ScriptRepository
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowveo.prompter.BuildConfig
import com.flowveo.prompter.data.AppSettings
import com.flowveo.prompter.data.FontChoice
import com.flowveo.prompter.data.KeyAction
import com.flowveo.prompter.data.ScrollMode
import com.flowveo.prompter.data.SettingsRepository
import com.flowveo.prompter.ui.common.LabeledRow
import com.flowveo.prompter.ui.common.RowDivider
import com.flowveo.prompter.ui.common.SectionLabel
import com.flowveo.prompter.ui.common.Segmented
import com.flowveo.prompter.ui.common.Stepper
import com.flowveo.prompter.ui.common.SurfaceCard
import com.flowveo.prompter.ui.common.SwitchRow
import com.flowveo.prompter.ui.common.TopBar
import com.flowveo.prompter.ui.common.clickableNoRipple
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.remote.KeyRemoteAccessibilityService
import com.flowveo.prompter.util.CameraLauncher
import com.flowveo.prompter.util.Permissions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val secrets: SecretStore,
    private val claude: ClaudeClient,
    private val scripts: ScriptRepository,
) : ViewModel() {
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val apiKey = secrets.apiKey.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Models for the picker, loaded on demand with the saved key. */
    val models = MutableStateFlow<List<ModelOption>>(emptyList())
    /** "Checking key…", errors, import results: one-line messages for the user. */
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    fun update(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { repo.update(transform) }

    /** Checks the key by listing its models; saves it and picks the newest Sonnet if it works. */
    fun saveKey(key: String, onDone: () -> Unit) = viewModelScope.launch {
        busy.value = true
        claude.listModels(key.trim())
            .onSuccess { list ->
                secrets.save(key)
                models.value = list
                val keep = settings.value.claudeModel?.takeIf { id -> list.any { it.id == id } }
                repo.update { it.copy(claudeModel = keep ?: ModelChoice.pickDefault(list)?.id) }
                message.value = "Key saved. ${list.size} models available."
                onDone()
            }
            .onFailure { message.value = it.message }
        busy.value = false
    }

    fun removeKey() = viewModelScope.launch {
        secrets.clear()
        repo.update { it.copy(claudeModel = null) }
        models.value = emptyList()
    }

    fun loadModels() = viewModelScope.launch {
        val key = secrets.current() ?: return@launch
        busy.value = true
        claude.listModels(key).onSuccess { models.value = it }.onFailure { message.value = it.message }
        busy.value = false
    }

    fun exportTo(write: suspend (String) -> Unit) = viewModelScope.launch {
        runCatching { write(scripts.exportJson()) }
            .onSuccess { message.value = "Scripts exported." }
            .onFailure { message.value = "Export failed: ${it.message}" }
    }

    fun importFrom(read: suspend () -> String) = viewModelScope.launch {
        runCatching { scripts.importJson(read()) }
            .onSuccess { r -> message.value = "Imported ${r.added} scripts" + if (r.skipped > 0) ", skipped ${r.skipped} already here." else "." }
            .onFailure { message.value = it.message ?: "Import failed." }
    }
}

/** Settings. Every value has a working default. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onPermissions: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pickCamera by remember { mutableStateOf(false) }
    var explainRemote by remember { mutableStateOf(false) }
    var pickKey by remember { mutableStateOf<KeyPick?>(null) }
    // The Accessibility switch lives in Android settings, so re-check whenever we come back.
    var remoteOn by remember { mutableStateOf(KeyRemoteAccessibilityService.isEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { remoteOn = KeyRemoteAccessibilityService.isEnabled(context) }
    val set = vm::update
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var editKey by remember { mutableStateOf(false) }
    var pickModel by remember { mutableStateOf(false) }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            vm.message.value = null
        }
    }
    // Backup files go through the system file picker, so no storage permission is needed.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.exportTo { json ->
            withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)!!.use { it.write(json.toByteArray()) } }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFrom {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    // A backup of even thousands of scripts is well under this; refuse anything bigger.
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        require(out.size() <= MAX_BACKUP_BYTES) { "That file is too large to be a Prompter backup." }
                    }
                    out.toByteArray().decodeToString()
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(PrompterColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TopBar("Settings", onBack = onBack)

        Group("Overlay") {
            SliderRow("Panel opacity", "${s.panelOpacityPct}%", s.panelOpacityPct.toFloat(), 30f..95f, steps = 12) { v ->
                set { it.copy(panelOpacityPct = v.roundToInt()) }
            }
            RowDivider()
            LabeledRow("Font size", "Default for new scripts") {
                Stepper(
                    "${s.fontSizeSp} sp",
                    onMinus = { set { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceIn(AppSettings.FONT_RANGE)) } },
                    onPlus = { set { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceIn(AppSettings.FONT_RANGE)) } },
                    label = "font size",
                )
            }
            RowDivider()
            LabeledRow("Font") {
                Segmented(listOf(FontChoice.MONO to "JetBrains Mono", FontChoice.SANS to "Inter"), s.font) { f -> set { it.copy(font = f) } }
            }
            RowDivider()
            SliderRow("Line spacing", "%.1f".format(s.lineSpacing), s.lineSpacing, 1f..2f, steps = 9) { v ->
                set { it.copy(lineSpacing = (v * 10).roundToInt() / 10f) }
            }
            RowDivider()
            SliderRow("Reading line", "${s.readingLinePct}% from top", s.readingLinePct.toFloat(), 15f..50f, steps = 6) { v ->
                set { it.copy(readingLinePct = (v / 5).roundToInt() * 5) }
            }
            RowDivider()
            SwitchRow("Fade past / future lines", checked = s.fadeLines) { on -> set { it.copy(fadeLines = on) } }
            RowDivider()
            SwitchRow("Mirror text", "For a physical prompter glass", s.mirror) { on -> set { it.copy(mirror = on) } }
            RowDivider()
            LabeledRow("Countdown") {
                Segmented(AppSettings.COUNTDOWN_OPTIONS.map { it to if (it == 0) "Off" else "$it s" }, s.countdownSec) { c -> set { it.copy(countdownSec = c) } }
            }
        }

        Group("Scrolling") {
            LabeledRow("Default mode", "For new scripts. Voice follows your speech and starts when you start talking.") {
                Segmented(listOf(ScrollMode.VOICE to "Voice", ScrollMode.AUTO to "Auto"), s.defaultMode) { m -> set { it.copy(defaultMode = m) } }
            }
            RowDivider()
            LabeledRow("Auto speed", "Default for new scripts") {
                Stepper(
                    "${s.autoWpm} wpm",
                    onMinus = { set { it.copy(autoWpm = (it.autoWpm - 10).coerceIn(AppSettings.WPM_RANGE)) } },
                    onPlus = { set { it.copy(autoWpm = (it.autoWpm + 10).coerceIn(AppSettings.WPM_RANGE)) } },
                    label = "speed",
                )
            }
            RowDivider()
            SliderRow("Pause marker // length", "%.1f s".format(s.pauseSec), s.pauseSec, 0.5f..5f, steps = 8) { v ->
                set { it.copy(pauseSec = (v * 2).roundToInt() / 2f) }
            }
            RowDivider()
            SwitchRow("Auto when mic is blocked", "Switch to Auto if voice tracking can't hear you", s.autoFallback) { on ->
                set { it.copy(autoFallback = on) }
            }
        }

        Group("Controls") {
            Column(Modifier.fillMaxWidth().clickableNoRipple { explainRemote = true }) {
                LabeledRow(
                    "Volume / remote keys",
                    if (remoteOn) "On · volume keys and Bluetooth clickers control the script" else "Off · needs an optional Accessibility service",
                ) { Text(if (remoteOn) "On" else "Set up", color = PrompterColors.AccentText) }
            }
            RowDivider()
            Column(Modifier.fillMaxWidth().clickableNoRipple { pickKey = KeyPick.UP }) {
                LabeledRow("Volume up", s.volumeUpAction.label) { Text("Change", color = PrompterColors.AccentText) }
            }
            RowDivider()
            Column(Modifier.fillMaxWidth().clickableNoRipple { pickKey = KeyPick.DOWN }) {
                LabeledRow("Volume down", s.volumeDownAction.label) { Text("Change", color = PrompterColors.AccentText) }
            }
            Text(
                "Tip: in Samsung Camera, turn off Settings › Shooting methods › Press Volume keys to… so the keys reach Prompter.",
                style = MaterialTheme.typography.bodySmall,
                color = PrompterColors.Muted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Group("Camera") {
            SwitchRow("Open camera on Start", checked = s.openCameraOnStart) { on -> set { it.copy(openCameraOnStart = on) } }
            RowDivider()
            Column(Modifier.fillMaxWidth().clickableNoRipple { pickCamera = true }) {
                LabeledRow("Camera app", CameraLauncher.labelFor(context, s.cameraPackage)) {
                    Text("Change", color = PrompterColors.AccentText)
                }
            }
        }

        Group("Claude") {
            Column(Modifier.fillMaxWidth().clickableNoRipple { editKey = true }) {
                LabeledRow("API key", apiKey?.let(::maskKey) ?: "Not set · needed for Polish with Claude") {
                    Text(if (apiKey == null) "Set" else "Change", color = PrompterColors.AccentText)
                }
            }
            if (apiKey != null) {
                RowDivider()
                Column(Modifier.fillMaxWidth().clickableNoRipple { pickModel = true; vm.loadModels() }) {
                    LabeledRow("Model", s.claudeModel ?: "Choose a model") { Text("Change", color = PrompterColors.AccentText) }
                }
            }
            Text(
                "Your script is sent to Anthropic only when you tap Polish in the editor. This is the only part of Prompter that uses the internet.",
                style = MaterialTheme.typography.bodySmall,
                color = PrompterColors.Muted,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }

        Group("About") {
            Column(Modifier.fillMaxWidth().clickableNoRipple { exportLauncher.launch("prompter-backup-${LocalDate.now()}.json") }) {
                LabeledRow("Export scripts", "Save all scripts as a JSON backup file") { Text("Export", color = PrompterColors.AccentText) }
            }
            RowDivider()
            Column(Modifier.fillMaxWidth().clickableNoRipple { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                LabeledRow("Import scripts", "Restore from a backup file; duplicates are skipped") { Text("Import", color = PrompterColors.AccentText) }
            }
            RowDivider()
            Column(Modifier.fillMaxWidth().clickableNoRipple(onPermissions)) {
                LabeledRow("Permissions", "Overlay, microphone, notifications, battery") { Text("Open", color = PrompterColors.AccentText) }
            }
            RowDivider()
            SwitchRow("Mic test strip", "Developer: show what voice tracking hears on the overlay", s.micTestStrip) { on ->
                set { it.copy(micTestStrip = on) }
            }
            RowDivider()
            LabeledRow("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") {}
        }
    }

    if (editKey) {
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!busy) editKey = false },
            title = { Text("Anthropic API key") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Create a key at console.anthropic.com › API keys. It's stored encrypted on this phone and never included in backups.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it },
                        singleLine = true,
                        placeholder = { Text("sk-ant-…") },
                        visualTransformation = PasswordVisualTransformation(),
                        // Password keyboard: no suggestions, and the keyboard doesn't learn the key.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (busy) Text("Checking the key…", style = MaterialTheme.typography.bodySmall, color = PrompterColors.Muted)
                }
            },
            confirmButton = {
                TextButton(enabled = key.isNotBlank() && !busy, onClick = { vm.saveKey(key) { editKey = false } }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (apiKey != null) TextButton(onClick = { vm.removeKey(); editKey = false }) { Text("Remove", color = PrompterColors.Warning) }
                    TextButton(onClick = { editKey = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (pickModel) {
        AlertDialog(
            onDismissRequest = { pickModel = false },
            title = { Text("Model") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (models.isEmpty()) Text(if (busy) "Loading models…" else "No models loaded.", color = PrompterColors.Muted)
                    models.forEach { m ->
                        CameraOption("${m.name}  ·  ${m.id}", m.id == s.claudeModel) {
                            set { it.copy(claudeModel = m.id) }
                            pickModel = false
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickModel = false }) { Text("Close") } },
        )
    }

    if (explainRemote) {
        AlertDialog(
            onDismissRequest = { explainRemote = false },
            title = { Text("Volume keys and clickers") },
            text = {
                Text(
                    "While your camera app is open it gets the key presses first. Prompter's optional " +
                        "Accessibility service lets the volume keys and a Bluetooth clicker pause, resume and move " +
                        "the script instead. It only reads key presses, only while the script is showing, and never " +
                        "reads your screen.\n\n" +
                        "1. Tap Open Accessibility, find Prompter remote keys under Installed apps, and turn it on.\n" +
                        "2. If Android says the setting is restricted (it does for apps not installed from the Play Store): " +
                        "tap App info, then the ⋮ menu › Allow restricted settings, and try step 1 again.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    explainRemote = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open Accessibility") }
            },
            dismissButton = {
                TextButton(onClick = {
                    explainRemote = false
                    context.startActivity(Permissions.appDetailsIntent(context))
                }) { Text("App info") }
            },
        )
    }

    pickKey?.let { which ->
        val selected = if (which == KeyPick.UP) s.volumeUpAction else s.volumeDownAction
        AlertDialog(
            onDismissRequest = { pickKey = null },
            title = { Text(if (which == KeyPick.UP) "Volume up" else "Volume down") },
            text = {
                Column {
                    KeyAction.entries.forEach { action ->
                        CameraOption(action.label, action == selected) {
                            set { if (which == KeyPick.UP) it.copy(volumeUpAction = action) else it.copy(volumeDownAction = action) }
                            pickKey = null
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickKey = null }) { Text("Close") } },
        )
    }

    if (pickCamera) {
        val apps = remember { CameraLauncher.installedApps(context) }
        AlertDialog(
            onDismissRequest = { pickCamera = false },
            title = { Text("Camera app") },
            text = {
                Column {
                    CameraOption("Phone default", s.cameraPackage == null) { set { it.copy(cameraPackage = null) }; pickCamera = false }
                    apps.forEach { app ->
                        CameraOption(app.label, s.cameraPackage == app.packageName) { set { it.copy(cameraPackage = app.packageName) }; pickCamera = false }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickCamera = false }) { Text("Close") } },
        )
    }
}

private enum class KeyPick { UP, DOWN }

private const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

/** "sk-ant-…abcd": enough to recognise the key without showing it. */
private fun maskKey(key: String) = if (key.length <= 8) "••••" else "${key.take(6)}…${key.takeLast(4)}"

@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title, Modifier.padding(top = 8.dp))
    SurfaceCard(content = content)
}

@Composable
private fun SliderRow(title: String, value: String, current: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        LabeledRow(title) { Text(value, color = PrompterColors.Text2, style = MaterialTheme.typography.bodyMedium) }
        Slider(
            value = current.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(thumbColor = PrompterColors.Accent, activeTrackColor = PrompterColors.Accent),
        )
    }
}

@Composable
private fun CameraOption(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier.fillMaxWidth().clickableNoRipple(onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, color = PrompterColors.Text)
    }
}
