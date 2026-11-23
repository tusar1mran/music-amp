package com.musicamp.poweramp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderCopy
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
 * Loads the real cover art for [track] (embedded tag, else a cover/folder image next to the file)
 * off the main thread. Returns null while loading or when the track has no artwork.
 */
@Composable
fun rememberTrackArt(controller: PowerampController, track: AudioTrack?): ImageBitmap? {
    val cached = remember(track?.id) {
        track?.id?.let { controller.getCachedArt(it)?.asImageBitmap() }
    }
    val art by produceState<ImageBitmap?>(initialValue = cached, track?.id) {
        if (cached != null) {
            value = cached
        } else {
            value = null
            value = track?.let { controller.loadArt(it)?.asImageBitmap() }
        }
    }
    return art
}

/** One artwork card: the real cover when present, otherwise a neutral gradient derived from the album name. */
@Composable
private fun ArtCard(
    art: ImageBitmap?,
    track: AudioTrack?,
    corner: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    spectrum: List<Float>? = null,
    iconSize: androidx.compose.ui.unit.Dp = 56.dp
) {
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color(0xFF2A1520))
            .border(1.dp, Color(0x33FFFFFF), shape)
    ) {
        if (art != null) {
            Image(
                bitmap = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val seed = ((track?.album ?: "") + (track?.artist ?: "")).hashCode() and 0x7fffffff
            val hue = (seed % 360).toFloat()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.hsv(hue, 0.55f, 0.40f), Color.hsv((hue + 40f) % 360f, 0.60f, 0.22f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = Color(0x99FFFFFF),
                    modifier = Modifier.size(iconSize)
                )
            }
        }
        // Live spectrum overlay (long-press the artwork to switch visualization on / off)
        if (spectrum != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0x66000000)),
                        startY = size.height * 0.4f, endY = size.height
                    )
                )
                val count = spectrum.size
                if (count == 0) return@Canvas
                val slot = size.width / count
                val barW = (slot * 0.55f).coerceAtLeast(2.dp.toPx())
                for (i in 0 until count) {
                    val h = (spectrum[i].coerceIn(0f, 1f) * size.height * 0.55f).coerceAtLeast(2.dp.toPx())
                    val x = i * slot + (slot - barW) / 2f
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.88f),
                        topLeft = Offset(x, size.height - h - 6.dp.toPx()),
                        size = Size(barW, h),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }
        }
    }
}

/** Small square cover for list rows and dialogs: the real artwork when available, else a neutral placeholder. */
@Composable
fun TrackThumb(
    controller: PowerampController,
    track: AudioTrack?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val art = rememberTrackArt(controller, track)
    ArtCard(
        art = art,
        track = track,
        corner = (size.value * 0.2f).coerceIn(8f, 18f).dp,
        modifier = modifier.size(size),
        iconSize = size * 0.45f
    )
}

/**
 * Album art carousel: centre card with the current track's real artwork and peeking cards for the
 * previous / next track. Smooth GPU-accelerated drag tracking, scale/fade morphing, and animated
 * track transitions (both swipe and Next/Previous button presses).
 */
