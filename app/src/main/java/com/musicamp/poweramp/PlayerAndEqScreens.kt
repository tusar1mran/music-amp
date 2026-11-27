package com.musicamp.poweramp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlin.math.roundToInt
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeoutOrNull
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 100% Pixel-Perfect Poweramp Main Player Screen (`01_current.png`).
 */
@Composable
private fun PowerampPlayerScreenContent(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    val track = controller.currentTrack
    val elapsedSec = (controller.currentPositionMs / 1000L).coerceAtLeast(0L)
    val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    val durationSec = (controller.durationMs / 1000L).coerceAtLeast(0L)
    // Internet streams have no duration: show LIVE and disable seeking
    val isLive = controller.durationMs <= 0L && controller.currentStream != null
    val durationStr = if (isLive) "LIVE" else "%d:%02d".format(durationSec / 60, durationSec % 60)
    // Smooth seekbar: progress glides every frame; off, it jumps once per second
    val seekProgress = when {
        controller.durationMs <= 0L -> 0f
        controller.smoothSeekbarAnimation || controller.isScrubbing -> controller.progressFraction
        else -> ((controller.currentPositionMs / 1000L) * 1000L).toFloat() / controller.durationMs.toFloat()
    }.coerceIn(0f, 1f)
    val coroutineScope = rememberCoroutineScope()

    // In Poweramp, when Visualization is set to Fade Controls or Full Screen while playing,
    // the UI controls smoothly fade opacity (`02_playing_t1.png`, `04_playing_vis1.png`)
    val controlsAlpha by animateFloatAsState(
        targetValue = when {
            controller.visMode == VisMode.FULL_SCREEN && controller.isPlaying -> 0.18f
            controller.visMode == VisMode.FADE_CONTROLS && controller.isPlaying -> controller.visFadedControlsOpacity
            else -> 1f
        },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "controlsFadeAlpha"
    )

    // Settings > Background: the opaque header is see-through over a blurred / glow background
    val plainBackground = controller.backgroundType == PLAYER_BG_BLACK
    Box(modifier = modifier.fillMaxSize()) {
        PlayerBackground(controller = controller, track = track, modifier = Modifier.matchParentSize())
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP CURVED BERRY HEADER & ALBUM ART CAROUSEL
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.30f)
            ) {
                // Berry Gradient Backdrop with curved bottom matching reference design
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.60f)
                        .clip(RoundedCornerShape(bottomStart = 38.dp, bottomEnd = 38.dp))
                        .background(ThemeBerryHeaderGradient, alpha = if (plainBackground) 1f else 0.3f)
                        .statusBarsPadding()
                ) {
                    // Header Top Navigation Row: Back < | "Now Playing" | Tune/EQ
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                controller.currentMainTab = MainNavTab.LIBRARY
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Text(
                            text = "Now Playing",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold.appWeight
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    controller.currentMainTab = MainNavTab.EQUALIZER
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Tune,
                                    contentDescription = "Equalizer",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    controller.showSettingsScreen = true
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // ALBUM ART CAROUSEL (Center card + left/right peek cards)
                BerryAlbumArtCarousel(
                    controller = controller,
                    track = track,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(top = 40.dp, bottom = 4.dp)
                )
            }

            // 2. METADATA ROW: Share Icon | Title & Artist | 3-Dot Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        track?.let { controller.shareTrack(it) }
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = PowerampTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                AnimatedContent(
                    targetState = track,
                    transitionSpec = {
                        val dir = if (controller.trackTransitionDirection >= 0) 1 else -1
                        (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { dir * it / 3 } +
                            fadeIn(tween(200)))
                            .togetherWith(
                                slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -dir * it / 3 } +
                                    fadeOut(tween(150))
                            )
                    },
                    label = "trackMetadataTransition",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                ) { animatedTrack ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = animatedTrack?.title ?: "No music",
                            color = PowerampTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold.appWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = animatedTrack?.artist ?: "No track",
                            color = PowerampTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium.appWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = {
                        controller.actionTargetTrack = track
                        controller.showTrackActionDialog = true
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Options",
                        tint = PowerampTextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Live Seeking Indicator Pill when holding Next / Previous button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controller.isSeekingFwd || controller.isSeekingRwd,
                    enter = scaleIn(initialScale = 0.85f, animationSpec = tween(140)) + fadeIn(tween(120)),
                    exit = scaleOut(targetScale = 0.85f, animationSpec = tween(140)) + fadeOut(tween(120))
                ) {
                    val deltaSec = (controller.seekingAccumulatedDeltaMs / 1000L)
                    val label = if (controller.isSeekingFwd) "⏩ +${kotlin.math.abs(deltaSec)}s" else "⏪ -${kotlin.math.abs(deltaSec)}s"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ThemeBerryPrimary.copy(alpha = 0.22f))
                            .border(1.dp, ThemeBerryPrimary.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = label,
                            color = ThemeBerryPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold.appWeight
                        )
                    }
                }
            }

            // 3. TIME & SYMMETRICAL SOUNDWAVE WAVEFORM SECTION
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = elapsedStr,
                    color = if (controller.isScrubbing || controller.isSeekingFwd || controller.isSeekingRwd) ThemeBerryPrimary else Color(0xFF4A4E57),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold.appWeight
                )

                // Symmetrical Waveform Bar in center (zero-lag scrubbing at native refresh rate)
                BerrySymmetricalWaveform(
                    bars = track?.waveformBars ?: emptyList(),
                    progress = seekProgress,
                    onSeek = { if (controller.durationMs > 0L) controller.seekToFraction(it) },
                    onScrubStart = { if (controller.durationMs > 0L) controller.isScrubbing = true },
                    onScrubPreview = { if (controller.durationMs > 0L) controller.previewScrubFraction(it) },
                    onScrubEnd = { frac ->
                        controller.isScrubbing = false
                        if (controller.durationMs > 0L) controller.seekToFraction(frac)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .padding(horizontal = 10.dp)
                )

                Text(
                    text = durationStr,
                    color = Color(0xFF4A4E57),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold.appWeight
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4. TRANSPORT CONTROLS: Shuffle | Prev Track (hold=rewind) | Glowing Play/Pause | Next Track (hold=fast-forward) | Repeat
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Button
                val isShuffle = controller.shuffleMode != ShuffleMode.OFF
                IconButton(
                    onClick = { controller.cycleShuffleMode() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) ThemeBerryPrimary else PowerampIconStrong,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Previous Track (tap = previous / restart, press & hold = smooth rewind)
                HoldToSeekTransportButton(
                    icon = Icons.Filled.SkipPrevious,
                    holdIcon = Icons.Filled.FastRewind,
                    contentDescription = "Previous Track / Hold to Rewind",
                    isSeeking = controller.isSeekingRwd,
                    onTap = { controller.previousTrackOrRestart() },
                    onSeekStart = {
                        controller.isSeekingRwd = true
                        controller.seekingAccumulatedDeltaMs = 0L
                    },
                    onSeekTick = { stepMs ->
                        controller.seekingAccumulatedDeltaMs -= stepMs
                        controller.seekRelative(-stepMs)
                    },
                    onSeekEnd = {
                        controller.isSeekingRwd = false
                        controller.seekingAccumulatedDeltaMs = 0L
                    }
                )

                // Main Play/Pause Button with Berry Halo / Glow Ring
                Box(
                    modifier = Modifier
                        .size(76.dp)
                        .clip(CircleShape)
                        .background(ThemeBerryPrimary.copy(alpha = 0.16f)) // Outer glow ring
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(ThemeBerryLight, ThemeBerryDark)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = controller.isPlaying,
                            transitionSpec = {
                                if (controller.playPauseMorphAnimation) {
                                    (scaleIn(initialScale = 0.72f, animationSpec = tween(160)) + fadeIn(tween(140)))
                                        .togetherWith(scaleOut(targetScale = 0.72f, animationSpec = tween(140)) + fadeOut(tween(120)))
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "playPauseMorph"
                        ) { playing ->
                            Icon(
                                imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play / Pause",
                                tint = PowerampOnAccent,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }

                // Next Track (tap = next track, press & hold = smooth fast-forward)
                HoldToSeekTransportButton(
                    icon = Icons.Filled.SkipNext,
                    holdIcon = Icons.Filled.FastForward,
                    contentDescription = "Next Track / Hold to Fast Forward",
                    isSeeking = controller.isSeekingFwd,
                    onTap = { controller.nextTrack() },
                    onSeekStart = {
                        controller.isSeekingFwd = true
                        controller.seekingAccumulatedDeltaMs = 0L
                    },
                    onSeekTick = { stepMs ->
                        controller.seekingAccumulatedDeltaMs += stepMs
                        controller.seekRelative(stepMs)
                    },
                    onSeekEnd = {
                        controller.isSeekingFwd = false
                        controller.seekingAccumulatedDeltaMs = 0L
                    }
                )

                // Repeat Button
                val isRepeat = controller.repeatMode != RepeatMode.OFF
                IconButton(
                    onClick = { controller.cycleRepeatMode() },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = if (controller.repeatMode == RepeatMode.REPEAT_SONG || controller.repeatMode == RepeatMode.SINGLE_SONG) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (isRepeat) ThemeBerryPrimary else PowerampIconStrong,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 5. Rating Pill + Audio Meta Info Pill (each can be hidden in Settings > Look and Feel)
            val showRating = controller.showRatingPill && track != null && controller.currentStream == null
            val showMeta = controller.showMetaLine && track != null
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRating && track != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(PowerampPillBg),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { controller.toggleUnlikeCurrentTrack() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (track.rating == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                contentDescription = "Dislike",
                                tint = if (track.rating == -1) ThemeBerryPrimary else PowerampTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .clickable { controller.toggleLikeCurrentTrack() }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (track.rating == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                contentDescription = "Like",
                                tint = if (track.rating == 1) ThemeBerryPrimary else PowerampTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                if (showMeta) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PowerampPillBg)
                            .clickable {
                                controller.actionTargetTrack = track
                                controller.showInfoTagsSheet = true
                            }
                            .padding(horizontal = 12.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isLive) "LIVE STREAM" else track?.bottomBadgeInfo ?: "",
                            color = PowerampTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold.appWeight,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            // 6. Bottom Navigation Bar (4 buttons without duplicate mini-player)
            PowerampBottomDock(
                controller = controller,
                showMiniPlayer = false
            )
        }
    }
}

/**
 * Transport button (Previous / Next) supporting:
 * - Quick tap: skip track / restart
 * - Press & hold (> 300ms): continuous smooth seeking with accelerating step size & haptic feedback
 */
@Composable
private fun HoldToSeekTransportButton(
    icon: ImageVector,
    holdIcon: ImageVector,
    contentDescription: String,
    isSeeking: Boolean,
    onTap: () -> Unit,
    onSeekStart: () -> Unit,
    onSeekTick: (stepMs: Long) -> Unit,
    onSeekEnd: () -> Unit
) {
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSeekStart by rememberUpdatedState(onSeekStart)
    val currentOnSeekTick by rememberUpdatedState(onSeekTick)
    val currentOnSeekEnd by rememberUpdatedState(onSeekEnd)
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = when {
            isSeeking -> 1.16f
            isPressed -> 0.90f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "transportBtnScale"
    )

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(if (isSeeking) ThemeBerryPrimary.copy(alpha = 0.16f) else Color.Transparent)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    isPressed = true

                    // Wait up to 300ms for release; if released early, treat as a normal tap
                    val releasedEarly = withTimeoutOrNull(300L) {
                        waitForUpOrCancellation()
                    }
                    if (releasedEarly != null) {
                        isPressed = false
                        currentOnTap()
                    } else {
                        // Still held after 300ms -> enter continuous seek mode!
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnSeekStart()
                        val seekJob: Job = scope.launch {
                            var elapsedHoldMs = 0L
                            while (isActive) {
                                // Smoothly accelerate seek speed the longer the button is held
                                val stepMs = when {
                                    elapsedHoldMs < 1200L -> 2000L
                                    elapsedHoldMs < 3000L -> 3500L
                                    else -> 5000L
                                }
                                currentOnSeekTick(stepMs)
                                delay(160L)
                                elapsedHoldMs += 160L
                            }
                        }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            seekJob.cancel()
                            isPressed = false
                            currentOnSeekEnd()
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSeeking) holdIcon else icon,
            contentDescription = contentDescription,
            tint = if (isSeeking) ThemeBerryPrimary else PowerampIconStrong,
            modifier = Modifier.size(34.dp)
        )
    }
}

/**
 * 100% Pixel-Perfect Poweramp Equalizer / Volume / Reverb Screen (`03_eq_tab1.png`, `07_eq_volume_tab.png`, `08_eq_reverb_tab.png`).
 */
@Composable
private fun PowerampEqualizerScreenContent(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
            .statusBarsPadding()
    ) {
        // Top Action Mode Bar: 3 Sub-Tabs (Equalizer Sliders Icon | Volume Knob Icon | Reverb Speaker Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(PowerampPillBg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sub-tab 1: Equalizer
            val eqActive = controller.currentEqSubTab == EqSubTab.EQUALIZER
            val eqTint by animateColorAsState(if (eqActive) ThemeBerryPrimary else Color(0xFF8E9095), label = "eqTabTint")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { controller.currentEqSubTab = EqSubTab.EQUALIZER },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Tune,
                    contentDescription = "Equalizer Tab",
                    tint = eqTint,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Sub-tab 2: Volume / Tempo / Balance / Stereo Expand (Rotary dial icon)
            val volActive = controller.currentEqSubTab == EqSubTab.VOLUME
            val volTint by animateColorAsState(if (volActive) ThemeBerryPrimary else Color(0xFF8E9095), label = "volTabTint")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { controller.currentEqSubTab = EqSubTab.VOLUME },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(volTint),
                    contentAlignment = Alignment.TopStart
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 5.dp, top = 5.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }

            // Sub-tab 3: Reverb
            val revActive = controller.currentEqSubTab == EqSubTab.REVERB
            val revTint by animateColorAsState(if (revActive) ThemeBerryPrimary else Color(0xFF8E9095), label = "revTabTint")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { controller.currentEqSubTab = EqSubTab.REVERB },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Speaker,
                    contentDescription = "Reverb Tab",
                    tint = revTint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        // Main EQ Sub-Tab Content Area with smooth horizontal slide animation between Equalizer / Volume / Reverb tabs
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AnimatedContent(
                targetState = controller.currentEqSubTab,
                transitionSpec = {
                    val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(260, easing = FastOutSlowInEasing)) { dir * it / 2 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(220)) { -dir * it / 2 } + fadeOut(tween(180)))
                },
                label = "eqSubTabSlide"
            ) { subTab ->
                when (subTab) {
                    EqSubTab.EQUALIZER -> EqualizerBandsAndToneSubTab(controller)
                    EqSubTab.VOLUME -> VolumeTempoBalanceSubTab(controller)
                    EqSubTab.REVERB -> ReverbDspSubTab(controller)
                }
            }
        }

        // Floating Mini-Player + Bottom Nav Dock
        PowerampBottomDock(
            controller = controller,
            showMiniPlayer = true
        )
    }
}

