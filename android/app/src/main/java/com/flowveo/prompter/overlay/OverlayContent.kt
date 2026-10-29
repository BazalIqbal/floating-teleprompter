package com.flowveo.prompter.overlay

import android.view.MotionEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flowveo.prompter.data.FontChoice
import com.flowveo.prompter.data.ScrollMode
import com.flowveo.prompter.scroll.AutoScroller
import com.flowveo.prompter.scroll.Command
import com.flowveo.prompter.scroll.PromptState
import com.flowveo.prompter.scroll.ScriptText
import com.flowveo.prompter.scroll.ScrollUiState
import com.flowveo.prompter.scroll.TextMatcher
import com.flowveo.prompter.scroll.VoiceTracker
import com.flowveo.prompter.ui.common.fontFamilyFor
import com.flowveo.prompter.ui.theme.PrompterColors
import com.flowveo.prompter.ui.theme.PrompterFonts
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How the panel looks, from Settings plus the script text. Speed, size and mode live in ScrollUiState. */
data class OverlayConfig(
    val text: String,
    val font: FontChoice = FontChoice.MONO,
    val lineSpacing: Float = 1.4f,
    val opacityPct: Int = 70,
    val readingLinePct: Int = 30,
    val fadeLines: Boolean = true,
    val mirror: Boolean = false,
    val pauseSec: Float = 1.5f,
    /** Developer strip (Settings › About › Mic test strip). */
    val devStrip: Boolean = false,
)

/** Everything the panel can ask the service/engine to do. */
class OverlayActions(
    val onTap: () -> Unit,
    val onStartFromVoice: () -> Unit,
    val onCountdownTick: () -> Unit,
    val onPosition: (Float) -> Unit,
    val onFinished: () -> Unit,
    val onResumeIfFinished: () -> Unit,
    val onTogglePause: () -> Unit,
    val onRestart: () -> Unit,
    val onClose: () -> Unit,
    val onSpeed: (Int) -> Unit,
    val onFontSize: (Int) -> Unit,
    val onToggleMode: () -> Unit,
    val onLock: () -> Unit,
    val onMinimize: () -> Unit,
    val onRestore: () -> Unit,
    val onMove: (Float, Float) -> Unit,
    val onResize: (Float, Float) -> Unit,
    /** Finger lifted after moving or resizing: save the panel geometry. */
    val onDragEnd: () -> Unit,
)

private const val MAX_STEP_SEC = 0.1f
/** How quickly the text glides to the spoken word (per second); higher = snappier. */
private const val VOICE_EASE_PER_SEC = 6f
private const val CONTROLS_VISIBLE_MS = 4_000L
private val PanelShape = RoundedCornerShape(24.dp)

/**
 * The floating script panel.
 * Single tap = play/pause, double tap = control bar for 4 s, long press = lock,
 * swipe = scroll by hand, top handle = move, bottom-right corner = resize, ✕ = close.
 */
