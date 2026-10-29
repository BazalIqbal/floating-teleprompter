package com.flowveo.prompter.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowveo.prompter.data.FontChoice
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.ui.theme.PrompterFonts

// Small shared building blocks for the in-app screens.

/** Simple stroke icons drawn in code (no icon library needed). */
enum class Glyph { Back, Search, Settings, Plus, Edit, Close, Play }

@Composable
fun GlyphIcon(glyph: Glyph, modifier: Modifier = Modifier, color: Color = PrompterColors.Text, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) { drawGlyph(glyph, color) }
}

private fun DrawScope.drawGlyph(glyph: Glyph, color: Color) {
    val w = size.width
    val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun path(vararg points: Pair<Float, Float>) = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * w, y * w) else lineTo(x * w, y * w) }
    }
    when (glyph) {
        Glyph.Back -> drawPath(path(0.62f to 0.2f, 0.32f to 0.5f, 0.62f to 0.8f), color, style = stroke)
        Glyph.Search -> {
            drawCircle(color, radius = w * 0.28f, center = Offset(w * 0.45f, w * 0.45f), style = stroke)
            drawLine(color, Offset(w * 0.65f, w * 0.65f), Offset(w * 0.85f, w * 0.85f), stroke.width, StrokeCap.Round)
        }
        Glyph.Settings -> {
            drawLine(color, Offset(w * 0.15f, w * 0.32f), Offset(w * 0.85f, w * 0.32f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.15f, w * 0.68f), Offset(w * 0.85f, w * 0.68f), stroke.width, StrokeCap.Round)
            drawCircle(PrompterColors.Background, w * 0.11f, Offset(w * 0.35f, w * 0.32f))
            drawCircle(color, w * 0.11f, Offset(w * 0.35f, w * 0.32f), style = stroke)
            drawCircle(PrompterColors.Background, w * 0.11f, Offset(w * 0.65f, w * 0.68f))
            drawCircle(color, w * 0.11f, Offset(w * 0.65f, w * 0.68f), style = stroke)
        }
        Glyph.Plus -> {
            drawLine(color, Offset(w * 0.5f, w * 0.15f), Offset(w * 0.5f, w * 0.85f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.15f, w * 0.5f), Offset(w * 0.85f, w * 0.5f), stroke.width, StrokeCap.Round)
        }
        Glyph.Edit -> drawPath(path(0.18f to 0.82f, 0.22f to 0.62f, 0.68f to 0.16f, 0.84f to 0.32f, 0.38f to 0.78f, 0.18f to 0.82f), color, style = stroke)
        Glyph.Close -> {
            drawLine(color, Offset(w * 0.2f, w * 0.2f), Offset(w * 0.8f, w * 0.8f), stroke.width, StrokeCap.Round)
            drawLine(color, Offset(w * 0.8f, w * 0.2f), Offset(w * 0.2f, w * 0.8f), stroke.width, StrokeCap.Round)
        }
        Glyph.Play -> drawPath(path(0.28f to 0.15f, 0.82f to 0.5f, 0.28f to 0.85f).apply { close() }, color)
    }
}

@Composable
fun GlyphButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.semantics { contentDescription = description }) {
        GlyphIcon(glyph)
    }
}

/** Title row with optional back button and trailing actions. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) GlyphButton(Glyph.Back, "Back", onBack)
        Text(
            title,
            style = if (onBack == null) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleMedium,
            color = PrompterColors.Text,
            modifier = Modifier.weight(1f).padding(start = if (onBack == null) 4.dp else 0.dp),
            maxLines = 1,
        )
        actions()
    }
}

@Composable
fun SurfaceCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PrompterColors.Surface)
            .border(1.dp, if (highlighted) PrompterColors.Accent.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = PrompterColors.AccentText, modifier = modifier.padding(start = 6.dp))
}

@Composable
fun RowDivider() = HorizontalDivider(color = PrompterColors.Hairline)

/** "−  140 wpm  +" stepper. */
@Composable
fun Stepper(
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
    label: String = "",
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        StepButton("−", "Decrease $label", onMinus, minusEnabled)
        Text(value, fontFamily = PrompterFonts.Mono, fontSize = 13.sp, color = PrompterColors.Text, textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 72.dp))
        StepButton("+", "Increase $label", onPlus, plusEnabled)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit, enabled: Boolean) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PrompterColors.Surface2)
            .semantics { contentDescription = description },
    ) {
        Text(symbol, fontSize = 18.sp, color = if (enabled) PrompterColors.Text else PrompterColors.Muted)
    }
}

/** Two- or three-way segmented control (Voice / Auto, Off / 3 s / 5 s). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PrompterColors.Background)
            .padding(3.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) PrompterColors.Accent else Color.Transparent)
                    .semantics { role = Role.RadioButton }
                    .clickableNoRipple { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(label, color = if (on) Color.White else PrompterColors.Text2, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun LabeledRow(title: String, subtitle: String? = null, trailing: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = PrompterColors.Text)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = PrompterColors.Muted)
        }
        trailing()
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    LabeledRow(title, subtitle) {
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = PrompterColors.Accent))
    }
}

fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)

fun fontFamilyFor(choice: FontChoice): FontFamily = if (choice == FontChoice.MONO) PrompterFonts.Mono else PrompterFonts.Sans

/**
 * Read-only preview styled exactly like the overlay: dark glass, lens glow, centred text,
 * current line bright and the others faded (Pre-flight screen).
 */
@Composable
fun OverlayPreview(
    text: String,
    font: FontChoice,
    fontSizeSp: Int,
    lineSpacing: Float,
    mirror: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 240.dp,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Color(0xE605070C))
            .border(1.dp, PrompterColors.Accent.copy(alpha = 0.4f), shape)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to PrompterColors.Accent.copy(alpha = 0.55f),
                        0.4f to PrompterColors.Accent.copy(alpha = 0.15f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, -size.height * 0.05f),
                        radius = size.width * 0.55f,
                    ),
                )
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen, scaleX = if (mirror) -1f else 1f)
                .drawWithContent {
                    drawContent()
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.35f),
                            0.22f to Color.Black.copy(alpha = 0.35f),
                            0.3f to Color.Black,
                            0.45f to Color.Black,
                            0.55f to Color.Black.copy(alpha = 0.6f),
                            1f to Color.Black.copy(alpha = 0.2f),
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            Text(
                text = text,
                style = TextStyle(
                    fontFamily = fontFamilyFor(font),
                    fontWeight = FontWeight.Medium,
                    fontSize = (fontSizeSp * 0.75f).sp,
                    lineHeight = (fontSizeSp * 0.75f * lineSpacing).sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp),
            )
        }
        Box(Modifier.padding(top = 70.dp).size(width = 3.dp, height = 26.dp).background(PrompterColors.Accent, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)))
    }
}

@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, big: Boolean = false, glyph: Glyph? = null) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.height(if (big) 60.dp else 44.dp),
        shape = CircleShape,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = PrompterColors.Accent),
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, color = Color.White, size = if (big) 14.dp else 12.dp)
            Box(Modifier.size(10.dp))
        }
        Text(text, fontFamily = PrompterFonts.Sans, fontWeight = if (big) FontWeight.Bold else FontWeight.SemiBold, fontSize = if (big) 17.sp else 14.sp)
    }
}
