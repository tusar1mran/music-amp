package com.musicamp.poweramp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

// ==========================================================================================
// Small building blocks
// ==========================================================================================

/** Pill background colours of the player buttons: idle and "on" (e.g. sleep timer running). */
val PlayerPillIdle: Color get() = if (AppTheme.isDark) Color(0xFF1C1C1E) else Color(0xFFE9E9EC)
val PlayerPillOn: Color get() = if (AppTheme.isDark) Color(0xFF3A3A3D) else Color(0xFFCFCFD4)

/**
 * The visualization glyph: five rounded bars; Fade mode adds a row of dots under shorter bars,
 * Disabled draws it dimmed (same idea as the mode icons on the player).
 */
@Composable
fun VisModeIcon(mode: VisMode, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val heights = floatArrayOf(0.42f, 0.74f, 1f, 0.74f, 0.42f)
        val barW = w * 0.11f
        val gap = (w - barW * 5) / 4f
        val fade = mode == VisMode.FADE_CONTROLS
        val area = if (fade) h * 0.64f else h
        val color = if (mode == VisMode.DISABLED) tint.copy(alpha = tint.alpha * 0.55f) else tint
        for (i in 0 until 5) {
            val bh = area * heights[i]
            val x = i * (barW + gap)
            drawRoundRect(
                color = color,
                topLeft = Offset(x, (area - bh) / 2f),
                size = Size(barW, bh),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
        if (fade) {
            val r = barW * 0.62f
            val y = h - r
            listOf(1, 2, 3).forEach { i ->
                val cx = i * (barW + gap) + barW / 2f
                drawCircle(color, radius = if (i == 2) r * 1.25f else r, center = Offset(cx, y))
            }
        }
    }
}

/** Rounded pill button used for Visualization / Sleep / Repeat / Shuffle on the player. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerPillButton(
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    content: @Composable (tint: Color) -> Unit
) {
    val tint = if (active) PowerampTextPrimary else PowerampTextSecondary
    Box(
        modifier = modifier
            .size(width = 86.dp, height = 46.dp)
            .clip(RoundedCornerShape(50))
            .background(if (active) PlayerPillOn else PlayerPillIdle)
            .combinedClickable(
                onClickLabel = contentDescription,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        content(tint)
    }
}

/** Dark rounded backing behind text while a visualization runs, so it stays readable. */
fun Modifier.visChip(enabled: Boolean, corner: Dp = 14.dp): Modifier =
    if (!enabled) this else this
        .clip(RoundedCornerShape(corner))
        .background(Color(0xB3000000))
        .padding(horizontal = 8.dp, vertical = 2.dp)

// ==========================================================================================
// Visualization: full-screen layer, auto preset change
// ==========================================================================================

/** The selected preset drawn over the whole player on black (Full Screen mode). */
@Composable
fun FullScreenVisualization(controller: PowerampController, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawRect(Color.Black)
        drawVisualization(
            controller.currentVisPreset.id,
            controller.liveSpectrumAmplitudes,
            controller.visPhase,
            controller.isPlaying
        )
    }
}

/** Runs the Cycle / Shuffle preset change while a visualization is showing and music plays. */
@Composable
fun VisPresetAutoChange(controller: PowerampController) {
    val mode = controller.visPresetCycleMode
    val sec = controller.visPresetDurationSec
    val running = controller.visMode != VisMode.DISABLED && controller.isPlaying && mode != "Locked" && sec > 0
    LaunchedEffect(running, mode, sec) {
        if (!running) return@LaunchedEffect
        while (true) {
            delay(sec * 1000L)
            controller.autoAdvanceVisPreset()
        }
    }
}

// ==========================================================================================
// Preset bar (top of the player while a visualization runs)
// ==========================================================================================