@Composable
fun OverlayContent(
    config: OverlayConfig,
    ui: ScrollUiState,
    /** Read only inside placement/the scroll loop, never during composition (see ScrollEngine.position). */
    position: () -> Float,
    /** Voice tracker state, when Voice mode is on; collected only where needed so level updates stay local. */
    voice: StateFlow<VoiceTracker.State>?,
    /** Line back/forward requests from the remote keys. */
    commands: Flow<Command>,
    actions: OverlayActions,
) {
    if (ui.minimized) {
        Bubble(actions)
        return
    }

    val prepared = remember(config.text) { ScriptText.prepare(config.text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val matcher = remember(prepared.display) { TextMatcher.from(prepared.display) }
    // Voice mode: the script word last heard, and the scroll position that puts its line on the reading line.
    var currentWord by remember { mutableIntStateOf(0) }
    var voiceTargetPx by remember { mutableFloatStateOf(0f) }
    val uiNow by rememberUpdatedState(ui)
    // Longest gap between two frames in the last ~3 s, shown on the developer strip.
    var maxFrameGapMs by remember { mutableLongStateOf(0L) }
    // Bumped on every control-bar interaction; the bar hides 4 s after the last one.
    var controlsNonce by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(false) }
    val touchControls: () -> Unit = {
        controlsVisible = true
        controlsNonce += 1
    }

    LaunchedEffect(controlsVisible, controlsNonce) {
        if (controlsVisible) {
            delay(CONTROLS_VISIBLE_MS)
            controlsVisible = false
        }
    }

    // --- Countdown: one tick per second.
    LaunchedEffect(ui.state, ui.countdown) {
        if (ui.state == PromptState.Countdown) {
            delay(1_000)
            actions.onCountdownTick()
        }
    }

    // --- Auto scroll (Auto mode, or Voice mode falling back because the mic is blocked).
    LaunchedEffect(ui.state, ui.followsVoice, layout, ui.wpm, config.pauseSec) {
        val l = layout ?: return@LaunchedEffect
        if (ui.state != PromptState.Prompting || ui.followsVoice) return@LaunchedEffect
        // Markers become scroll positions: hold once the marker's line has passed the reading line.
        val markersPx = prepared.markerOffsets.map { l.getLineBottom(l.getLineForOffset(it)) }
        val maxPx = l.getLineTop(l.lineCount - 1)
        val speed = AutoScroller.pixelsPerSecond(ui.wpm, prepared.words, l.size.height.toFloat())
        fun nextMarkerAfter(px: Float) = markersPx.indexOfFirst { it > px }.let { if (it < 0) markersPx.size else it }
        var pos = AutoScroller.Position(px = position(), nextMarker = nextMarkerAfter(position()))
        var last = withFrameNanos { it }
        var gapWindowStart = last
        var gapMax = 0L
        while (true) {
            val now = withFrameNanos { it }
            val gapNs = now - last
            // A stalled frame must not make the script jump ahead: cap the step at 100 ms.
            val dt = (gapNs / 1_000_000_000f).coerceAtMost(MAX_STEP_SEC)
            last = now
            gapMax = maxOf(gapMax, gapNs / 1_000_000)
            if (now - gapWindowStart > 3_000_000_000L) {
                maxFrameGapMs = gapMax
                gapMax = 0L
                gapWindowStart = now
            }
            // The user may have scrolled by hand or skipped a line: continue from there.
            val current = position()
            if (abs(current - pos.px) > 0.5f) pos = AutoScroller.Position(current, 0f, nextMarkerAfter(current))
            pos = AutoScroller.step(pos, dt, speed, markersPx, config.pauseSec, maxPx)
            actions.onPosition(pos.px)
            if (pos.px >= maxPx) {
                actions.onFinished()
                break
            }
        }
    }

    // --- New take: back to the first word.
    LaunchedEffect(ui.state) {
        if (ui.state == PromptState.Ready) {
            currentWord = 0
            voiceTargetPx = 0f
        }
    }

    // --- Voice mode, matching: every new piece of recognized text is lined up with the script.
    LaunchedEffect(voice, matcher, layout) {
        val v = voice ?: return@LaunchedEffect
        val l = layout ?: return@LaunchedEffect
        v.map { it.text to it.textVersion }.distinctUntilChanged().collect { (text, _) ->
            if (text.isBlank() || !uiNow.followsVoice) return@collect
            val state = uiNow.state
            // A take starts the moment the opening words are spoken.
            val startingNow = state == PromptState.Ready && matcher.matchesStart(text)
            if (startingNow) actions.onStartFromVoice()
            if (state != PromptState.Prompting && !startingNow) return@collect
            matcher.match(text, currentWord)?.let { j ->
                currentWord = j
                voiceTargetPx = l.getLineTop(l.getLineForOffset(matcher.words[j].offset))
            }
        }
    }

    // --- Voice mode, movement: glide towards the spoken word. If speech stops, the target
    // stays put and so does the script.
    LaunchedEffect(ui.state, ui.followsVoice, layout) {
        val l = layout ?: return@LaunchedEffect
        if (ui.state != PromptState.Prompting || !ui.followsVoice) return@LaunchedEffect
        val maxPx = l.getLineTop(l.lineCount - 1)
        var written = position()
        // Resuming after Auto fallback (or a pause): listen from wherever the text is now.
        currentWord = wordAt(l, matcher, written)
        voiceTargetPx = written
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceAtMost(MAX_STEP_SEC)
            last = now
            val current = position()
            if (abs(current - written) > 0.5f) {
                // Moved by hand (swipe, line step): carry on listening from there.
                currentWord = wordAt(l, matcher, current)
                voiceTargetPx = current
            }
            val next = current + (voiceTargetPx - current) * (dt * VOICE_EASE_PER_SEC).coerceAtMost(1f)
            actions.onPosition(next)
            written = next
            if (currentWord >= matcher.size - 1 && next >= maxPx - 1f) {
                actions.onFinished()
                break
            }
        }
    }

    // --- Remote keys: line back / forward need the text layout, so they are done here.
    LaunchedEffect(commands, layout) {
        val l = layout ?: return@LaunchedEffect
        commands.collect { cmd ->
            actions.onPosition(lineTarget(l, position(), if (cmd == Command.LineForward) +1 else -1))
            actions.onResumeIfFinished()
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clip(PanelShape)
            .background(Color(0xFF05070C).copy(alpha = config.opacityPct / 100f))
            .border(1.dp, PrompterColors.Accent.copy(alpha = 0.35f), PanelShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { actions.onTap() },
                    onDoubleTap = { touchControls() },
                    onLongPress = { actions.onLock() },
                )
            },
    ) {
        val readingFraction = config.readingLinePct / 100f
        val readingLineY = constraints.maxHeight * readingFraction
        val dimmed = ui.state == PromptState.Paused || ui.state == PromptState.Ready
        val maxScroll = { layout?.let { it.getLineTop(it.lineCount - 1) } ?: 0f }

        LensGlow(strength = if (ui.state == PromptState.Paused) 0f else 0.55f)

        // --- Script text, moved up by the scroll position, with the fade mask. Swipe to scroll by hand.
        if (ui.state != PromptState.Countdown && ui.state != PromptState.Finished) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { change, dy ->
                            change.consume()
                            actions.onPosition((position() - dy).coerceIn(0f, maxScroll()))
                        }
                    }
                    .graphicsLayer(
                        compositingStrategy = CompositingStrategy.Offscreen,
                        alpha = if (dimmed) 0.55f else 1f,
                        scaleX = if (config.mirror) -1f else 1f,
                    )
                    .drawWithContent {
                        drawContent()
                        if (config.fadeLines) drawRect(brush = fadeMask(readingFraction), blendMode = BlendMode.DstIn)
                    },
            ) {
                Text(
                    text = prepared.display,
                    // Keep a new layout only when it really changed (font size, resize). When the
                    // text height changes, keep the same place in the script.
                    onTextLayout = { new ->
                        val old = layout
                        if (old == null || old.size != new.size || old.lineCount != new.lineCount) {
                            if (old != null && old.size.height > 0) {
                                actions.onPosition(position() * new.size.height / old.size.height)
                            }
                            layout = new
                        }
                    },
                    style = TextStyle(
                        fontFamily = fontFamilyFor(config.font),
                        fontWeight = FontWeight.Medium,
                        fontSize = ui.fontSizeSp.sp,
                        lineHeight = (ui.fontSizeSp * config.lineSpacing).sp,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(unbounded = true, align = Alignment.Top)
                        .padding(horizontal = 20.dp)
                        .offset { IntOffset(0, (readingLineY - position()).roundToInt()) },
                )
            }
            // Thin reading-line marker on the left edge.
            with(LocalDensity.current) {
                Box(
                    Modifier
                        .offset(y = readingLineY.toDp() + 2.dp)
                        .size(width = 3.dp, height = 24.dp)
                        .background(PrompterColors.Accent, RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)),
                )
            }
        }

        val bottomPad = if (config.devStrip) 64.dp else 18.dp
        when (ui.state) {
            PromptState.Ready -> Pill(
                if (ui.followsVoice) "Start speaking (or tap)" else "Tap to start",
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad),
            )
            PromptState.Countdown -> Text(
                text = ui.countdown.toString(),
                style = TextStyle(fontFamily = PrompterFonts.Mono, fontWeight = FontWeight.Bold, fontSize = 88.sp, color = Color.White),
                modifier = Modifier.align(Alignment.Center),
            )
            PromptState.Paused -> if (!controlsVisible) PauseBadge(Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPad))
            PromptState.Finished -> FinishedActions(actions, Modifier.align(Alignment.Center))
            PromptState.Prompting -> Unit
        }

        // --- Mode badge (top right): VOICE (listening), MIC BLOCKED · AUTO (fallback), or AUTO · wpm.
        ModeBadge(ui, voice, Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 14.dp))
        if (config.devStrip) {
            DebugStrip(voice, { maxFrameGapMs }, { currentWord }, matcher.size, Modifier.align(Alignment.BottomCenter))
        }

        // --- Control bar (double tap), shown for 4 s after the last touch.
        if (controlsVisible) {
            ControlBar(
                ui = ui,
                actions = actions,
                onInteract = touchControls,
                lineStep = { dir ->
                    layout?.let { l -> actions.onPosition(lineTarget(l, position(), dir)) }
                    actions.onResumeIfFinished()
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
            )
        }

        // --- Close button: always visible, so the overlay can be dismissed at any time.
        TapIcon("Close overlay", Modifier.align(Alignment.TopStart), actions.onClose) {
            Canvas(Modifier.size(12.dp)) {
                val c = Color.White.copy(alpha = 0.6f)
                drawLine(c, Offset(0f, 0f), Offset(size.width, size.height), strokeWidth = 4f, cap = StrokeCap.Round)
                drawLine(c, Offset(size.width, 0f), Offset(0f, size.height), strokeWidth = 4f, cap = StrokeCap.Round)
            }
        }

        // --- Grab handle (move) and resize corner. Raw screen coordinates are used because
        // the window itself moves under the finger.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(width = 120.dp, height = 28.dp)
                .rawDrag(actions.onMove, actions.onDragEnd),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(width = 36.dp, height = 4.dp).background(Color.White.copy(alpha = 0.35f), CircleShape))
        }
        if (!controlsVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(36.dp)
                    .rawDrag(actions.onResize, actions.onDragEnd),
            ) {
                Canvas(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(14.dp)) {
                    val c = Color.White.copy(alpha = 0.45f)
                    val w = size.width
                    drawLine(c, Offset(w, w * 0.35f), Offset(w * 0.35f, w), strokeWidth = 3f, cap = StrokeCap.Round)
                    drawLine(c, Offset(w, w * 0.7f), Offset(w * 0.7f, w), strokeWidth = 3f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Scroll position of the previous/next line relative to the reading line. */
private fun lineTarget(l: TextLayoutResult, current: Float, dir: Int): Float {
    val line = l.getLineForVerticalPosition(current + 1f)
    val top = l.getLineTop(line)
    return when {
        dir > 0 -> l.getLineTop((line + 1).coerceAtMost(l.lineCount - 1))
        current - top > 4f -> top // part-way through a line: back to its start first
        else -> l.getLineTop((line - 1).coerceAtLeast(0))
    }
}

/** Mask: past lines 35%, the reading line 100%, upcoming lines 60%, fading out at the bottom. */
private fun fadeMask(r: Float): Brush = Brush.verticalGradient(
    0f to Color.Black.copy(alpha = 0.15f),
    (r - 0.10f).coerceAtLeast(0.01f) to Color.Black.copy(alpha = 0.35f),
    (r - 0.02f).coerceAtLeast(0.02f) to Color.Black,
    (r + 0.10f) to Color.Black,
    (r + 0.20f) to Color.Black.copy(alpha = 0.6f),
    0.85f.coerceAtLeast(r + 0.21f) to Color.Black.copy(alpha = 0.5f),
    1f to Color.Black.copy(alpha = 0.1f),
)

/** Blue radial glow coming from the front camera, as in the reel reference. */
@Composable
private fun LensGlow(strength: Float) {
    if (strength <= 0f) return
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to PrompterColors.Accent.copy(alpha = strength),
                        0.4f to PrompterColors.Accent.copy(alpha = strength * 0.3f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, -size.height * 0.05f),
                        radius = size.width * 0.55f,
                    ),
                )
            },
    )
}