@Composable
private fun EqualizerBandsAndToneSubTab(controller: PowerampController) {
    val parametric = controller.parametricEqEnabled
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
        // 0. Graphic / Parametric mode switch + engine notes
        EqModeSwitch(
            parametric = parametric,
            onChange = { controller.setParametricEq(it) },
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 2.dp)
        )
        EqEngineNotes(controller, parametric)

        if (parametric) {
            ParametricEqEditor(
                controller = controller,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else {
            // 1. Preamp + Horizontally Scrollable 10-Band Graphic Equalizer Sliders (`03_eq_tab1.png`)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Preamp slider on left in its dark pill container
                PowerampEqBandSlider(
                    label = "Preamp",
                    dbValue = controller.preampDb,
                    onValueChange = {
                        controller.preampDb = it
                        controller.applyEqualizerBands()
                        controller.applyVolumeAndBalance()
                    },
                    isPreamp = true,
                    enabled = controller.eqEnabled
                )

                Spacer(modifier = Modifier.width(4.dp))

                // 10 frequency sliders (31, 62, 125, 250, 500, 1K, 2K, 4K, 8K, 16K)
                val eqListState = rememberLazyListState()
                LazyRow(
                    state = eqListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(
                        items = controller.bandFrequencies,
                        key = { index, label -> "$index-$label" },
                        contentType = { _, _ -> "eqBand" }
                    ) { index, freqLabel ->
                        val gain = controller.bandGainsDb.getOrElse(index) { 0f }
                        PowerampEqBandSlider(
                            label = freqLabel,
                            dbValue = gain,
                            onValueChange = { newDb ->
                                controller.bandGainsDb[index] = newDb
                                controller.applyEqualizerBands()
                            },
                            isPreamp = false,
                            enabled = controller.eqEnabled
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Frequency Response Curve Scroller (`03_eq_tab1.png`) with live spectrum during playback
            PowerampFrequencyResponseGraph(
                bandGains = controller.bandGainsDb,
                preampDb = controller.preampDb,
                eqEnabled = controller.eqEnabled,
                isPlaying = controller.isPlaying,
                spectrumBars = controller.liveSpectrumAmplitudes
            )
        }

        // 3. Info Badge (`NO DVC EQ 10 TON LMT`), reflecting the live processing chain
        val badge = buildString {
            append(if (controller.dvcActive) "DVC" else "NO DVC")
            if (controller.eqEnabled) {
                append(if (parametric) " PEQ ${controller.parametricBands.count { it.enabled }}" else " EQ 10")
            }
            if (controller.toneEnabled) append(" TON")
            if (controller.limitEnabled) append(" LMT")
            if (controller.bitPerfectActive) append(" · BIT-PERFECT")
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF151618))
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    color = Color(0xFFD2D4D8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // 4. Lower Controls Area: Left column (Equ, Tone, Limit toggle pills) + Right area (Preset button, 3-dot menu, Bass & Treble rotary knobs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Left column: Equ, Tone, Limit buttons
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EqTogglePillButton(
                    text = "Equ",
                    checked = controller.eqEnabled,
                    onClick = {
                        controller.eqEnabled = !controller.eqEnabled
                        controller.applyToneAndEffects()
                        controller.applyVolumeAndBalance()
                    }
                )
                EqTogglePillButton(
                    text = "Tone",
                    checked = controller.toneEnabled,
                    onClick = {
                        controller.toneEnabled = !controller.toneEnabled
                        controller.applyToneAndEffects()
                    }
                )
                EqTogglePillButton(
                    text = "Limit",
                    checked = controller.limitEnabled,
                    onClick = {
                        controller.limitEnabled = !controller.limitEnabled
                        controller.showToast(if (controller.limitEnabled) "Limiter Enabled" else "Limiter Disabled")
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right area: Top row (Preset outlined pill + 3-dot circle menu), Bottom row (Bass knob + Treble knob)
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(8.dp))

                    // Outlined Preset Button
                    Box(
                        modifier = Modifier
                            .width(155.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                            .clickable { controller.showEqPresetSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = controller.selectedPresetName,
                            color = PowerampTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold.appWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // 3-dot menu circle
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, PowerampBorderGray, CircleShape)
                            .clickable { controller.resetEqualizer() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Reset Equalizer",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Bass & Treble Knobs Row (`03_eq_tab1.png`)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Bass label + value + knob
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(46.dp)) {
                            Text(
                                text = "Bass",
                                color = PowerampTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold.appWeight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${controller.bassPercent}%",
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold.appWeight
                            )
                        }
                        PowerampRotaryKnob(
                            value = controller.bassPercent / 100f,
                            onValueChange = {
                                controller.bassPercent = (it * 100f).roundToInt().coerceIn(0, 100)
                                controller.applyToneAndEffects()
                            },
                            size = 82.dp,
                            enabled = controller.toneEnabled,
                            showGreenArc = controller.bassPercent > 0
                        )
                    }

                    // Treble label + value + knob
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.width(50.dp)) {
                            Text(
                                text = "Treble",
                                color = PowerampTextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold.appWeight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${controller.treblePercent}%",
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold.appWeight
                            )
                        }
                        PowerampRotaryKnob(
                            value = controller.treblePercent / 100f,
                            onValueChange = {
                                controller.treblePercent = (it * 100f).roundToInt().coerceIn(0, 100)
                                controller.applyToneAndEffects()
                            },
                            size = 82.dp,
                            enabled = controller.toneEnabled,
                            showGreenArc = controller.treblePercent > 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EqTogglePillButton(
    text: String,
    checked: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (checked) Color(0xFF28292D) else Color(0xFF141517),
        animationSpec = tween(220),
        label = "eqPillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (checked) Color.White else Color(0xFF9EA0A5),
        animationSpec = tween(220),
        label = "eqPillText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (checked) Color.Transparent else Color(0xFF3A3B3F),
        animationSpec = tween(220),
        label = "eqPillBorder"
    )
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold.appWeight
        )
    }
}

/**
 * Sub-Tab 2: Volume / Balance / Stereo Expand / Tempo / Mono (`07_eq_volume_tab.png`).
 */
@Composable
private fun VolumeTempoBalanceSubTab(controller: PowerampController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Row 1: Balance Knob (left) & Stereo Expand Knob (right)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Balance Knob (-1.00 .. +1.00, 0.00 at top center)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerampRotaryKnob(
                    value = ((controller.balanceValue + 1f) / 2f).coerceIn(0f, 1f),
                    onValueChange = { norm ->
                        val raw = (norm * 2f - 1f)
                        controller.balanceValue = if (kotlin.math.abs(raw) < 0.03f) 0f else (raw * 100f).roundToInt() / 100f
                        controller.applyVolumeAndBalance()
                    },
                    size = 114.dp,
                    bipolar = true,
                    showGreenArc = kotlin.math.abs(controller.balanceValue) > 0.02f
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Balance",
                    color = PowerampTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
                Text(
                    text = "%.2f".format(controller.balanceValue),
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Stereo Expand Knob (0% .. 100%)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerampRotaryKnob(
                    value = controller.stereoExpandPercent / 100f,
                    onValueChange = { norm ->
                        controller.stereoExpandPercent = (norm * 100f).roundToInt().coerceIn(0, 100)
                        controller.applyToneAndEffects()
                    },
                    size = 114.dp,
                    showGreenArc = controller.stereoExpandPercent > 0
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Stereo Expand",
                    color = PowerampTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
                Text(
                    text = "${controller.stereoExpandPercent}%",
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }
        }

        // Row 2: Tempo Toggle Button (left), Tempo Knob (center, 1.00x), + / - Pill Buttons (right)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
        ) {
            val tempoBg by animateColorAsState(if (controller.tempoEnabled) Color(0xFF28292D) else Color.Transparent, label = "tempoBg")
            val tempoText by animateColorAsState(if (controller.tempoEnabled) Color.White else Color(0xFFB5B7BC), label = "tempoText")

            // Tempo toggle button at top-left
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(72.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(tempoBg)
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable {
                        controller.tempoEnabled = !controller.tempoEnabled
                        controller.applyTempo()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tempo",
                    color = tempoText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Tempo Knob in center
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PowerampRotaryKnob(
                    value = ((controller.tempoMultiplier - 0.5f) / 1.5f).coerceIn(0f, 1f),
                    onValueChange = { norm ->
                        controller.tempoMultiplier = ((0.5f + norm * 1.5f) * 100f).roundToInt() / 100f
                        controller.applyTempo()
                    },
                    size = 122.dp,
                    enabled = controller.tempoEnabled,
                    showGreenArc = controller.tempoEnabled
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "%.2fx".format(controller.tempoMultiplier),
                    color = if (controller.tempoEnabled) PowerampTextSecondary else Color(0xFF3E4044),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // + and - buttons on right
            Column(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xFF111214))
                        .clickable(enabled = controller.tempoEnabled) {
                            controller.tempoMultiplier = (((controller.tempoMultiplier + 0.05f) * 100f).roundToInt() / 100f).coerceAtMost(2.0f)
                            controller.applyTempo()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        color = if (controller.tempoEnabled) Color.White else Color(0xFF45464A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold.appWeight
                    )
                }
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0xFF111214))
                        .clickable(enabled = controller.tempoEnabled) {
                            controller.tempoMultiplier = (((controller.tempoMultiplier - 0.05f) * 100f).roundToInt() / 100f).coerceAtLeast(0.5f)
                            controller.applyTempo()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "-",
                        color = if (controller.tempoEnabled) Color.White else Color(0xFF45464A),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold.appWeight
                    )
                }
            }
        }

        // Row 3: Mono toggle button (top-left), Reset button (top-right), Large Glowing Green Volume Knob (center)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(225.dp)
                .padding(bottom = 4.dp)
        ) {
            val monoBg by animateColorAsState(if (controller.monoEnabled) Color(0xFF28292D) else Color.Transparent, label = "monoBg")
            val monoText by animateColorAsState(if (controller.monoEnabled) Color.White else Color(0xFFB5B7BC), label = "monoText")

            // Mono toggle pill
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(68.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(monoBg)
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable {
                        controller.monoEnabled = !controller.monoEnabled
                        controller.applyVolumeAndBalance()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Mono",
                    color = monoText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Reset button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(68.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable { controller.resetVolumeTab() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Large Volume Knob with Neon Green Arc (`07_eq_volume_tab.png`)
            Column(
                modifier = Modifier.align(Alignment.BottomCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PowerampRotaryKnob(
                    value = controller.volumePercent / 100f,
                    onValueChange = { norm ->
                        controller.volumePercent = (norm * 100f).roundToInt().coerceIn(0, 100)
                        controller.applyVolumeAndBalance()
                    },
                    size = 160.dp,
                    showGreenArc = true
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Volume",
                    color = PowerampTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
                Text(
                    text = "${controller.volumePercent}%",
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }
        }
    }
}

/**
 * Sub-Tab 3: Reverb DSP Parameters (`08_eq_reverb_tab.png`).
 */
@Composable
private fun ReverbDspSubTab(controller: PowerampController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Row 1: Damp, Filter, Fade Knobs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ReverbKnobItem(
                label = "Damp",
                value = controller.reverbDamp,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbDamp = it }
            )
            ReverbKnobItem(
                label = "Filter",
                value = controller.reverbFilter,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbFilter = it }
            )
            ReverbKnobItem(
                label = "Fade",
                value = controller.reverbFade,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbFade = it }
            )
        }

        // Row 2: Pre-Delay, Pre-Delay Mix, Size Knobs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ReverbKnobItem(
                label = "Pre-Delay",
                value = controller.reverbPreDelay,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbPreDelay = it }
            )
            ReverbKnobItem(
                label = "Pre-Delay Mix",
                value = controller.reverbPreDelayMix,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbPreDelayMix = it }
            )
            ReverbKnobItem(
                label = "Size",
                value = controller.reverbSize,
                enabled = controller.reverbEnabled,
                onValueChange = { controller.reverbSize = it }
            )
        }

        // Row 3: Reverb toggle button | Preset button | Save button | Reset button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reverb Toggle
            Box(
                modifier = Modifier
                    .width(72.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (controller.reverbEnabled) Color(0xFF28292D) else Color.Transparent)
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable {
                        controller.reverbEnabled = !controller.reverbEnabled
                        controller.applyToneAndEffects()
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reverb",
                    color = if (controller.reverbEnabled) Color.White else Color(0xFFB5B7BC),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Preset Button
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable { controller.showReverbPresetSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = controller.selectedReverbPreset,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Save Button
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable { controller.saveCustomReverbPreset() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Save",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            // Reset Button
            Box(
                modifier = Modifier
                    .width(64.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.2.dp, PowerampBorderGray, RoundedCornerShape(18.dp))
                    .clickable { controller.resetReverbTab() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Reset",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }
        }

        // Row 4: Mix label + Large Mix Knob (`08_eq_reverb_tab.png`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.padding(top = 12.dp, end = 24.dp)
            ) {
                Text(
                    text = "Mix",
                    color = if (controller.reverbEnabled) PowerampTextPrimary else Color(0xFF55565A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
                Text(
                    text = "%.2f".format(controller.reverbMix),
                    color = if (controller.reverbEnabled) PowerampTextSecondary else Color(0xFF424347),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold.appWeight
                )
            }

            PowerampRotaryKnob(
                value = controller.reverbMix,
                onValueChange = { controller.reverbMix = it },
                size = 128.dp,
                enabled = controller.reverbEnabled,
                showGreenArc = controller.reverbEnabled && controller.reverbMix > 0f
            )
        }
    }
}

@Composable
private fun ReverbKnobItem(
    label: String,
    value: Float,
    enabled: Boolean,
    onValueChange: (Float) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PowerampRotaryKnob(
            value = value,
            onValueChange = onValueChange,
            size = 98.dp,
            enabled = enabled,
            showGreenArc = enabled && value > 0f
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (enabled) PowerampTextPrimary else Color(0xFF5C5E63),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold.appWeight
        )
        Text(
            text = "%.2f".format(value),
            color = if (enabled) PowerampTextSecondary else Color(0xFF424347),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold.appWeight
        )
    }
}

// ---- Public entry points: each screen applies the Look and Feel settings (font, status bar) ----
@Composable
fun PowerampPlayerScreen(controller: PowerampController, modifier: Modifier = Modifier) =
    LookAndFeelScope(controller) { PowerampPlayerScreenContent(controller, modifier) }

@Composable
fun PowerampEqualizerScreen(controller: PowerampController, modifier: Modifier = Modifier) =
    LookAndFeelScope(controller) { PowerampEqualizerScreenContent(controller, modifier) }
