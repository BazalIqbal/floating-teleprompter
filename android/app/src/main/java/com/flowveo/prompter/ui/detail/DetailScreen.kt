package com.flowveo.prompter.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.flowveo.prompter.data.AppSettings
import com.flowveo.prompter.data.ScriptEntity
import com.flowveo.prompter.data.ScriptRepository
import com.flowveo.prompter.data.ScriptStats
import com.flowveo.prompter.data.ScrollMode
import com.flowveo.prompter.data.SettingsRepository
import com.flowveo.prompter.scroll.ScriptText
import com.flowveo.prompter.ui.common.Glyph
import com.flowveo.prompter.ui.common.GlyphButton
import com.flowveo.prompter.ui.common.LabeledRow
import com.flowveo.prompter.ui.common.OverlayPreview
import com.flowveo.prompter.ui.common.PillButton
import com.flowveo.prompter.ui.common.RowDivider
import com.flowveo.prompter.ui.common.Segmented
import com.flowveo.prompter.ui.common.Stepper
import com.flowveo.prompter.ui.common.SurfaceCard
import com.flowveo.prompter.ui.common.SwitchRow
import com.flowveo.prompter.ui.common.TopBar
import com.flowveo.prompter.ui.theme.PrompterColors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUi(val script: ScriptEntity? = null, val settings: AppSettings = AppSettings())

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: ScriptRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    private val id: Long = checkNotNull(savedState["id"])

    val ui = combine(repo.observe(id), settingsRepo.settings) { s, settings -> DetailUi(s, settings) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUi())

    /** Quick settings change this script only (each script remembers its own mode, speed and size). */
    fun updateScript(transform: (ScriptEntity) -> ScriptEntity) = viewModelScope.launch {
        ui.value.script?.let { repo.save(transform(it)) }
    }

    fun updateSettings(transform: (AppSettings) -> AppSettings) = viewModelScope.launch { settingsRepo.update(transform) }
}

/** Pre-flight screen. */
@Composable
fun DetailScreen(onBack: () -> Unit, onEdit: () -> Unit, onStart: () -> Unit, vm: DetailViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val script = ui.script ?: return
    val settings = ui.settings
    val preview = ScriptText.prepare(script.body).display.lineSequence().take(12).joinToString("\n")

    Column(
        Modifier
            .fillMaxSize()
            .background(PrompterColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        TopBar(script.title.ifBlank { "Untitled" }, onBack = onBack) {
            GlyphButton(Glyph.Edit, "Edit script", onEdit)
        }

        OverlayPreview(
            text = preview.ifBlank { "This script is empty. Tap the pencil to add text." },
            font = settings.font,
            fontSizeSp = script.fontSizeSp,
            lineSpacing = settings.lineSpacing,
            mirror = settings.mirror,
        )
        Text(
            "${ScriptStats.summary(script.body)} · as it will look on the overlay",
            style = MaterialTheme.typography.bodySmall,
            color = PrompterColors.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        SurfaceCard {
            LabeledRow("Mode", if (script.mode == ScrollMode.VOICE) "Follows your voice; switches to Auto if it can't hear you" else "Scrolls at a steady speed") {
                Segmented(listOf(ScrollMode.VOICE to "Voice", ScrollMode.AUTO to "Auto"), script.mode) { m -> vm.updateScript { it.copy(mode = m) } }
            }
            RowDivider()
            LabeledRow("Speed", if (script.mode == ScrollMode.VOICE) "Used for Auto fallback" else null) {
                Stepper(
                    "${script.speedWpm} wpm",
                    onMinus = { vm.updateScript { it.copy(speedWpm = (it.speedWpm - 10).coerceIn(AppSettings.WPM_RANGE)) } },
                    onPlus = { vm.updateScript { it.copy(speedWpm = (it.speedWpm + 10).coerceIn(AppSettings.WPM_RANGE)) } },
                    minusEnabled = script.speedWpm > AppSettings.WPM_RANGE.first,
                    plusEnabled = script.speedWpm < AppSettings.WPM_RANGE.last,
                    label = "speed",
                )
            }
            RowDivider()
            LabeledRow("Font size") {
                Stepper(
                    "${script.fontSizeSp} sp",
                    onMinus = { vm.updateScript { it.copy(fontSizeSp = (it.fontSizeSp - 2).coerceIn(AppSettings.FONT_RANGE)) } },
                    onPlus = { vm.updateScript { it.copy(fontSizeSp = (it.fontSizeSp + 2).coerceIn(AppSettings.FONT_RANGE)) } },
                    minusEnabled = script.fontSizeSp > AppSettings.FONT_RANGE.first,
                    plusEnabled = script.fontSizeSp < AppSettings.FONT_RANGE.last,
                    label = "font size",
                )
            }
            RowDivider()
            SwitchRow("Mirror text", "For a physical prompter glass", settings.mirror) { on -> vm.updateSettings { it.copy(mirror = on) } }
        }

        SurfaceCard {
            SwitchRow(
                "Open camera on Start",
                if (settings.cameraPackage == null) "Your default camera, video mode · change in Settings" else "Opens the app chosen in Settings",
                settings.openCameraOnStart,
            ) { on -> vm.updateSettings { it.copy(openCameraOnStart = on) } }
        }

        Spacer(Modifier.height(4.dp))
        PillButton("Start prompting", onStart, modifier = Modifier.fillMaxWidth(), big = true, glyph = Glyph.Play)
        Spacer(Modifier.height(8.dp))
    }
}