/** Double-tap control bar (design: OverlayStates "Controls"). */
@Composable
private fun ControlBar(
    ui: ScrollUiState,
    actions: OverlayActions,
    onInteract: () -> Unit,
    lineStep: (Int) -> Unit,
    modifier: Modifier,
) {
    fun tap(block: () -> Unit): () -> Unit = {
        block()
        onInteract()
    }
    val label = TextStyle(fontFamily = PrompterFonts.Mono, fontSize = 11.sp, color = PrompterColors.Text)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PrompterColors.Surface)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures { onInteract() } } // taps between buttons keep the bar open
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TapIcon("Back one line", onClick = tap { lineStep(-1) }) { ArrowIcon(up = true) }
            TapIcon(
                if (ui.state == PromptState.Prompting) "Pause" else "Play",
                onClick = tap {
                    if (ui.state == PromptState.Ready || ui.state == PromptState.Finished) actions.onTap() else actions.onTogglePause()
                },
                filled = true,
            ) { if (ui.state == PromptState.Prompting) PauseIcon() else PlayIcon() }
            TapIcon("Forward one line", onClick = tap { lineStep(+1) }) { ArrowIcon(up = false) }
            TapIcon("Restart", onClick = tap(actions.onRestart)) { RestartIcon() }
            TapIcon("Lock", onClick = tap(actions.onLock)) { LockIcon() }
            TapIcon("Minimize", onClick = tap(actions.onMinimize)) { MinimizeIcon() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(PrompterColors.Surface2), verticalAlignment = Alignment.CenterVertically) {
                SmallButton("−", "Slower", tap { actions.onSpeed(-10) })
                Text("${ui.wpm} wpm", style = label)
                SmallButton("+", "Faster", tap { actions.onSpeed(+10) })
            }
            Row(Modifier.clip(RoundedCornerShape(10.dp)).background(PrompterColors.Surface2), verticalAlignment = Alignment.CenterVertically) {
                SmallButton("A−", "Smaller text", tap { actions.onFontSize(-2) })
                SmallButton("A+", "Larger text", tap { actions.onFontSize(+2) })
            }
            Text(
                if (ui.mode == ScrollMode.VOICE) "VOICE" else "AUTO",
                style = label.copy(color = PrompterColors.AccentText, fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrompterColors.Accent.copy(alpha = 0.2f))
                    .semantics { contentDescription = "Switch Voice or Auto mode" }
                    .pointerInput(Unit) { detectTapGestures { tap(actions.onToggleMode)() } }
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun TapIcon(
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    filled: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (filled) PrompterColors.Accent else Color.Transparent)
            .semantics { contentDescription = description }
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun SmallButton(text: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(width = 34.dp, height = 36.dp)
            .semantics { contentDescription = description }
            .pointerInput(onClick) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = PrompterColors.Text, fontSize = 13.sp, fontFamily = PrompterFonts.Sans) }
}