@Composable
fun VisPresetBar(controller: PowerampController, modifier: Modifier = Modifier) {
    val preset = controller.currentVisPreset
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(50))
            .background(PowerampDialogBg.copy(alpha = 0.96f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clickable { controller.showVisPresetList = true }
                .padding(start = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VisModeIcon(VisMode.FULL_SCREEN, PowerampTextPrimary, Modifier.size(width = 22.dp, height = 18.dp))
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(
                    text = preset.name,
                    color = PowerampTextPrimary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.category,
                    color = PowerampTextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold.appWeight,
                    maxLines = 1
                )
            }
        }
        // Preset change mode: Cycle (timed) -> Shuffle -> Lock
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable { controller.cycleVisPresetMode() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (controller.visPresetCycleMode) {
                    "Shuffle" -> Icons.Filled.Shuffle
                    "Locked" -> Icons.Filled.Lock
                    else -> Icons.Filled.Update
                },
                contentDescription = "Preset change mode",
                tint = PowerampTextPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(48.dp)
                .clip(CircleShape)
                .clickable {
                    controller.setVisModeTo(VisMode.DISABLED)
                    controller.showToast(VisMode.DISABLED.label, "Long press for more options")
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Disable visualization", tint = PowerampTextPrimary, modifier = Modifier.size(28.dp))
        }
    }
}

// ==========================================================================================
// Preset list: drops down from the top, with search
// ==========================================================================================

@Composable
fun VisPresetListDropdown(controller: PowerampController) = AppTextStyleScope {
    var query by remember { mutableStateOf("") }
    val all = controller.visPresets
    val shown = remember(query, all) {
        all.withIndex().filter { query.isBlank() || it.value.name.contains(query.trim(), ignoreCase = true) }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controller.showVisPresetList = false
            }
    ) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 8.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text("Search", color = PowerampTextSecondary, fontSize = 22.sp, fontWeight = FontWeight.Bold.appWeight)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = PowerampTextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(PowerampTextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable {
                            controller.showVisPresetList = false
                            controller.pendingSettingsSection = "Visualization"
                            controller.showSettingsScreen = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Visualization settings", tint = PowerampTextPrimary, modifier = Modifier.size(26.dp))
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable { controller.showVisPresetList = false },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = PowerampTextPrimary, modifier = Modifier.size(28.dp))
                }
            }
            LazyColumn(modifier = Modifier.padding(bottom = 10.dp)) {
                itemsIndexed(shown, key = { _, p -> p.value.id }) { _, entry ->
                    val selected = entry.index == controller.currentVisPresetIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                controller.selectVisPreset(entry.index)
                                controller.showVisPresetList = false
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VisModeIcon(VisMode.FULL_SCREEN, PowerampTextPrimary, Modifier.size(width = 22.dp, height = 18.dp))
                        Spacer(modifier = Modifier.width(20.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.value.name, color = PowerampTextPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(entry.value.category, color = PowerampTextPrimary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold.appWeight)
                        }
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PowerampTextPrimary, modifier = Modifier.size(26.dp))
                        }
                    }
                }
            }
        }
    }
}

// ==========================================================================================
// Long-press menu of the Visualization button
// ==========================================================================================

@Composable
fun VisModeMenu(controller: PowerampController) = AppTextStyleScope {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controller.showVisModeMenu = false
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(vertical = 8.dp)
        ) {
            listOf(
                VisMode.DISABLED to "Disable Visualization",
                VisMode.FADE_CONTROLS to "Visualization - Fade",
                VisMode.FULL_SCREEN to "Visualization - Full Screen"
            ).forEach { (mode, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            controller.showVisModeMenu = false
                            controller.setVisModeTo(mode)
                        }
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VisModeIcon(mode, PowerampTextPrimary, Modifier.size(width = 22.dp, height = 22.dp))
                    Spacer(modifier = Modifier.width(26.dp))
                    Text(
                        text = label,
                        color = PowerampTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        modifier = Modifier.weight(1f)
                    )
                    if (controller.visMode == mode) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PowerampTextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PowerampTextSecondary.copy(alpha = 0.18f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        controller.showVisModeMenu = false
                        controller.pendingSettingsSection = "Visualization"
                        controller.showSettingsScreen = true
                    }
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null, tint = PowerampTextPrimary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(24.dp))
                Text("Settings", color = PowerampTextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold.appWeight)
            }
        }
    }
}