@Composable
fun BerryAlbumArtCarousel(
    controller: PowerampController,
    track: AudioTrack?,
    modifier: Modifier = Modifier
) {
    val prevTrack = controller.peekPrevious()
    val nextTrack = controller.peekNext()
    val art = rememberTrackArt(controller, track)
    val prevArt = rememberTrackArt(controller, prevTrack)
    val nextArt = rememberTrackArt(controller, nextTrack)

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Distance in px from center card to the side peeking slot
    val cardSpanPx = with(density) { 210.dp.toPx() }
    val dragOffsetX = remember { Animatable(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var skipNextTrackChangeAnim by remember { mutableStateOf(false) }
    var lastTrackId by remember { mutableStateOf(track?.id) }

    // Smooth slide-in animation when track changes via Next/Prev buttons or auto-advance
    LaunchedEffect(track?.id) {
        val currentId = track?.id
        if (lastTrackId != null && currentId != null && currentId != lastTrackId) {
            if (skipNextTrackChangeAnim) {
                skipNextTrackChangeAnim = false
            } else if (controller.trackSlideTransitions) {
                val incomingFrom = if (controller.trackTransitionDirection >= 0) cardSpanPx * 0.72f else -cardSpanPx * 0.72f
                dragOffsetX.snapTo(incomingFrom)
                dragOffsetX.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = 0.86f,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
        lastTrackId = currentId
    }

    val normalizedProgress = (dragOffsetX.value / cardSpanPx).coerceIn(-1.2f, 1.2f)
    val absProgress = kotlin.math.abs(normalizedProgress).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(215.dp)
            .pointerInput(cardSpanPx) {
                var lastDeltaX = 0f
                detectHorizontalDragGestures(
                    onDragStart = {
                        isDragging = true
                        lastDeltaX = 0f
                    },
                    onDragCancel = {
                        isDragging = false
                        scope.launch {
                            dragOffsetX.animateTo(
                                0f,
                                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
                            )
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        val currentOffset = dragOffsetX.value
                        val threshold = cardSpanPx * 0.26f
                        val fastNext = lastDeltaX < -18f && currentOffset < -24f
                        val fastPrev = lastDeltaX > 18f && currentOffset > 24f
                        when {
                            currentOffset < -threshold || fastNext -> {
                                scope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragOffsetX.animateTo(
                                        -cardSpanPx,
                                        tween(durationMillis = 190, easing = FastOutSlowInEasing)
                                    )
                                    skipNextTrackChangeAnim = true
                                    controller.nextTrack()
                                    dragOffsetX.snapTo(0f)
                                }
                            }
                            currentOffset > threshold || fastPrev -> {
                                scope.launch {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    dragOffsetX.animateTo(
                                        cardSpanPx,
                                        tween(durationMillis = 190, easing = FastOutSlowInEasing)
                                    )
                                    skipNextTrackChangeAnim = true
                                    controller.previousTrackOrRestart()
                                    dragOffsetX.snapTo(0f)
                                }
                            }
                            else -> {
                                scope.launch {
                                    dragOffsetX.animateTo(
                                        0f,
                                        spring(dampingRatio = 0.80f, stiffness = Spring.StiffnessMediumLow)
                                    )
                                }
                            }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        lastDeltaX = dragAmount
                        scope.launch {
                            dragOffsetX.snapTo((dragOffsetX.value + dragAmount).coerceIn(-cardSpanPx * 1.1f, cardSpanPx * 1.1f))
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Previous peeking card (slides into center and scales up when dragging right)
        val prevFocus = normalizedProgress.coerceIn(0f, 1f)
        val prevScale = 0.795f + (1f - 0.795f) * prevFocus
        val prevAlpha = 0.72f + 0.28f * prevFocus
        ArtCard(
            art = prevArt,
            track = prevTrack,
            corner = 18.dp,
            modifier = Modifier
                .size(195.dp)
                .zIndex(if (prevFocus > 0.5f) 2f else 0f)
                .graphicsLayer {
                    translationX = -cardSpanPx + dragOffsetX.value
                    scaleX = prevScale
                    scaleY = prevScale
                    alpha = prevAlpha
                }
        )

        // Next peeking card (slides into center and scales up when dragging left)
        val nextFocus = (-normalizedProgress).coerceIn(0f, 1f)
        val nextScale = 0.795f + (1f - 0.795f) * nextFocus
        val nextAlpha = 0.72f + 0.28f * nextFocus
        ArtCard(
            art = nextArt,
            track = nextTrack,
            corner = 18.dp,
            modifier = Modifier
                .size(195.dp)
                .zIndex(if (nextFocus > 0.5f) 2f else 0f)
                .graphicsLayer {
                    translationX = cardSpanPx + dragOffsetX.value
                    scaleX = nextScale
                    scaleY = nextScale
                    alpha = nextAlpha
                }
        )

        // Center active card (follows finger, scales down smoothly as it leaves center)
        val centerScale = 1f - (1f - 0.795f) * absProgress
        val centerAlpha = 1f - 0.28f * absProgress
        ArtCard(
            art = art,
            track = track,
            corner = 18.dp,
            modifier = Modifier
                .size(195.dp)
                .zIndex(if (absProgress <= 0.5f) 2f else 1f)
                .graphicsLayer {
                    translationX = dragOffsetX.value
                    scaleX = centerScale
                    scaleY = centerScale
                    alpha = centerAlpha
                }
                .pointerInput(Unit) { detectTapGestures(onLongPress = { controller.cycleVisMode() }) },
            spectrum = if (controller.visMode != VisMode.DISABLED) controller.liveSpectrumAmplitudes else null
        )
    }
}

/**
 * Symmetrical soundwave waveform seek bar:
 * - Zero-lag finger scrubbing at native refresh rate (60/90/120Hz+)
 * - Coalesces MediaPlayer seeks so audio & UI stay silky smooth while dragging
 * - Smooth sub-bar color interpolation + glowing scrubber cursor while dragging
 */
@Composable
fun BerrySymmetricalWaveform(
    bars: List<Float>,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onScrubStart: () -> Unit = {},
    onScrubPreview: (Float) -> Unit = {},
    onScrubEnd: (Float) -> Unit = onSeek
) {
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrubPreview by rememberUpdatedState(onScrubPreview)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val haptics = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(progress.coerceIn(0f, 1f)) }
    val activeAccent = ThemeBerryAccent
    val inactiveColor = ThemeWaveUnplayed

    val barAmplitudes = remember(bars) {
        if (bars.isNotEmpty() && bars.size >= 36) {
            val target = minOf(44, bars.size)
            List(target) { i ->
                val from = i * bars.size / target
                val to = maxOf(from + 1, (i + 1) * bars.size / target)
                bars.subList(from, to).average().toFloat()
            }
        } else {
            val count = 42
            List(count) { i ->
                val norm = i.toFloat() / (count - 1)
                val env = sin(norm * PI.toFloat()).coerceIn(0.25f, 1f)
                val variation = 0.35f + 0.65f * ((i * 7 + 3) % 11) / 10f
                (env * variation).coerceIn(0.2f, 0.95f)
            }
        }
    }

    val displayProgress = (if (isDragging) dragFraction else progress).coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width).coerceIn(0f, 1f)
                    dragFraction = frac
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnSeek(frac)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { startOffset ->
                        isDragging = true
                        val frac = (startOffset.x / size.width).coerceIn(0f, 1f)
                        dragFraction = frac
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnScrubStart()
                        currentOnScrubPreview(frac)
                    },
                    onDragEnd = {
                        val finalFrac = dragFraction
                        isDragging = false
                        currentOnScrubEnd(finalFrac)
                    },
                    onDragCancel = {
                        val finalFrac = dragFraction
                        isDragging = false
                        currentOnScrubEnd(finalFrac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = frac
                        currentOnScrubPreview(frac)
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val centerY = h / 2f
        val count = barAmplitudes.size
        val gap = 3.dp.toPx()
        val totalGaps = (count - 1) * gap
        val barWidth = ((w - totalGaps) / count).coerceIn(2.5.dp.toPx(), 6.dp.toPx())
        val actualTotalWidth = count * barWidth + (count - 1) * gap
        val startX = (w - actualTotalWidth) / 2f
        val headX = startX + displayProgress * actualTotalWidth

        for (i in 0 until count) {
            val bx = startX + i * (barWidth + gap)
            val barEnd = bx + barWidth
            // Smooth sub-bar color blend so progress glides pixel-by-pixel instead of jumping bar-by-bar
            val barFill = ((headX - bx) / barWidth).coerceIn(0f, 1f)
            val barColor = when {
                barFill >= 1f -> activeAccent
                barFill <= 0f -> inactiveColor
                else -> lerp(inactiveColor, activeAccent, barFill)
            }

            val amp = barAmplitudes[i]
            // Subtle magnification on the bar right under the playhead while scrubbing
            val proximityBoost = if (isDragging) {
                val dist = kotlin.math.abs((bx + barEnd) * 0.5f - headX)
                val boost = (1f - (dist / (barWidth * 3.5f))).coerceIn(0f, 1f)
                1f + 0.22f * boost
            } else 1f
            val barHalfHeight = (h * 0.42f * amp * proximityBoost).coerceIn(3.dp.toPx(), h * 0.48f)

            drawRoundRect(
                color = barColor,
                topLeft = Offset(bx, centerY - barHalfHeight),
                size = Size(barWidth, barHalfHeight * 2f),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }

        // Sleek glowing playhead needle while scrubbing
        if (isDragging) {
            val clampedHeadX = headX.coerceIn(startX, startX + actualTotalWidth)
            drawLine(
                color = activeAccent.copy(alpha = 0.35f),
                start = Offset(clampedHeadX, 2.dp.toPx()),
                end = Offset(clampedHeadX, h - 2.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(clampedHeadX, 2.dp.toPx()),
                end = Offset(clampedHeadX, h - 2.dp.toPx()),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
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
    val currentValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current

    var isDragging by remember { mutableStateOf(false) }
    var dragAccumulator by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    // Raw (un-detented) position of the finger-driven value, so the magnetic centre never "eats" movement
    var rawAccumulator by remember { mutableFloatStateOf(value.coerceIn(0f, 1f)) }
    // Gesture mode is decided ONCE at touch-down so it can never flip mid-drag (the cause of jitter).
    var angularMode by remember { mutableStateOf(false) }
    var inDetent by remember { mutableStateOf(false) }

    // Animated value for programmatic changes (presets, reset, tap). Snaps while the finger is down so the
    // knob follows the touch with zero lag and never "springs back" on release.
    val animated = remember { Animatable(value.coerceIn(0f, 1f)) }
    LaunchedEffect(value, isDragging) {
        val target = value.coerceIn(0f, 1f)
        if (isDragging) animated.snapTo(target)
        else animated.animateTo(target, tween(durationMillis = 220, easing = FastOutSlowInEasing))
    }

    val clamped = (if (isDragging) dragAccumulator else animated.value).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(enabled, bipolar) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { start ->
                        isDragging = true
                        dragAccumulator = currentValue
                        rawAccumulator = currentValue
                        inDetent = bipolar && kotlin.math.abs(currentValue - 0.5f) < 0.001f
                        val cx = this.size.width.toFloat() / 2f
                        val cy = this.size.height.toFloat() / 2f
                        // Touches that begin on the outer ring behave like a real dial (circular motion);
                        // touches in the middle use fine vertical drag. Fixed for the whole gesture.
                        angularMode = kotlin.math.hypot(start.x - cx, start.y - cy) > cx * 0.55f
                    },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val cx = this.size.width.toFloat() / 2f
                        val cy = this.size.height.toFloat() / 2f

                        val effectiveDelta = if (angularMode) {
                            val prevPos = change.position - dragAmount
                            val a1 = atan2((prevPos.y - cy).toDouble(), (prevPos.x - cx).toDouble()).toFloat()
                            val a2 = atan2((change.position.y - cy).toDouble(), (change.position.x - cx).toDouble()).toFloat()
                            var aDiff = a2 - a1
                            while (aDiff > PI.toFloat()) aDiff -= (2f * PI.toFloat())
                            while (aDiff < -PI.toFloat()) aDiff += (2f * PI.toFloat())
                            aDiff / (290f * (PI.toFloat() / 180f))
                        } else {
                            // ~200dp of vertical travel covers the full range
                            -dragAmount.y / (density.density * 200f)
                        }

                        rawAccumulator = (rawAccumulator + effectiveDelta).coerceIn(0f, 1f)
                        var nextVal = rawAccumulator

                        // Magnetic centre detent for bipolar knobs: snaps near 0.5 but releases smoothly
                        if (bipolar) {
                            val near = kotlin.math.abs(rawAccumulator - 0.5f) < 0.025f
                            if (near) nextVal = 0.5f
                            if (near && !inDetent) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            inDetent = near
                        }

                        if (nextVal != dragAccumulator) {
                            dragAccumulator = nextVal
                            currentOnValueChange(nextVal)
                        }
                    }
                )
            }
            .pointerInput(enabled, bipolar) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = {
                        val resetVal = if (bipolar) 0.5f else 0f
                        dragAccumulator = resetVal
                        rawAccumulator = resetVal
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
    val currentDb by rememberUpdatedState(dbValue.coerceIn(-15f, 15f))
    val haptics = LocalHapticFeedback.current
    var isDragging by remember { mutableStateOf(false) }
    var dragDb by remember { mutableFloatStateOf(dbValue.coerceIn(-15f, 15f)) }
    var rawDb by remember { mutableFloatStateOf(dbValue.coerceIn(-15f, 15f)) }
    var inDetent by remember { mutableStateOf(false) }

    // Snap while the finger is down (zero lag), glide for presets / reset / tap.
    val animatedDbState = remember { Animatable(dbValue.coerceIn(-15f, 15f)) }
    LaunchedEffect(if (isDragging) null else dbValue) {
        if (!isDragging) {
            val target = dbValue.coerceIn(-15f, 15f)
            animatedDbState.animateTo(target, tween(durationMillis = 200, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(isDragging) {
        if (!isDragging) {
            animatedDbState.snapTo(dragDb.coerceIn(-15f, 15f))
        }
    }

    val effectiveDb = if (isDragging) dragDb else animatedDbState.value
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
                            dragDb = currentDb
                            rawDb = currentDb
                            inDetent = kotlin.math.abs(currentDb) < 0.001f
                        },
                        onDragEnd = { isDragging = false },
                        onDragCancel = { isDragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Relative drag: thumb moves exactly with the finger (track = height - 48dp padding)
                            val trackPx = (size.height.toFloat() - 48.dp.toPx()).coerceAtLeast(40f)
                            rawDb = (rawDb - dragAmount / trackPx * 30f).coerceIn(-15f, 15f)
                            var newDb = rawDb
                            val near = kotlin.math.abs(rawDb) < 0.35f
                            if (near) newDb = 0f
                            if (near && !inDetent) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            inDetent = near
                            if (newDb != dragDb) {
                                dragDb = newDb
                                currentOnValueChange(newDb)
                            }
                        }
                    )
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onDoubleTap = {
                            dragDb = 0f
                            rawDb = 0f
                            currentOnValueChange(0f)
                        },
                        onTap = { offset ->
                            val pad = 24.dp.toPx()
                            val trackPx = (size.height.toFloat() - 2f * pad).coerceAtLeast(40f)
                            val newFrac = (1f - ((offset.y - pad) / trackPx)).coerceIn(0f, 1f)
                            val newDb = (newFrac * 30f - 15f)
                            dragDb = newDb
                            rawDb = newDb
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
 * Modern 5-Button Bottom Navigation Bar matching the reference WhatsApp design:
 * Clean white background with top shadow, Home (Berry active), Album, Center Raised Berry (+) Button,
 * Playlist, Profile, and mini-player support.
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
            .background(PowerampBg)
            .navigationBarsPadding()
    ) {
        if (showMiniPlayer && track != null) {
            // Elegant Light Mini-Player Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PowerampSurface)
                    .border(1.dp, PowerampBorderGray, RoundedCornerShape(16.dp))
                    .clickable {
                        controller.showLyricsScreen = false
                        controller.currentMainTab = MainNavTab.PLAYER
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mini artwork thumbnail (real cover when the file has one)
                TrackThumb(controller, track, 42.dp)

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
                        color = PowerampTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mini Play/Pause Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(ThemeBerryPrimary)
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = PowerampOnAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 5-Item Navigation Bar: Home | Album | (+) Raised Berry Button | Playlist | Profile
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(PowerampBg)
                .border(width = 0.8.dp, color = PowerampBorderGray),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isHomeActive = controller.currentMainTab == MainNavTab.LIBRARY && controller.activeLibraryCategory == null
                val isAlbumActive = controller.currentMainTab == MainNavTab.LIBRARY && controller.activeLibraryCategory == LibraryCategory.ALBUMS
                val isPlaylistActive = controller.currentMainTab == MainNavTab.LIBRARY && controller.activeLibraryCategory == LibraryCategory.PLAYLISTS

                // 1. Home Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            controller.showMainMenuSheet = false
                            controller.showLyricsScreen = false
                            controller.activeLibraryCategory = null
                            controller.activeSubFilter = null
                            controller.currentMainTab = MainNavTab.LIBRARY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Home,
                        contentDescription = "Home",
                        tint = if (isHomeActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Home",
                        color = if (isHomeActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        fontSize = 11.sp,
                        fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // 2. Album Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            controller.showMainMenuSheet = false
                            controller.showLyricsScreen = false
                            controller.activeSubFilter = null
                            controller.activeLibraryCategory = LibraryCategory.ALBUMS
                            controller.currentMainTab = MainNavTab.LIBRARY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Album,
                        contentDescription = "Album",
                        tint = if (isAlbumActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Album",
                        color = if (isAlbumActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        fontSize = 11.sp,
                        fontWeight = if (isAlbumActive) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // 3. Center raised Equalizer button (opens the Equalizer / Volume / Reverb screen)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    val eqActive = controller.currentMainTab == MainNavTab.EQUALIZER && !controller.showSettingsScreen
                    Box(
                        modifier = Modifier
                            .offset(y = (-6).dp)
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ThemeBerryPrimary)
                            .border(if (eqActive) 3.dp else 0.dp, PowerampTextPrimary.copy(alpha = 0.55f), CircleShape)
                            .clickable {
                                controller.showLyricsScreen = false
                                controller.showSettingsScreen = false
                                controller.showMainMenuSheet = false
                                controller.currentMainTab = MainNavTab.EQUALIZER
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Equalizer,
                            contentDescription = "Equalizer",
                            tint = PowerampOnAccent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // 4. Playlist Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            controller.showMainMenuSheet = false
                            controller.showLyricsScreen = false
                            controller.activeSubFilter = null
                            controller.activeLibraryCategory = LibraryCategory.PLAYLISTS
                            controller.currentMainTab = MainNavTab.LIBRARY
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.QueueMusic,
                        contentDescription = "Playlist",
                        tint = if (isPlaylistActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Playlist",
                        color = if (isPlaylistActive) ThemeBerryPrimary else Color(0xFF8A8E99),
                        fontSize = 11.sp,
                        fontWeight = if (isPlaylistActive) FontWeight.Bold else FontWeight.Medium
                    )
                }

                // 5. Settings Button
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable {
                            controller.showLyricsScreen = false
                            controller.showSettingsScreen = true
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = if (controller.showSettingsScreen) ThemeBerryPrimary else Color(0xFF8A8E99),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Settings",
                        color = if (controller.showSettingsScreen) ThemeBerryPrimary else Color(0xFF8A8E99),
                        fontSize = 11.sp,
                        fontWeight = if (controller.showSettingsScreen) FontWeight.Bold else FontWeight.Medium
                    )
                }
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
 * Interactive Vertical A-Z Fast Scroller Strip on the right edge of Library / All Songs.
 * Supports both tap and continuous vertical drag with haptic ticks and active-letter highlighting.
 */
@Composable
fun PowerampAlphabetScroller(
    onLetterSelected: (letter: Char, isDragging: Boolean) -> Unit,
    onActiveLetterChanged: (letter: Char?, yFraction: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val letters = remember {
        listOf('˄', '0', 'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', '#')
    }
    val currentOnLetterSelected by rememberUpdatedState(onLetterSelected)
    val currentOnActiveChanged by rememberUpdatedState(onActiveLetterChanged)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var dismissJob by remember { mutableStateOf<Job?>(null) }

    Column(
        modifier = modifier
            .width(24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xA6121315))
            .pointerInput(letters) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dismissJob?.cancel()

                    fun letterAt(y: Float): Pair<Char, Float> {
                        val h = size.height.toFloat().coerceAtLeast(1f)
                        val frac = (y / h).coerceIn(0f, 0.9999f)
                        val idx = (frac * letters.size).toInt().coerceIn(0, letters.lastIndex)
                        return letters[idx] to frac
                    }

                    val (initialChar, initialFrac) = letterAt(down.position.y)
                    activeLetter = initialChar
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnActiveChanged(initialChar, initialFrac)
                    currentOnLetterSelected(initialChar, false)

                    var lastChar = initialChar
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        val (ch, frac) = letterAt(change.position.y)
                        currentOnActiveChanged(ch, frac)
                        if (ch != lastChar) {
                            lastChar = ch
                            activeLetter = ch
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            currentOnLetterSelected(ch, true)
                        }
                    }

                    // Keep the big letter bubble visible briefly after release for clear tap feedback
                    dismissJob?.cancel()
                    dismissJob = scope.launch {
                        delay(420L)
                        activeLetter = null
                        currentOnActiveChanged(null, 0.5f)
                    }
                }
            }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { ch ->
            val isActive = activeLetter == ch
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ch.toString(),
                    color = if (isActive) ThemeBerryPrimary else Color(0xFFB5B7BC),
                    fontSize = if (isActive) 11.sp else 8.5.sp,
                    lineHeight = if (isActive) 11.sp else 9.sp,
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Large animated floating Alphabet Bubble shown when tapping or dragging on [PowerampAlphabetScroller].
 */
@Composable
fun PowerampAlphabetBubble(
    letter: Char?,
    modifier: Modifier = Modifier
) {
    val lastNonNull = remember { mutableStateOf('A') }
    if (letter != null) lastNonNull.value = letter

    AnimatedVisibility(
        visible = letter != null,
        enter = scaleIn(
            initialScale = 0.55f,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 480f)
        ) + fadeIn(tween(110)),
        exit = scaleOut(
            targetScale = 0.72f,
            animationSpec = tween(160, easing = FastOutSlowInEasing)
        ) + fadeOut(tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(86.dp)
                .clip(RoundedCornerShape(topStart = 43.dp, topEnd = 43.dp, bottomStart = 43.dp, bottomEnd = 12.dp))
                .background(PowerampDialogBg.copy(alpha = 0.96f))
                .border(2.2.dp, ThemeBerryPrimary, RoundedCornerShape(topStart = 43.dp, topEnd = 43.dp, bottomStart = 43.dp, bottomEnd = 12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lastNonNull.value.toString(),
                color = ThemeBerryPrimary,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}