// ---- small stroke icons for the control bar

@Composable
private fun ArrowIcon(up: Boolean) = Canvas(Modifier.size(16.dp)) {
    val c = PrompterColors.Text
    val w = size.width
    val tip = if (up) 0.15f else 0.85f
    val base = if (up) 0.85f else 0.15f
    val wing = tip + if (up) 0.3f else -0.3f
    drawLine(c, Offset(w / 2, base * w), Offset(w / 2, tip * w), 4f, StrokeCap.Round)
    drawLine(c, Offset(w * 0.2f, wing * w), Offset(w / 2, tip * w), 4f, StrokeCap.Round)
    drawLine(c, Offset(w * 0.8f, wing * w), Offset(w / 2, tip * w), 4f, StrokeCap.Round)
}

@Composable
private fun RestartIcon() = Canvas(Modifier.size(16.dp)) {
    val c = PrompterColors.Text
    drawArc(c, startAngle = -60f, sweepAngle = 300f, useCenter = false, style = Stroke(width = 4f, cap = StrokeCap.Round))
    val w = size.width
    drawLine(c, Offset(w * 0.75f, w * 0.02f), Offset(w * 0.78f, w * 0.32f), 4f, StrokeCap.Round)
    drawLine(c, Offset(w * 0.78f, w * 0.32f), Offset(w * 0.48f, w * 0.3f), 4f, StrokeCap.Round)
}

