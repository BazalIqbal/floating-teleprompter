package com.flowveo.prompter.ui.editor

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.flowveo.prompter.claude.ClaudeClient
import com.flowveo.prompter.claude.PolishPreset
import com.flowveo.prompter.claude.SecretStore
import com.flowveo.prompter.data.SettingsRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowveo.prompter.data.ScriptEntity
import com.flowveo.prompter.data.ScriptRepository
import com.flowveo.prompter.data.ScriptStats
import com.flowveo.prompter.di.AppScope
import com.flowveo.prompter.scroll.AutoScroller
import com.flowveo.prompter.ui.common.TopBar
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.ui.theme.PrompterFonts
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorUi(
    val loaded: Boolean = false,
    val title: String = "",
    val body: TextFieldValue = TextFieldValue(""),
    val saved: Boolean = true,
)

/** Polish with Claude: what the editor is showing. */
data class PolishUi(
    /** True when an API key and model are set, so the button is shown. */
    val available: Boolean = false,
    val working: Boolean = false,
    /** Side-by-side compare: the original text and Claude's rewrite (editable before accepting). */
    val compare: Pair<String, String>? = null,
    /** Text before the last accepted polish, for Undo. */
    val undoBody: String? = null,
    val error: String? = null,
)

@HiltViewModel
class EditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ScriptRepository,
    @AppScope private val appScope: CoroutineScope,
    private val secrets: SecretStore,
    private val settings: SettingsRepository,
    private val claude: ClaudeClient,
) : ViewModel() {

    private val _polish = MutableStateFlow(PolishUi())
    val polish = _polish.asStateFlow()

    init {
        viewModelScope.launch {
            combine(secrets.apiKey, settings.settings) { key, s -> key != null && s.claudeModel != null }
                .collect { ok -> _polish.update { it.copy(available = ok) } }
        }
    }

    /** Sends the script to Claude with [preset]; the result opens in the compare view. */
    fun startPolish(preset: PolishPreset) = viewModelScope.launch {
        val key = secrets.current()
        val model = settings.current().claudeModel
        val text = _ui.value.body.text
        if (key == null || model == null || text.isBlank()) return@launch
        _polish.update { it.copy(working = true, error = null) }
        claude.polish(key, model, text, preset)
            .onSuccess { polished -> _polish.update { it.copy(working = false, compare = text to polished) } }
            .onFailure { e -> _polish.update { it.copy(working = false, error = e.message) } }
    }

    fun editPolished(text: String) = _polish.update { p -> p.copy(compare = p.compare?.let { it.first to text }) }

    /** Accept: replace the script (autosaves) and offer Undo until leaving the editor. */
    fun acceptPolish() {
        val (original, polished) = _polish.value.compare ?: return
        _polish.update { it.copy(compare = null, undoBody = original) }
        change { it.copy(body = TextFieldValue(polished)) }
    }

    fun rejectPolish() = _polish.update { it.copy(compare = null) }

    fun undoPolish() {
        val before = _polish.value.undoBody ?: return
        _polish.update { it.copy(undoBody = null) }
        change { it.copy(body = TextFieldValue(before)) }
    }

    fun dismissPolishError() = _polish.update { it.copy(error = null) }

    private val id: Long = checkNotNull(savedState["id"])
    private var original: ScriptEntity? = null
    private var saveJob: Job? = null

    private val _ui = MutableStateFlow(EditorUi())
    val ui = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            val s = repo.get(id) ?: return@launch
            original = s
            _ui.value = EditorUi(loaded = true, title = s.title, body = TextFieldValue(s.body))
        }
    }

    fun setTitle(t: String) = change { it.copy(title = t) }

    fun setBody(v: TextFieldValue) = change { it.copy(body = v) }

    /** Inserts text at the cursor (used by Paste and the `//` pause button). */
    fun insert(text: String) = change {
        val v = it.body
        val start = v.selection.min
        val end = v.selection.max
        val newText = v.text.substring(0, start) + text + v.text.substring(end)
        it.copy(body = TextFieldValue(newText, TextRange(start + text.length)))
    }

    fun clear() = change { it.copy(body = TextFieldValue("")) }

    /** Autosaves 400 ms after the last change. */
    private fun change(transform: (EditorUi) -> EditorUi) {
        val before = _ui.value
        val after = transform(before)
        val textChanged = before.title != after.title || before.body.text != after.body.text
        _ui.value = if (textChanged) after.copy(saved = false) else after
        if (!textChanged) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            saveNow()
        }
    }

    private suspend fun saveNow() {
        val o = original ?: return
        val u = _ui.value
        // An empty title falls back to the script's first line.
        val title = if (u.title.isBlank() && u.body.text.isBlank()) "" else ScriptStats.titleFor(u.title, u.body.text)
        val updated = o.copy(title = title, body = u.body.text)
        repo.save(updated)
        original = updated
        _ui.value = _ui.value.copy(saved = true)
    }

    /**
     * Called when leaving the editor. Runs in the app scope so it finishes even though this
     * screen (and its viewModelScope) is about to go away. A script left completely empty is deleted.
     */
    fun flush() {
        saveJob?.cancel()
        val u = _ui.value
        appScope.launch {
            if (u.loaded && u.title.isBlank() && u.body.text.isBlank()) repo.delete(id) else saveNow()
        }
    }
}