// ==========================================================================================
// Scrolling waveform seekbar behind the transport buttons
// ==========================================================================================

/**
 * The track's waveform scrolls under the transport buttons with the playing position fixed at the
 * centre: the played part sits on the left (dim), the rest on the right (bright). Drag sideways to seek.
 */
@Composable
fun ScrollingWaveSeek(controller: PowerampController, bars: List<Float>, progress: Float, modifier: Modifier = Modifier) {
    val seekable = controller.durationMs > 0L
    val waveform = remember(bars, controller.currentTrack?.id) {
        if (bars.isNotEmpty()) bars else {
            // No analysed waveform yet: a stable pseudo-shape so the scroller still reads as a track
            val seed = (controller.currentTrack?.id ?: 0L).toInt()
            List(160) { i ->
                val a = kotlin.math.sin(i * 0.21f + seed) * 0.5f + 0.5f
                val b = kotlin.math.sin(i * 0.057f + seed * 0.3f) * 0.5f + 0.5f
                (0.25f + 0.75f * a * b).coerceIn(0.1f, 1f)
            }
        }
    }
    val currentProgress by rememberUpdatedState(progress)
    val dim = PowerampTextPrimary.copy(alpha = 0.16f)
    val bright = PowerampTextPrimary.copy(alpha = 0.55f)
    Canvas(
        modifier = modifier.pointerInput(seekable) {
            if (!seekable) return@pointerInput
            var frac = 0f
            detectHorizontalDragGestures(
                onDragStart = {
                    frac = currentProgress
                    controller.isScrubbing = true
                },
                onDragEnd = {
                    controller.isScrubbing = false
                    controller.seekToFraction(frac)
                },
                onDragCancel = { controller.isScrubbing = false },
                onHorizontalDrag = { change, dx ->
                    change.consume()
                    val pitch = 7.dp.toPx()
                    val contentW = waveform.size * pitch
                    frac = (frac - dx / contentW).coerceIn(0f, 1f)
                    controller.previewScrubFraction(frac)
                }
            )
        }
    ) {
        if (!seekable) return@Canvas
        val pitch = 7.dp.toPx()
        val barW = 4.5.dp.toPx()
        val contentW = waveform.size * pitch
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxHalf = size.height * 0.5f
        val offset = cx - currentProgress * contentW
        for (i in waveform.indices) {
            val x = offset + i * pitch
            if (x < -pitch || x > size.width + pitch) continue
            val half = (waveform[i].coerceIn(0f, 1f) * maxHalf).coerceAtLeast(3.dp.toPx())
            drawRoundRect(
                color = if (x + barW / 2f < cx) dim else bright,
                topLeft = Offset(x, cy - half),
                size = Size(barW, half * 2f),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
            )
        }
    }
}

// ==========================================================================================
// Long-press menus of the Repeat / Shuffle pills: pick a mode directly
// ==========================================================================================

@Composable
private fun ModeChoiceMenu(
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) = AppTextStyleScope {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x80000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(PowerampDialogBg)
                .clickable(enabled = false) {}
                .padding(vertical = 8.dp)
        ) {
            options.forEachIndexed { i, (label, selected) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(i) }
                        .padding(horizontal = 24.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        color = PowerampTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        modifier = Modifier.weight(1f)
                    )
                    if (selected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = PowerampTextPrimary, modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun RepeatModeMenu(controller: PowerampController) {
    val modes = RepeatMode.entries
    ModeChoiceMenu(
        options = modes.map { it.label to (it == controller.repeatMode) },
        onPick = { i ->
            controller.showRepeatMenu = false
            controller.setRepeatModeTo(modes[i])
        },
        onDismiss = { controller.showRepeatMenu = false }
    )
}

@Composable
fun ShuffleModeMenu(controller: PowerampController) {
    val modes = ShuffleMode.entries
    ModeChoiceMenu(
        options = modes.map { it.label to (it == controller.shuffleMode) },
        onPick = { i ->
            controller.showShuffleMenu = false
            controller.setShuffleModeTo(modes[i])
        },
        onDismiss = { controller.showShuffleMenu = false }
    )
}