@Composable
private fun LockIcon() = Canvas(Modifier.size(16.dp)) {
    val c = PrompterColors.Text
    val w = size.width
    drawRoundRect(c, topLeft = Offset(w * 0.12f, w * 0.45f), size = Size(w * 0.76f, w * 0.52f), cornerRadius = CornerRadius(3f))
    drawArc(
        c, startAngle = 180f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(w * 0.27f, w * 0.05f), size = Size(w * 0.46f, w * 0.7f),
        style = Stroke(width = 3.5f),
    )
}

@Composable
private fun MinimizeIcon() = Canvas(Modifier.size(16.dp)) {
    drawLine(PrompterColors.Text, Offset(size.width * 0.15f, size.height * 0.75f), Offset(size.width * 0.85f, size.height * 0.75f), 4f, StrokeCap.Round)
}

@Composable
private fun PlayIcon() = Canvas(Modifier.size(14.dp)) {
    val p = androidx.compose.ui.graphics.Path().apply {
        moveTo(size.width * 0.2f, 0f)
        lineTo(size.width, size.height / 2)
        lineTo(size.width * 0.2f, size.height)
        close()
    }
    drawPath(p, Color.White)
}

@Composable
private fun PauseIcon() = Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    Box(Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(1.dp)))
    Box(Modifier.size(width = 4.dp, height = 14.dp).background(Color.White, RoundedCornerShape(1.dp)))
}