/** Script editor, with optional Polish with Claude. */
@Composable
fun EditorScreen(onBack: () -> Unit, onPreview: () -> Unit, vm: EditorViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // System back gesture: save first, like the on-screen back button.
    BackHandler {
        vm.flush()
        onBack()
    }
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }
    var pickPreset by remember { mutableStateOf(false) }
    val polish by vm.polish.collectAsStateWithLifecycle()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PrompterColors.Accent,
        unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
        focusedContainerColor = PrompterColors.Surface,
        unfocusedContainerColor = PrompterColors.Surface,
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(PrompterColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TopBar("Edit script", onBack = { vm.flush(); onBack() }) {
            Text(
                if (ui.saved) "Saved" else "Saving…",
                style = MaterialTheme.typography.bodySmall,
                color = if (ui.saved) PrompterColors.Success else PrompterColors.Muted,
                modifier = Modifier.padding(end = 8.dp),
            )
            TextButton(onClick = { vm.flush(); onPreview() }) { Text("Preview", color = PrompterColors.AccentText) }
        }

        OutlinedTextField(
            value = ui.title,
            onValueChange = vm::setTitle,
            label = { Text("Title") },
            placeholder = { Text("Defaults to the first line") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolButton("Paste") { clipboardText(context)?.let(vm::insert) }
            ToolButton("Clear") { confirmClear = true }
            ToolButton("// pause", mono = true) { vm.insert(" ${AutoScroller.PAUSE_MARKER} ") }
        }
        if (polish.available || polish.undoBody != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (polish.available) {
                    OutlinedButton(
                        onClick = { pickPreset = true },
                        enabled = !polish.working && ui.body.text.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, PrompterColors.Accent.copy(alpha = 0.5f)),
                    ) {
                        Text(if (polish.working) "Polishing…" else "✦ Polish with Claude", color = PrompterColors.AccentText, fontSize = 13.sp)
                    }
                }
                if (polish.undoBody != null) {
                    TextButton(onClick = vm::undoPolish) { Text("Undo polish", color = PrompterColors.Text2) }
                }
                if (polish.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = PrompterColors.Accent)
            }
        }

        OutlinedTextField(
            value = ui.body,
            onValueChange = vm::setBody,
            placeholder = { Text("Paste or type your script. Add // where you want a short pause.") },
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(20.dp),
            textStyle = TextStyle(fontFamily = PrompterFonts.Mono, fontSize = 15.sp, lineHeight = 24.sp, color = PrompterColors.Text),
            colors = fieldColors,
        )

        val words = ScriptStats.words(ui.body.text)
        val pauses = Regex(Regex.escape(AutoScroller.PAUSE_MARKER)).findAll(ui.body.text).count()
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$words words", style = MaterialTheme.typography.bodySmall, color = PrompterColors.Text2)
            Text("${ScriptStats.formatDuration(ScriptStats.readTimeSec(words))} at 150 wpm", style = MaterialTheme.typography.bodySmall, color = PrompterColors.Text2)
            Text("$pauses pauses", style = MaterialTheme.typography.bodySmall, color = PrompterColors.Text2)
        }
    }

    if (pickPreset) {
        AlertDialog(
            onDismissRequest = { pickPreset = false },
            title = { Text("Polish with Claude") },
            text = {
                Column {
                    PolishPreset.entries.forEach { preset ->
                        TextButton(onClick = { pickPreset = false; vm.startPolish(preset) }, modifier = Modifier.fillMaxWidth()) {
                            Text(preset.label, color = PrompterColors.Text, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickPreset = false }) { Text("Cancel") } },
        )
    }

    polish.compare?.let { (original, polished) ->
        CompareDialog(original, polished, vm::editPolished, onAccept = vm::acceptPolish, onReject = vm::rejectPolish)
    }

    polish.error?.let { msg ->
        AlertDialog(
            onDismissRequest = vm::dismissPolishError,
            title = { Text("Couldn't polish") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = vm::dismissPolishError) { Text("OK") } },
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear the script text?") },
            confirmButton = { TextButton(onClick = { vm.clear(); confirmClear = false }) { Text("Clear", color = PrompterColors.Warning) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

/** Original and Claude's rewrite, stacked (a phone is too narrow for side by side). Accept or Reject. */
@Composable
private fun CompareDialog(
    original: String,
    polished: String,
    onEdit: (String) -> Unit,
    onAccept: () -> Unit,
    onReject: () -> Unit,
) {
    Dialog(onDismissRequest = onReject, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .background(PrompterColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Compare", style = MaterialTheme.typography.headlineMedium, color = PrompterColors.Text)
            Text("ORIGINAL · ${ScriptStats.summary(original)}", style = MaterialTheme.typography.labelSmall, color = PrompterColors.Muted)
            Text(
                original,
                fontFamily = PrompterFonts.Mono,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = PrompterColors.Text2,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(PrompterColors.Surface, RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            )
            Text("POLISHED · ${ScriptStats.summary(polished)} · you can edit it", style = MaterialTheme.typography.labelSmall, color = PrompterColors.AccentText)
            OutlinedTextField(
                value = polished,
                onValueChange = onEdit,
                textStyle = TextStyle(fontFamily = PrompterFonts.Mono, fontSize = 13.sp, lineHeight = 19.sp, color = PrompterColors.Text),
                modifier = Modifier.fillMaxWidth().weight(1.3f),
                shape = RoundedCornerShape(16.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onReject, modifier = Modifier.weight(1f).height(48.dp)) { Text("Reject", color = PrompterColors.Text) }
                Button(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrompterColors.Accent),
                ) { Text("Accept") }
            }
        }
    }
}

@Composable
private fun ToolButton(label: String, mono: Boolean = false, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
        Text(label, color = PrompterColors.Text, fontFamily = if (mono) PrompterFonts.Mono else PrompterFonts.Sans, fontSize = 13.sp)
    }
}

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(ClipboardManager::class.java)
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}
