package com.musicamp.poweramp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val PowerampBg = Color(0xFF000000)
val PowerampSurface = Color(0xFF141416)
val PowerampPillBg = Color(0xFF1A1B1E)
val PowerampDialogBg = Color(0xFF212226)
val PowerampBorderGray = Color(0xFF424347)
val PowerampNeonGreen = Color(0xFF00FF19)
val PowerampNeonGlow = Color(0x6600FF19)
val PowerampTextPrimary = Color(0xFFFFFFFF)
val PowerampTextSecondary = Color(0xFF9EA0A5)

/**
 * Draws the exact jagged mountain/lightning Poweramp cover placeholder artwork seen on Main Player,
 * Mini-player, Track list, and Dialogs, plus the real-time Visualization overlay (`spectrum-centered`,
 * `milkdrop-neon-horizon`, `bars-symmetrical-hd`, `radial-bass-reactor`, `stereo-oscilloscope`).
 */
@Composable
fun PowerampMountainArtwork(
    modifier: Modifier = Modifier,
    visMode: VisMode = VisMode.DISABLED,
    visPresetId: String = "spectrum_centered",
    visPhase: Float = 0f,
    isPlaying: Boolean = false,
    spectrumBars: List<Float> = emptyList()
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle breathing/beat scale when playing
        val beatPulse = if (isPlaying) 1f + 0.012f * sin(visPhase * 1.4f) else 1f
        val cx = w * 0.5f
        val cy = h * 0.5f

        // Base dark mountain bolt polygon matching screenshot 01_current.png
        val boltPath = Path().apply {
            fun sx(nx: Float) = cx + (w * nx - cx) * beatPulse
            fun sy(ny: Float) = cy + (h * ny - cy) * beatPulse
            moveTo(sx(0.04f), sy(0.56f))
            lineTo(sx(0.12f), sy(0.44f))
            lineTo(sx(0.18f), sy(0.52f))
            lineTo(sx(0.27f), sy(0.40f))
            lineTo(sx(0.36f), sy(0.53f))
            lineTo(sx(0.49f), sy(0.35f))
            lineTo(sx(0.61f), sy(0.53f))
            lineTo(sx(0.75f), sy(0.29f))
            lineTo(sx(0.97f), sy(0.77f))
            // Bottom contour back from right to left
            lineTo(sx(0.75f), sy(0.46f))
            lineTo(sx(0.61f), sy(0.70f))
            lineTo(sx(0.48f), sy(0.47f))
            lineTo(sx(0.35f), sy(0.63f))
            lineTo(sx(0.25f), sy(0.50f))
            lineTo(sx(0.18f), sy(0.60f))
            lineTo(sx(0.12f), sy(0.52f))
            close()
        }

        drawPath(
            path = boltPath,
            color = if (visMode == VisMode.FULL_SCREEN && isPlaying) Color(0xFF101113) else Color(0xFF1D1E20)
        )

        // When Visualization is active or music is playing with visualization enabled:
        if (visMode != VisMode.DISABLED) {
            val activeAmps = if (spectrumBars.isNotEmpty()) spectrumBars else List(48) { i ->
                if (isPlaying) {
                    ((sin(visPhase * 2f + i * 0.45f) * 0.5f + 0.5f) * 0.85f).coerceIn(0.05f, 1f)
                } else 0.04f
            }

            when (visPresetId) {
                "spectrum_centered" -> {
                    // Exact match to Poweramp's `spectrum-centered` (`02_playing_t1.png`, `03_playing_t2.png`):
                    // Sharp vertical white/silver pins symmetrical around a horizontal axis
                    val barCount = 44
                    val slotW = w / barCount
                    val pinW = (slotW * 0.45f).coerceAtLeast(2.2.dp.toPx())
                    val centerY = h * 0.36f
                    val maxHalfH = h * 0.30f

                    for (i in 0 until barCount) {
                        val ampIdx = ((i.toFloat() / barCount) * activeAmps.size).toInt().coerceIn(0, activeAmps.lastIndex)
                        val rawAmp = activeAmps[ampIdx]
                        // Shape envelope so it bursts dynamically in clusters like `02_playing_t1.png`
                        val clusterMod = (sin(i * 0.38f + visPhase * 0.8f) * 0.5f + 0.5f)
                        val halfH = (rawAmp * (0.4f + 0.6f * clusterMod) * maxHalfH).coerceAtLeast(3.dp.toPx())
                        val x = i * slotW + (slotW - pinW) / 2f

                        drawLine(
                            color = Color.White,
                            start = Offset(x + pinW / 2f, centerY - halfH),
                            end = Offset(x + pinW / 2f, centerY + halfH),
                            strokeWidth = pinW,
                            cap = StrokeCap.Square
                        )
                    }
                }

                "milkdrop_neon_wave" -> {
                    val barCount = 42
                    val barSpace = w / barCount
                    for (i in 0 until barCount) {
                        val ampIdx = ((i.toFloat() / barCount) * activeAmps.size).toInt().coerceIn(0, activeAmps.lastIndex)
                        val dynamicAmp = activeAmps[ampIdx]
                        val barH = (h * 0.46f * dynamicAmp).coerceAtLeast(8f)
                        val x = i * barSpace + barSpace * 0.2f
                        val yTop = h * 0.50f - barH / 2f
                        val hueColor = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E5FF), PowerampNeonGreen, Color(0xFF00E5FF)),
                            startY = yTop,
                            endY = yTop + barH
                        )
                        drawRoundRect(
                            brush = hueColor,
                            topLeft = Offset(x, yTop),
                            size = Size(barSpace * 0.6f, barH),
                            cornerRadius = CornerRadius(6f, 6f),
                            alpha = 0.88f
                        )
                    }
                }

                "bars_mirrored_eq" -> {
                    val barCount = 32
                    val slotW = w / barCount
                    val baseLineY = h * 0.68f
                    for (i in 0 until barCount) {
                        val ampIdx = ((i.toFloat() / barCount) * activeAmps.size).toInt().coerceIn(0, activeAmps.lastIndex)
                        val amp = activeAmps[ampIdx]
                        val barH = (h * 0.44f * amp).coerceAtLeast(6f)
                        val x = i * slotW + slotW * 0.15f
                        drawRoundRect(
                            color = PowerampNeonGreen,
                            topLeft = Offset(x, baseLineY - barH),
                            size = Size(slotW * 0.7f, barH),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                        // Subtle bottom reflection
                        drawRoundRect(
                            color = PowerampNeonGlow,
                            topLeft = Offset(x, baseLineY + 4f),
                            size = Size(slotW * 0.7f, barH * 0.35f),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }

                "plasma_radial_pulse" -> {
                    val avgAmp = if (activeAmps.isNotEmpty()) activeAmps.average().toFloat() else 0.3f
                    val baseR = minOf(w, h) * (0.18f + avgAmp * 0.14f)
                    val rayCount = 48
                    for (i in 0 until rayCount) {
                        val angle = (i.toFloat() / rayCount) * (2f * PI.toFloat()) + visPhase * 0.25f
                        val amp = activeAmps[i % activeAmps.size]
                        val r1 = baseR
                        val r2 = baseR + minOf(w, h) * 0.22f * amp
                        drawLine(
                            color = if (i % 2 == 0) PowerampNeonGreen else Color(0xFF00E5FF),
                            start = Offset(cx + cos(angle) * r1, cy + sin(angle) * r1),
                            end = Offset(cx + cos(angle) * r2, cy + sin(angle) * r2),
                            strokeWidth = 3.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                else -> {
                    // stereo-oscilloscope ribbon wave
                    val path1 = Path()
                    val path2 = Path()
                    val steps = 64
                    val stepX = w / steps
                    for (i in 0..steps) {
                        val x = i * stepX
                        val amp = activeAmps[(i * activeAmps.size / (steps + 1)).coerceIn(0, activeAmps.lastIndex)]
                        val y1 = cy + sin(visPhase * 2.2f + i * 0.25f) * (h * 0.24f * amp)
                        val y2 = cy + cos(visPhase * 1.7f - i * 0.22f) * (h * 0.20f * amp)
                        if (i == 0) {
                            path1.moveTo(x, y1)
                            path2.moveTo(x, y2)
                        } else {
                            path1.lineTo(x, y1)
                            path2.lineTo(x, y2)
                        }
                    }
                    drawPath(path1, color = PowerampNeonGreen, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path2, color = Color(0xFF00E5FF), style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))
                }
            }
        }
    }
}

/**
 * Poweramp Dynamic Waveseek Bar behind the transport buttons (`01_current.png`, `02_playing_t1.png`, `04_playing_vis1.png`).
 *
 * Authentic Poweramp playback animation:
 * - At 0:00 (paused), the waveform starts right at the vertical cursor pillar (`X = 58.5%`) and extends to the right edge.
 * - As music plays (`progress > 0`), the entire waveform smoothly scrolls from RIGHT to LEFT across the screen!
 * - Bars that have scrolled to the left of the playhead cursor (around Play/Pause and Rewind buttons) are rendered
 *   with Poweramp's signature played-track shading and dual pinch-bracket lines (`| ... |`) flanking the Play/Pause button!
 * - While playing, the active bars near the playhead pulse subtly with the live audio amplitude.
 */
@Composable
fun PowerampWaveseekBar(
    bars: List<Float>,
    progress: Float,
    isPlaying: Boolean,
    visPhase: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeBars = if (bars.isNotEmpty()) bars else List(84) { 0.45f + 0.5f * ((it % 7) / 7f) }
    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                    onSeek(frac)
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h * 0.5f

        // In Poweramp's waveseek (`01_current.png` -> `02_playing_t1.png` -> `04_playing_vis1.png`):
        // The right playhead bracket sits at X = 61.2% of width, and the left playhead bracket sits at X = 38.8% (flanking the center Play/Pause icon).
        val rightBracketX = w * 0.612f
        val leftBracketX = w * 0.388f
        val centerPlayX = w * 0.50f

        val slotW = (w - rightBracketX - 6.dp.toPx()) / 14.5f
        val barWidth = (slotW * 0.68f).coerceAtLeast(10f)
        val totalTrackPx = activeBars.size * slotW

        // At progress = 0f, bar #0 starts exactly at rightBracketX + 6.dp.
        // As progress increases from 0f to 1f, the entire bar strip scrolls smoothly to the left by `progress * totalTrackPx`!
        val scrollOffsetPx = progress.coerceIn(0f, 1f) * (totalTrackPx * 0.72f)
        val firstBarOriginX = rightBracketX + 8.dp.toPx() - scrollOffsetPx

        for (i in activeBars.indices) {
            val x = firstBarOriginX + i * slotW
            if (x + barWidth < 12.dp.toPx() || x > w - 4.dp.toPx()) continue

            // Never draw waveform bars directly over the center Play/Pause button circle (`leftBracketX .. rightBracketX`)
            // when paused at 0:00, and when playing, attenuate/curve bars passing behind the Play/Pause button just like `02_playing_t1.png` & `04_playing_vis1.png`!
            val barCenterX = x + barWidth / 2f
            val distFromPlayCenter = kotlin.math.abs(barCenterX - centerPlayX)
            val playGapHalf = (rightBracketX - leftBracketX) * 0.46f
            if (isPlaying && distFromPlayCenter < playGapHalf) {
                continue
            }

            val baseAmp = activeBars[i].coerceIn(0.42f, 0.96f)
            // Subtle live music beat reactivity on bars near the playhead when playing
            val livePulse = if (isPlaying) {
                1f + 0.08f * sin(visPhase * 2.4f + i * 0.6f)
            } else 1f
            val barH = (h * 0.52f * baseAmp * livePulse).coerceIn(14.dp.toPx(), h * 0.64f)

            // Determine bar color based on horizontal zone (matching `01_current.png`, `02_playing_t1.png`, `04_playing_vis1.png`)
            val barColor = when {
                // Bars behind Fast-Forward button (`>>`, roughly 62%..76% width) are dimmed so white `>>` icon stands out clearly
                barCenterX in (w * 0.615f)..(w * 0.765f) -> Color(0xFF424347)
                // Bars behind Rewind button (`<<`, roughly 23%..38% width)
                barCenterX < leftBracketX -> {
                    val fade = ((barCenterX - w * 0.18f) / (leftBracketX - w * 0.18f)).coerceIn(0f, 1f)
                    Color(0xFF8C8E93).copy(alpha = (0.25f + 0.65f * fade))
                }
                else -> Color(0xFF9EA0A5)
            }

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Draw the iconic Poweramp curved hourglass playhead brackets:
        // 1. Right bracket at `rightBracketX` (always visible, bright silver-gray `#B0B2B7`)
        drawPowerampPlayheadBracket(
            x = rightBracketX,
            centerY = centerY,
            height = h * 0.55f,
            color = Color(0xFFB0B2B7),
            curveInwardRight = true
        )

        // 2. Left bracket at `leftBracketX` (appears once playback has scrolled bars past the center Play/Pause button, matching `04_playing_vis1.png`!)
        if (progress > 0.008f || isPlaying) {
            val leftAlpha = ((progress * 25f).coerceIn(0.35f, 0.75f))
            drawPowerampPlayheadBracket(
                x = leftBracketX,
                centerY = centerY,
                height = h * 0.53f,
                color = Color(0xFF686A70).copy(alpha = leftAlpha),
                curveInwardRight = false
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPowerampPlayheadBracket(
    x: Float,
    centerY: Float,
    height: Float,
    color: Color,
    curveInwardRight: Boolean
) {
    val topY = centerY - height / 2f
    val botY = centerY + height / 2f
    val curveOffset = if (curveInwardRight) -4.dp.toPx() else 4.dp.toPx()

    val bracketPath = Path().apply {
        moveTo(x, topY + 6.dp.toPx())
        quadraticTo(x + curveOffset, centerY, x, botY - 6.dp.toPx())
    }
    drawPath(
        path = bracketPath,
        color = color,
        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
    )
    drawCircle(
        color = color,
        radius = 4.8.dp.toPx(),
        center = Offset(x, topY + 5.dp.toPx())
    )
    drawCircle(
        color = color,
        radius = 4.8.dp.toPx(),
        center = Offset(x, botY - 5.dp.toPx())
    )
}

/**
 * Poweramp Rotary Knob (`PowerampRotaryKnob`):
 * Matches screenshots 03_eq_tab1.png, 07_eq_volume_tab.png, 08_eq_reverb_tab.png:
 * - Dark circular dial (#262628) with crisp 1.5dp outer border (#525357)
 * - Optional neon green outer progress arc (`showGreenArc = true` on Volume knob)
 * - Rotated white/gray pill indicator line inside the rim
 * - High-precision vertical and circular rotary drag interaction
 * - 100% authentic silky smooth transition animation on preset change, double-tap reset, reset button, and tap
 */
@Composable
fun PowerampRotaryKnob(
    value: Float, // normalized 0f..1f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 94.dp,
    enabled: Boolean = true,
    showGreenArc: Boolean = false,
    bipolar: Boolean = false // true for Balance knob where 0.5f is top dead center (12 o'clock)
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val density = LocalDensity.current

    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }

    // Smooth transition animation when updating via presets, double-tap reset, reset button, or tap
    val animatedValue by animateFloatAsState(
        targetValue = value.coerceIn(0f, 1f),
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "knobSmoothTransition"
    )

    // During active dragging, visual state tracks touch directly with zero latency (120fps fluid).
    // During non-drag state updates, it transitions with a silky analog servo easing curve.
    val effectiveValue = if (isDragging) dragAccumulator else animatedValue
    val clamped = effectiveValue.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled, bipolar) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragAccumulator = value.coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                    },
                    onDragCancel = {
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val px = change.position.x
                        val py = change.position.y
                        val cx = this.size.width.toFloat() / 2f
                        val cy = this.size.height.toFloat() / 2f

                        // 1. Angular delta relative to knob center (rotary wheel movement)
                        val prevPos = change.position - dragAmount
                        val a1 = atan2((prevPos.y - cy).toDouble(), (prevPos.x - cx).toDouble()).toFloat()
                        val a2 = atan2((py - cy).toDouble(), (px - cx).toDouble()).toFloat()
                        var aDiff = a2 - a1
                        while (aDiff > PI.toFloat()) aDiff -= (2f * PI.toFloat())
                        while (aDiff < -PI.toFloat()) aDiff += (2f * PI.toFloat())

                        val angularSweepRad = 290f * (PI.toFloat() / 180f)
                        val angleDelta = aDiff / angularSweepRad

                        // 2. High-precision vertical micro-drag delta (smooth linear dragging)
                        // Calibrated: ~220dp of vertical travel covers the full 0% to 100% range
                        val verticalDelta = -dragAmount.y / (density.density * 220f)

                        // 3. Intelligent hybrid blend:
                        // If user touches near rim and makes a circular tangential motion, use angular delta.
                        // If user swipes vertically across knob or label, use vertical delta.
                        val distFromCenter = kotlin.math.hypot(px - cx, py - cy)
                        val isRimCircular = distFromCenter > (cx * 0.35f) && kotlin.math.abs(dragAmount.x) > kotlin.math.abs(dragAmount.y) * 0.55f
                        val effectiveDelta = if (isRimCircular) angleDelta else verticalDelta

                        var nextVal = (dragAccumulator + effectiveDelta).coerceIn(0f, 1f)

                        // Magnetic center detent for bipolar knobs (Balance / Tone at 0.5)
                        if (bipolar) {
                            if (kotlin.math.abs(nextVal - 0.5f) < 0.022f) {
                                nextVal = 0.5f
                            }
                        }

                        dragAccumulator = nextVal
                        currentOnValueChange(nextVal)
                    }
                )
            }
            .pointerInput(enabled, bipolar) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        val resetVal = if (bipolar) 0.5f else 0f
                        dragAccumulator = resetVal
                        currentOnValueChange(resetVal)
                    },
                    onTap = { offset ->
                        val cx = this.size.width.toFloat() / 2f
                        val cy = this.size.height.toFloat() / 2f
                        val angleRad = atan2((offset.y - cy).toDouble(), (offset.x - cx).toDouble()).toFloat()
                        var angleDeg = angleRad * (180f / PI.toFloat())
                        if (angleDeg < 0f) angleDeg += 360f

                        // Map angle [125° .. 415°] (where 415° = 55°)
                        val startAngle = 125f
                        var relAngle = angleDeg - startAngle
                        if (relAngle < 0f) relAngle += 360f

                        if (relAngle <= 290f) {
                            var tapFrac = (relAngle / 290f).coerceIn(0f, 1f)
                            if (bipolar && kotlin.math.abs(tapFrac - 0.5f) < 0.035f) {
                                tapFrac = 0.5f
                            }
                            dragAccumulator = tapFrac
                            currentOnValueChange(tapFrac)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val outerRadius = (minOf(w, h) / 2f) - 10.dp.toPx()

            // 1. Perimeter Tick Dots / Notches around 290° active arc (125° to 415°)
            val tickCount = 21
            val arcStart = 125f
            val arcTotal = 290f
            for (i in 0 until tickCount) {
                val tFrac = i.toFloat() / (tickCount - 1)
                val tAngle = (arcStart + arcTotal * tFrac) * (PI / 180f).toFloat()

                val isTickActive = if (bipolar) {
                    if (clamped >= 0.5f) {
                        tFrac in 0.5f..(clamped + 0.015f)
                    } else {
                        tFrac in (clamped - 0.015f)..0.5f
                    }
                } else {
                    tFrac <= clamped + 0.015f
                }

                val rTick = outerRadius + 6.dp.toPx()
                val tickPos = Offset(center.x + rTick * cos(tAngle), center.y + rTick * sin(tAngle))
                val isCenterTick = bipolar && i == tickCount / 2
                drawCircle(
                    color = when {
                        isCenterTick && kotlin.math.abs(clamped - 0.5f) < 0.02f -> PowerampNeonGreen
                        isCenterTick -> Color.White
                        isTickActive && showGreenArc -> PowerampNeonGreen
                        isTickActive -> Color(0xFF8E9096)
                        else -> Color(0xFF2C2D31)
                    },
                    radius = if (isCenterTick) 2.2.dp.toPx() else 1.4.dp.toPx(),
                    center = tickPos
                )
            }

            // 2. Optional neon green glowing outer arc (Volume knob / Active knobs)
            if (showGreenArc) {
                if (bipolar) {
                    // Bipolar arc originates from top center (270° / 0.5f)
                    val diff = clamped - 0.5f
                    if (kotlin.math.abs(diff) > 0.01f) {
                        val centerAngle = 270f
                        val sweep = diff * 290f
                        val arcRadius = outerRadius + 4.dp.toPx()
                        drawArc(
                            color = PowerampNeonGlow,
                            startAngle = centerAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = Size(arcRadius * 2f, arcRadius * 2f),
                            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = PowerampNeonGreen,
                            startAngle = centerAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                            size = Size(arcRadius * 2f, arcRadius * 2f),
                            style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                } else if (clamped > 0.005f) {
                    val startAngle = 125f
                    val sweepAngle = 290f * clamped
                    val arcRadius = outerRadius + 4.dp.toPx()
                    // Outer soft green glow
                    drawArc(
                        color = PowerampNeonGlow,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Core bright neon green arc
                    drawArc(
                        color = PowerampNeonGreen,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                        size = Size(arcRadius * 2f, arcRadius * 2f),
                        style = Stroke(width = 3.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Knob Body (dark circular surface with authentic recessed bevel)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = if (enabled) listOf(Color(0xFF323337), Color(0xFF1E1F21))
                    else listOf(Color(0xFF222325), Color(0xFF161718)),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius,
                center = center
            )

            // 4. Crisp Outer Border Ring
            drawCircle(
                color = if (enabled) Color(0xFF56575B) else Color(0xFF333438),
                radius = outerRadius,
                center = center,
                style = Stroke(width = 1.6.dp.toPx())
            )

            // 5. Center Detent Indicator (Top 12 o'clock dot for bipolar knobs)
            if (bipolar) {
                val topCenterAngle = 270f * (PI / 180f).toFloat()
                val rDetent = outerRadius - 6.dp.toPx()
                drawCircle(
                    color = if (kotlin.math.abs(clamped - 0.5f) < 0.02f) PowerampNeonGreen else Color(0xFF6C6E74),
                    radius = 2.0.dp.toPx(),
                    center = Offset(center.x + rDetent * cos(topCenterAngle), center.y + rDetent * sin(topCenterAngle))
                )
            }

            // 6. Active Pointer Notch Line inside the rim
            val angleDeg = 125f + 290f * clamped
            val angleRad = (angleDeg * PI / 180.0).toFloat()

            val tickOuter = outerRadius - 5.dp.toPx()
            val tickInner = outerRadius - 19.dp.toPx()
            val p1 = Offset(
                center.x + tickInner * cos(angleRad),
                center.y + tickInner * sin(angleRad)
            )
            val p2 = Offset(
                center.x + tickOuter * cos(angleRad),
                center.y + tickOuter * sin(angleRad)
            )

            drawLine(
                color = if (enabled) Color(0xFFFFFFFF) else Color(0xFF55565A),
                start = p1,
                end = p2,
                strokeWidth = 3.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Poweramp Vertical Graphic Equalizer Slider (`PowerampEqBandSlider`):
 * Matches screenshot 03_eq_tab1.png:
 * - Vertical track line with subtle horizontal tick marks at top and bottom
 * - Glowing neon green bar below the thumb (`#00FF19`) for EQ frequency bands (or dark line for Preamp)
 * - Rounded vertical capsule thumb (`#2B2B2E`) with a lighter gray border and horizontal white dash inside
 * - Band label (`Preamp`, `31`, `62`, `125`, ...) and numeric dB value (`0.0`) at the bottom
 * - Smooth transition animation on preset switch and double-tap reset
 */
@Composable
fun PowerampEqBandSlider(
    label: String,
    dbValue: Float, // -15f..15f
    onValueChange: (Float) -> Unit,
    isPreamp: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    var isDragging by remember { mutableStateOf(false) }
    var dragDb by remember { mutableFloatStateOf(dbValue.coerceIn(-15f, 15f)) }

    val animatedDb by animateFloatAsState(
        targetValue = dbValue.coerceIn(-15f, 15f),
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        ),
        label = "eqBandSliderSmooth"
    )

    val effectiveDb = if (isDragging) dragDb else animatedDb
    val fraction = ((effectiveDb + 15f) / 30f).coerceIn(0f, 1f) // 0f = bottom (-15dB), 0.5f = center (0dB), 1f = top (+15dB)

    Column(
        modifier = modifier
            .width(if (isPreamp) 60.dp else 44.dp)
            .fillMaxHeight()
            .background(
                color = if (isPreamp) Color(0xFF121214) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectVerticalDragGestures(
                        onDragStart = {
                            isDragging = true
                            dragDb = dbValue.coerceIn(-15f, 15f)
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val newFrac = (1f - (change.position.y / h)).coerceIn(0f, 1f)
                            val newDb = (newFrac * 30f - 15f)
                            dragDb = newDb
                            currentOnValueChange(newDb)
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            dragDb = 0f
                            currentOnValueChange(0f)
                        },
                        onTap = { offset ->
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val newFrac = (1f - (offset.y / h)).coerceIn(0f, 1f)
                            val newDb = (newFrac * 30f - 15f)
                            dragDb = newDb
                            currentOnValueChange(newDb)
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val centerX = w / 2f
                val topPadding = 24.dp.toPx()
                val bottomPadding = 24.dp.toPx()
                val trackHeight = (h - topPadding - bottomPadding).coerceAtLeast(10f)

                val thumbCenterY = topPadding + (1f - fraction) * trackHeight
                val thumbW = 28.dp.toPx()
                val thumbH = 54.dp.toPx()

                // Top & bottom subtle tick marks on left and right of the track
                val tickOffset = 10.dp.toPx()
                val tickLen = 2.5.dp.toPx()
                val topTickY = topPadding + 6.dp.toPx()
                val botTickY = h - bottomPadding - 6.dp.toPx()

                for (ty in listOf(topTickY, botTickY)) {
                    drawLine(
                        color = Color(0xFF75767A),
                        start = Offset(centerX - tickOffset - tickLen, ty),
                        end = Offset(centerX - tickOffset, ty),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF75767A),
                        start = Offset(centerX + tickOffset, ty),
                        end = Offset(centerX + tickOffset + tickLen, ty),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }

                // Upper unlit track line from topPadding to thumb top
                drawLine(
                    color = Color(0xFF4B4C50),
                    start = Offset(centerX, topPadding),
                    end = Offset(centerX, (thumbCenterY - thumbH / 2f).coerceAtLeast(topPadding)),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Lower track line from thumb bottom to bottomPadding
                val lowerStart = (thumbCenterY + thumbH / 2f).coerceAtMost(h - bottomPadding)
                if (!isPreamp && enabled) {
                    // Outer neon green glow
                    drawLine(
                        color = PowerampNeonGlow,
                        start = Offset(centerX, lowerStart),
                        end = Offset(centerX, h - bottomPadding),
                        strokeWidth = 7.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // Bright neon green core line
                    drawLine(
                        color = PowerampNeonGreen,
                        start = Offset(centerX, lowerStart),
                        end = Offset(centerX, h - bottomPadding),
                        strokeWidth = 2.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                } else {
                    drawLine(
                        color = Color(0xFF4B4C50),
                        start = Offset(centerX, lowerStart),
                        end = Offset(centerX, h - bottomPadding),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                // Capsule thumb body
                val thumbTopLeft = Offset(centerX - thumbW / 2f, thumbCenterY - thumbH / 2f)
                drawRoundRect(
                    color = Color(0xFF28282B),
                    topLeft = thumbTopLeft,
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
                )
                drawRoundRect(
                    color = Color(0xFF56575C),
                    topLeft = thumbTopLeft,
                    size = Size(thumbW, thumbH),
                    cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f),
                    style = Stroke(width = 1.6.dp.toPx())
                )

                // White horizontal center dash inside capsule thumb
                val dashHalf = 5.5.dp.toPx()
                drawLine(
                    color = Color(0xFFD8D9DD),
                    start = Offset(centerX - dashHalf, thumbCenterY),
                    end = Offset(centerX + dashHalf, thumbCenterY),
                    strokeWidth = 3.2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = PowerampTextPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Text(
            text = "%.1f".format(dbValue),
            color = PowerampTextSecondary,
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

/**
 * Poweramp Frequency Response Curve Scroller box below the EQ bands (`03_eq_tab1.png`),
 * enhanced with real-time live audio spectrum bars pulsing behind the green frequency spline curve when music is playing!
 */
@Composable
fun PowerampFrequencyResponseGraph(
    bandGains: List<Float>,
    preampDb: Float,
    eqEnabled: Boolean,
    isPlaying: Boolean = false,
    spectrumBars: List<Float> = emptyList(),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1D1E21))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val midY = h / 2f

            // Slightly lighter inner viewport highlight representing visible knob scroll window
            drawRoundRect(
                color = Color(0xFF2B2C30),
                topLeft = Offset(0f, 0f),
                size = Size(w * 0.85f, h),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
            )

            // Live real-time FFT spectrum bars dancing inside the Frequency Response Box during playback
            if (isPlaying && spectrumBars.isNotEmpty()) {
                val count = 36
                val slot = w / count
                for (i in 0 until count) {
                    val idx = ((i.toFloat() / count) * spectrumBars.size).toInt().coerceIn(0, spectrumBars.lastIndex)
                    val amp = spectrumBars[idx]
                    val barH = (h * 0.78f * amp).coerceAtLeast(3f)
                    drawRoundRect(
                        color = if (eqEnabled) Color(0x3800FF19) else Color(0x338E9095),
                        topLeft = Offset(i * slot + slot * 0.15f, h - barH),
                        size = Size(slot * 0.7f, barH),
                        cornerRadius = CornerRadius(3f, 3f)
                    )
                }
            }

            if (bandGains.isEmpty()) return@Canvas

            val path = Path()
            val stepX = w / (bandGains.size - 1).coerceAtLeast(1)

            val points = bandGains.mapIndexed { idx, gain ->
                val x = idx * stepX
                val totalGain = (gain + preampDb * 0.35f).coerceIn(-15f, 15f)
                val y = (midY - (totalGain / 15f) * (h * 0.38f)).coerceIn(6f, h - 6f)
                Offset(x, y)
            }

            path.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val ctrlX = (p0.x + p1.x) / 2f
                path.cubicTo(ctrlX, p0.y, ctrlX, p1.y, p1.x, p1.y)
            }

            if (eqEnabled) {
                drawPath(
                    path = path,
                    color = PowerampNeonGlow,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = path,
                    color = PowerampNeonGreen,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            } else {
                drawPath(
                    path = path,
                    color = Color(0xFF68696E),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

/**
 * Floating Mini-Player + 4-Button Bottom Navigation Bar (`02_library.png`, `03_eq_tab1.png`, `04_search.png`).
 * On the main Player screen (`showMiniPlayer = false`), only the rounded 4-icon bottom navbar pill is shown.
 * On Library / Equalizer / Search / Lyrics screens (`showMiniPlayer = true`), the unified rounded container
 * includes the mini-player row + thin seekbar + 4 navigation icons.
 */
@Composable
fun PowerampBottomDock(
    controller: PowerampController,
    showMiniPlayer: Boolean,
    modifier: Modifier = Modifier
) {
    val track = controller.currentTrack
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF18191B))
            .padding(top = if (showMiniPlayer) 8.dp else 4.dp, bottom = 4.dp)
    ) {
        if (showMiniPlayer && track != null) {
            // Mini-player row: clicking opens Main Player screen, swiping left/right changes track with slide animation!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var totalDx = 0f
                        detectDragGestures(
                            onDragStart = { totalDx = 0f },
                            onDragEnd = {
                                if (totalDx > 80f) {
                                    controller.previousTrack()
                                } else if (totalDx < -80f) {
                                    controller.nextTrack()
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                totalDx += dragAmount.x
                            }
                        )
                    }
                    .clickable {
                        controller.showLyricsScreen = false
                        controller.currentMainTab = MainNavTab.PLAYER
                    }
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PowerampMountainArtwork(
                    modifier = Modifier.size(36.dp),
                    visMode = controller.visMode,
                    visPresetId = controller.currentVisPreset.id,
                    visPhase = controller.visPhase,
                    isPlaying = controller.isPlaying,
                    spectrumBars = controller.liveSpectrumAmplitudes
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        color = PowerampTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        color = Color(0xFFD0D2D6),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = PowerampTextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Mini-player thin horizontal seekbar with grey pill thumb (`navbar_seekbar`)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .padding(horizontal = 16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            controller.seekToFraction((offset.x / size.width).coerceIn(0f, 1f))
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            controller.seekToFraction((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val cy = h / 2f
                    val trackH = 10.dp.toPx()

                    // Dark rounded track
                    drawRoundRect(
                        color = Color(0xFF232427),
                        topLeft = Offset(0f, cy - trackH / 2f),
                        size = Size(w, trackH),
                        cornerRadius = CornerRadius(trackH / 2f, trackH / 2f)
                    )

                    // Pill thumb
                    val thumbW = 18.dp.toPx()
                    val thumbH = 10.dp.toPx()
                    val thumbX = (controller.progressFraction * (w - thumbW)).coerceIn(0f, (w - thumbW).coerceAtLeast(0f))
                    drawRoundRect(
                        color = Color(0xFF8C8E93),
                        topLeft = Offset(thumbX, cy - thumbH / 2f),
                        size = Size(thumbW, thumbH),
                        cornerRadius = CornerRadius(thumbH / 2f, thumbH / 2f)
                    )
                }
            }
        }

        // 4 Navigation Buttons Row: Library (2x2 grid), Equ/Effects (3 bars), Search (Magnifier), Main Menu (3 thick bars)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isLibActive = controller.currentMainTab == MainNavTab.LIBRARY && !controller.showMainMenuSheet
            val isEqActive = controller.currentMainTab == MainNavTab.EQUALIZER && !controller.showMainMenuSheet
            val isSearchActive = controller.currentMainTab == MainNavTab.SEARCH && !controller.showMainMenuSheet
            val isMenuActive = controller.showMainMenuSheet

            // 1. Library button (4 rounded squares grid)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        controller.showMainMenuSheet = false
                        controller.showLyricsScreen = false
                        if (controller.currentMainTab == MainNavTab.LIBRARY && controller.activeLibraryCategory != null) {
                            if (controller.activeSubFilter != null) {
                                controller.activeSubFilter = null
                            } else {
                                controller.activeLibraryCategory = null
                            }
                        } else {
                            controller.currentMainTab = MainNavTab.LIBRARY
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.GridView,
                    contentDescription = "Library",
                    tint = if (isLibActive) Color.White else Color(0xFF9EA0A5),
                    modifier = Modifier.size(26.dp)
                )
            }

            // 2. Equalizer / Effects button (3 vertical bars)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        controller.showMainMenuSheet = false
                        controller.showLyricsScreen = false
                        controller.currentMainTab = MainNavTab.EQUALIZER
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = "Equ/Effects",
                    tint = if (isEqActive) Color.White else Color(0xFF9EA0A5),
                    modifier = Modifier.size(28.dp)
                )
            }

            // 3. Search button
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        controller.showMainMenuSheet = false
                        controller.showLyricsScreen = false
                        controller.currentMainTab = MainNavTab.SEARCH
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = if (isSearchActive) Color.White else Color(0xFF9EA0A5),
                    modifier = Modifier.size(28.dp)
                )
            }

            // 4. Main Menu button (3 thick rounded horizontal bars)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable {
                        controller.showMainMenuSheet = !controller.showMainMenuSheet
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Main Menu",
                    tint = if (isMenuActive) Color.White else Color(0xFF9EA0A5),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/**
 * Colored circular category icon matching `02_library.png` and `15_library_scrolled.png`.
 */
@Composable
fun LibraryCategoryBadge(
    category: LibraryCategory,
    size: Dp = 38.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(category.circleColor),
        contentAlignment = Alignment.Center
    ) {
        when (category.iconType) {
            "treble_clef" -> {
                Text(
                    text = "𝄞",
                    color = Color.Black,
                    fontSize = (size.value * 0.62f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
            "years" -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("`70", color = Color(0xFF1D3238), fontSize = 7.sp, fontWeight = FontWeight.Bold, lineHeight = 7.sp)
                    Text("`90", color = Color(0xFF112024), fontSize = 8.sp, fontWeight = FontWeight.Bold, lineHeight = 8.sp)
                    Text("`20", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 10.sp)
                }
            }
            "plus" -> {
                Text(
                    text = "+",
                    color = Color.Black,
                    fontSize = (size.value * 0.62f).sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            else -> {
                val icon = when (category.iconType) {
                    "folder" -> Icons.Filled.Folder
                    "folder_tree" -> Icons.Filled.FolderCopy
                    "album" -> Icons.Filled.MusicNote
                    "mic", "mic_star" -> Icons.Filled.Mic
                    "guitar" -> Icons.Filled.MusicNote
                    "person" -> Icons.Filled.Person
                    "playlist" -> Icons.Filled.PlaylistPlay
                    "stream" -> Icons.Filled.Sensors
                    "queue" -> Icons.Filled.FastForward
                    "bookmark" -> Icons.Filled.Bookmark
                    "most_played", "recent_played" -> Icons.Filled.PlayArrow
                    "thumb_up" -> Icons.Filled.ThumbUp
                    "thumb_down" -> Icons.Filled.ThumbDown
                    else -> Icons.Filled.MoreHoriz
                }
                Icon(
                    imageVector = icon,
                    contentDescription = category.title,
                    tint = Color.Black,
                    modifier = Modifier.size(size * 0.58f)
                )
            }
        }
    }
}

/**
 * Vertical A-Z Fast Scroller Strip on the right edge of Library / All Songs (`09_all_songs.png`).
 */
@Composable
fun PowerampAlphabetScroller(
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier
) {
    val letters = listOf('˄', '0', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '#')
    Column(
        modifier = modifier
            .width(20.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x99121315))
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { ch ->
            Text(
                text = ch.toString(),
                color = Color(0xFFB5B7BC),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onLetterSelected(ch) }
            )
        }
    }
}