/** Minimized: a small glowing bubble at the screen edge. Tap to restore, drag to move. */
@Composable
private fun Bubble(actions: OverlayActions) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Color(0xD905070C))
            .border(1.dp, PrompterColors.Accent.copy(alpha = 0.6f), CircleShape)
            .rawDrag(actions.onMove, onEnd = {}, onTap = actions.onRestore)
            .semantics { contentDescription = "Restore script" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(20.dp)) {
            val c = PrompterColors.AccentText
            listOf(0.25f to 0.85f, 0.5f to 0.85f, 0.75f to 0.6f).forEach { (y, end) ->
                drawLine(c, Offset(size.width * 0.15f, size.height * y), Offset(size.width * end, size.height * y), 5f, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun Pill(text: String, modifier: Modifier) {
    Row(
        modifier = modifier
            .background(PrompterColors.Accent, CircleShape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayIcon()
        Text(text, color = Color.White, style = TextStyle(fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 13.sp))
    }
}

@Composable
private fun PauseBadge(modifier: Modifier) {
    Box(
        modifier = modifier.size(44.dp).background(Color.White.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) { PauseIcon() }
}

@Composable
private fun FinishedActions(actions: OverlayActions, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("End of script", color = Color.White.copy(alpha = 0.75f), fontFamily = PrompterFonts.Mono, fontSize = 15.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = actions.onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = PrompterColors.Accent),
            ) { Text("Restart", fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold) }
            OutlinedButton(onClick = actions.onClose) {
                Text("Close", color = PrompterColors.Text, fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Top-right status: blue VOICE while listening, amber when the mic is blocked, or AUTO · wpm. */
@Composable
private fun ModeBadge(ui: ScrollUiState, voiceFlow: StateFlow<VoiceTracker.State>?, modifier: Modifier) {
    val voice = voiceFlow?.collectAsState()?.value
    val (label, color) = when {
        ui.mode == ScrollMode.AUTO -> "AUTO · ${ui.wpm}" to PrompterColors.AccentText
        ui.voiceFallback -> "MIC BLOCKED · AUTO" to PrompterColors.Warning
        voice?.blocked == true -> "MIC BLOCKED" to PrompterColors.Warning
        else -> "VOICE" to PrompterColors.Accent
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (ui.mode == ScrollMode.VOICE) Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 10.sp, fontFamily = PrompterFonts.Sans, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Developer strip (Settings › About › Mic test strip): what the voice tracker hears, which
 * script word it matched, and the longest frame gap (lag check).
 */
@Composable
private fun DebugStrip(
    voiceFlow: StateFlow<VoiceTracker.State>?,
    maxFrameGapMs: () -> Long,
    currentWord: () -> Int,
    totalWords: Int,
    modifier: Modifier,
) {
    val voice = voiceFlow?.collectAsState()?.value
    val small = TextStyle(fontFamily = PrompterFonts.Mono, fontSize = 10.sp, color = PrompterColors.Text2)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xE6151922))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (voice == null) {
                Text("voice off (Auto mode)", style = small)
            } else {
                Text("lvl %.1f".format(voice.level), style = small)
                LevelBar(voice.level, Modifier.width(40.dp).height(6.dp))
                Text("word ${currentWord() + 1}/$totalWords", style = small)
                Text("restarts:${voice.restarts}${if (voice.onDevice) " dev" else ""}", style = small)
            }
            Text("gap:${maxFrameGapMs()}ms", style = small)
        }
        if (voice != null) {
            Text(
                text = voice.error?.let { "[$it] " }.orEmpty() + voice.text.ifBlank { "…" },
                style = small.copy(color = PrompterColors.Text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(1.dp))
    }
}

@Composable
private fun LevelBar(level: Float, modifier: Modifier) {
    // Recognizer RMS is roughly -2..10.
    val fraction = (level + 2f) / 12f
    Box(modifier.background(PrompterColors.Surface2, CircleShape)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fraction.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(PrompterColors.Accent, CircleShape),
        )
    }
}

/** Index of the first script word on the line at scroll position [px]. */
private fun wordAt(l: TextLayoutResult, matcher: TextMatcher, px: Float): Int {
    val lineStart = l.getLineStart(l.getLineForVerticalPosition(px + 1f))
    val i = matcher.words.indexOfFirst { it.offset >= lineStart }
    return if (i < 0) (matcher.size - 1).coerceAtLeast(0) else i
}

private fun yesNo(b: Boolean) = if (b) "yes" else "no"

/** Finger positions of a drag, kept across recompositions. */
private class DragTracker {
    var lastX = 0f
    var lastY = 0f
    var downX = 0f
    var downY = 0f
}

/**
 * Drag using raw screen coordinates (the overlay window moves under the finger).
 * [onEnd] runs when the finger lifts; [onTap] runs instead if the finger barely moved.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.rawDrag(onDelta: (Float, Float) -> Unit, onEnd: () -> Unit, onTap: (() -> Unit)? = null): Modifier = composed {
    val tracker = remember { DragTracker() }
    pointerInteropFilter { e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracker.lastX = e.rawX
                tracker.lastY = e.rawY
                tracker.downX = e.rawX
                tracker.downY = e.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                onDelta(e.rawX - tracker.lastX, e.rawY - tracker.lastY)
                tracker.lastX = e.rawX
                tracker.lastY = e.rawY
            }
            MotionEvent.ACTION_UP -> {
                val moved = abs(e.rawX - tracker.downX) + abs(e.rawY - tracker.downY)
                if (onTap != null && moved < 24f) onTap() else onEnd()
            }
        }
        true
    }
}
