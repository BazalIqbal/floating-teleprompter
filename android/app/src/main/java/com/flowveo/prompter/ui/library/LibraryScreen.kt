package com.flowveo.prompter.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowveo.prompter.data.ScriptEntity
import com.flowveo.prompter.data.ScriptRepository
import com.flowveo.prompter.data.ScriptStats
import com.flowveo.prompter.ui.common.Glyph
import com.flowveo.prompter.ui.common.GlyphButton
import com.flowveo.prompter.ui.common.GlyphIcon
import com.flowveo.prompter.ui.common.PillButton
import com.flowveo.prompter.ui.common.SurfaceCard
import com.flowveo.prompter.ui.common.TopBar
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.ui.theme.PrompterFonts
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUi(
    val pinned: ScriptEntity? = null,
    val scripts: List<ScriptEntity> = emptyList(),
    val total: Int = 0,
    val query: String = "",
)

@HiltViewModel
class LibraryViewModel @Inject constructor(private val repo: ScriptRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    val ui = combine(repo.observeAll(), query) { all, q ->
        val filtered = if (q.isBlank()) all else all.filter {
            it.title.contains(q, ignoreCase = true) || it.body.contains(q, ignoreCase = true)
        }
        LibraryUi(
            pinned = all.firstOrNull { it.lastUsedAt != null }.takeIf { q.isBlank() },
            scripts = filtered,
            total = all.size,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUi())

    init {
        viewModelScope.launch { repo.seedSampleIfFirstRun() }
    }

    fun setQuery(q: String) {
        query.value = q
    }

    fun create(onCreated: (Long) -> Unit) = viewModelScope.launch { onCreated(repo.create()) }

    fun duplicate(id: Long) = viewModelScope.launch { repo.duplicate(id) }

    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

/** Home screen: the script library. */
@Composable
fun LibraryScreen(
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onStart: (Long) -> Unit,
    onSettings: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var searching by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ScriptEntity?>(null) }

    Box(
        Modifier
            .fillMaxSize()
            .background(PrompterColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                TopBar("Prompter") {
                    GlyphButton(Glyph.Search, "Search", { searching = !searching; if (!searching) vm.setQuery("") })
                    GlyphButton(Glyph.Settings, "Settings", onSettings)
                }
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = ui.query,
                        onValueChange = vm::setQuery,
                        placeholder = { Text("Search title or text") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrompterColors.Accent,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                        ),
                    )
                }
            }
            ui.pinned?.let { pinned ->
                item { PinnedCard(pinned, onOpen = { onOpen(pinned.id) }, onStart = { onStart(pinned.id) }) }
            }
            item {
                Text(
                    if (ui.query.isBlank()) "All scripts · ${ui.total}" else "${ui.scripts.size} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = PrompterColors.Text2,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            if (ui.scripts.isEmpty()) {
                item {
                    Text(
                        if (ui.query.isBlank()) "No scripts yet. Tap New script, or share text to Prompter from any app." else "Nothing matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrompterColors.Muted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(ui.scripts, key = { it.id }) { script ->
                ScriptCard(
                    script = script,
                    onClick = { onOpen(script.id) },
                    onEdit = { onEdit(script.id) },
                    onDuplicate = { vm.duplicate(script.id) },
                    onDelete = { toDelete = script },
                )
            }
        }

        ExtendedFloatingActionButton(
            onClick = { vm.create(onEdit) },
            containerColor = PrompterColors.Accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = { GlyphIcon(Glyph.Plus, color = Color.White, size = 16.dp) },
            text = { Text("New script", fontFamily = PrompterFonts.Sans) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        )
    }

    toDelete?.let { script ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete script?") },
            text = { Text("\"${script.title.ifBlank { "Untitled" }}\" will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(script.id); toDelete = null }) { Text("Delete", color = PrompterColors.Warning) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PinnedCard(script: ScriptEntity, onOpen: () -> Unit, onStart: () -> Unit) {
    SurfaceCard(highlighted = true, modifier = Modifier.combinedClickable(onClick = onOpen)) {
        Text("LAST USED", style = MaterialTheme.typography.labelSmall, color = PrompterColors.AccentText)
        Text(script.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium, fontSize = 20.sp, color = PrompterColors.Text)
        Text(
            ScriptStats.firstLine(script.body, 120),
            fontFamily = PrompterFonts.Mono,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = PrompterColors.Text2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${ScriptStats.summary(script.body)} · ${script.mode.label()}",
                style = MaterialTheme.typography.bodySmall,
                color = PrompterColors.Text2,
                modifier = Modifier.weight(1f),
            )
            PillButton("Start", onStart, glyph = Glyph.Play)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScriptCard(
    script: ScriptEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        SurfaceCard(modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = { menu = true })) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    script.title.ifBlank { "Untitled" },
                    style = MaterialTheme.typography.titleMedium,
                    color = PrompterColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(lastUsed(script), style = MaterialTheme.typography.bodySmall, color = PrompterColors.Muted)
            }
            Text(
                ScriptStats.firstLine(script.body).ifEmpty { "Empty script" },
                fontFamily = PrompterFonts.Mono,
                fontSize = 12.sp,
                color = PrompterColors.Text2,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(ScriptStats.summary(script.body), style = MaterialTheme.typography.bodySmall, color = PrompterColors.Muted)
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { menu = false; onDuplicate() })
            DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
        }
        Spacer(Modifier.height(0.dp))
    }
}

private fun lastUsed(script: ScriptEntity): String =
    script.lastUsedAt?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Not used yet"

fun com.flowveo.prompter.data.ScrollMode.label() = if (this == com.flowveo.prompter.data.ScrollMode.VOICE) "Voice" else "Auto"
