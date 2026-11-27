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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.ui.unit.Dp
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
 * Main player, laid out like Poweramp's: edge-to-edge cover, left-aligned title / artist, like row,
 * pill buttons (Visualization, Sleep timer | Repeat, Shuffle), transport over a scrolling waveform
 * seekbar, times, format pill and the dock.
 *
 * Visualization: Fade draws the preset over the cover and dims the controls; Full Screen hides the
 * whole player behind the preset while music plays. Any touch brings the controls (and the preset
 * bar) back for a few seconds; that first touch only wakes them.
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
    val seekProgress = if (controller.durationMs <= 0L) 0f else controller.progressFraction.coerceIn(0f, 1f)

    val visOn = controller.visMode != VisMode.DISABLED
    val playing = controller.isPlaying
    val awake = controller.visControlsAwake
    // Controls are "asleep" while a visualization plays and nobody touched the screen lately
    val menuOpen = controller.showVisModeMenu || controller.showVisPresetList || controller.showRepeatMenu || controller.showShuffleMenu
    val asleep = visOn && playing && !awake && !menuOpen
    val controlsAlpha by animateFloatAsState(
        targetValue = when {
            !asleep -> 1f
            controller.visMode == VisMode.FULL_SCREEN -> 0f
            else -> controller.visFadedControlsOpacity
        },
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "controlsFadeAlpha"
    )
    val fullScreenVis = controller.visMode == VisMode.FULL_SCREEN && playing
    VisPresetAutoChange(controller)

    Box(modifier = modifier.fillMaxSize()) {
        PlayerBackground(controller = controller, track = track, modifier = Modifier.matchParentSize())
        if (fullScreenVis) {
            FullScreenVisualization(controller = controller, modifier = Modifier.matchParentSize())
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Full Screen fades everything; Fade keeps the cover (with the visualization) bright
                .alpha(if (controller.visMode == VisMode.FULL_SCREEN) controlsAlpha else 1f)
                // Watch touches without consuming them: keeps controls awake while a visualization runs
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        if (controller.visMode != VisMode.DISABLED) controller.wakeVisControls()
                    }
                }
        ) {
            // 1. COVER: edge to edge, swipe sideways to change track, long-press to switch visualization
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                BerryAlbumArtCarousel(
                    controller = controller,
                    track = track,
                    cardSize = maxWidth,
                    cardSpan = maxWidth,
                    cardHeight = maxHeight,
                    cardCorner = 0.dp,
                    verticalPadding = 0.dp,
                    modifier = Modifier.fillMaxSize()
                )
                // Soft fade into the page under the cover so the title reads well
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(72.dp)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, PowerampBg.copy(alpha = 0.85f))))
                )
            }

            Column(modifier = Modifier.fillMaxWidth().alpha(if (controller.visMode == VisMode.FADE_CONTROLS) controlsAlpha else 1f)) {
            // 2. TITLE / ARTIST (left aligned)
            AnimatedContent(
                targetState = track,
                transitionSpec = {
                    val dir = if (controller.trackTransitionDirection >= 0) 1 else -1
                    (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { dir * it / 4 } + fadeIn(tween(200)))
                        .togetherWith(slideOutHorizontally(tween(200)) { -dir * it / 4 } + fadeOut(tween(150)))
                },
                label = "trackMetadataTransition",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp)
            ) { t ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = t?.title ?: "No music",
                        color = PowerampTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.visChip(visOn)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = t?.artist ?: "",
                        color = PowerampTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.visChip(visOn)
                    )
                }
            }

            // 3. LIKE / DISLIKE  ......  A-B  menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rating = track?.rating ?: 0
                IconButton(onClick = { controller.toggleLikeCurrentTrack() }, enabled = track != null) {
                    Icon(
                        imageVector = if (rating == 1) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                        contentDescription = "Like",
                        tint = PowerampTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(onClick = { controller.toggleUnlikeCurrentTrack() }, enabled = track != null) {
                    Icon(
                        imageVector = if (rating == -1) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                        contentDescription = "Unlike",
                        tint = PowerampTextPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                // A-B repeat chip, shown only while set (long-press the elapsed time to set points)
                if (controller.abStartMs != null) {
                    Text(
                        text = if (controller.abEndMs != null) "A-B" else "A-",
                        color = PowerampTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold.appWeight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PlayerPillOn)
                            .clickable { controller.cycleAbRepeat() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
                IconButton(onClick = {
                    controller.actionTargetTrack = track
                    controller.showTrackActionDialog = true
                }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu", tint = PowerampTextPrimary, modifier = Modifier.size(26.dp))
                }
            }

            // 4. PILLS: Visualization, Sleep timer  ......  Repeat, Shuffle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerPillButton(
                    contentDescription = "Visualization",
                    active = visOn,
                    onClick = { controller.cycleVisMode() },
                    onLongClick = { controller.showVisModeMenu = true }
                ) { tint ->
                    VisModeIcon(controller.visMode, tint, Modifier.size(width = 26.dp, height = 26.dp))
                }
                PlayerPillButton(
                    contentDescription = "Sleep Timer",
                    active = controller.sleepTimerRemainingSeconds > 0,
                    onClick = { controller.showSleepTimerDialog = true },
                    onLongClick = { if (controller.sleepTimerRemainingSeconds > 0) controller.setSleepTimer(0) }
                ) { tint ->
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = tint, modifier = Modifier.size(30.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                PlayerPillButton(
                    contentDescription = "Repeat",
                    active = controller.repeatMode != RepeatMode.OFF,
                    onClick = { controller.cycleRepeatMode() },
                    onLongClick = { controller.showRepeatMenu = true }
                ) { tint ->
                    Icon(
                        imageVector = when (controller.repeatMode) {
                            RepeatMode.REPEAT_SONG, RepeatMode.SINGLE_SONG -> Icons.Filled.RepeatOne
                            else -> Icons.Filled.Repeat
                        },
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(28.dp)
                    )
                }
                PlayerPillButton(
                    contentDescription = "Shuffle",
                    active = controller.shuffleMode != ShuffleMode.OFF,
                    onClick = { controller.cycleShuffleMode() },
                    onLongClick = { controller.showShuffleMenu = true }
                ) { tint ->
                    Icon(Icons.Filled.Shuffle, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
                }
            }

            // Live seeking indicator while holding Previous / Next
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(22.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = controller.isSeekingFwd || controller.isSeekingRwd,
                    enter = fadeIn(tween(120)),
                    exit = fadeOut(tween(120))
                ) {
                    val deltaSec = kotlin.math.abs(controller.seekingAccumulatedDeltaMs / 1000L)
                    Text(
                        text = if (controller.isSeekingFwd) "+${deltaSec}s" else "-${deltaSec}s",
                        color = PowerampTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold.appWeight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(PlayerPillOn)
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
            }

            // 5. TRANSPORT over the scrolling waveform seekbar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                ScrollingWaveSeek(
                    controller = controller,
                    bars = track?.waveformBars ?: emptyList(),
                    progress = seekProgress,
                    modifier = Modifier.fillMaxSize()
                )
                // Buttons sit on discs of the page colour so the waveform passes behind them
                val disc = PowerampBg
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { controller.prevCategory() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Filled.KeyboardDoubleArrowLeft,
                            contentDescription = "Prev. category",
                            tint = PowerampTextPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    HoldToSeekTransportButton(
                        icon = Icons.Filled.FastRewind,
                        holdIcon = Icons.Filled.FastRewind,
                        contentDescription = "Previous track",
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
                        },
                        buttonSize = 72.dp,
                        iconSize = 44.dp,
                        background = disc
                    )
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(disc)
                            .clickable { controller.togglePlayPause() },
                        contentAlignment = Alignment.Center
                    ) {
                        AnimatedContent(
                            targetState = playing,
                            transitionSpec = {
                                if (controller.playPauseMorphAnimation) {
                                    (scaleIn(initialScale = 0.72f, animationSpec = tween(160)) + fadeIn(tween(140)))
                                        .togetherWith(scaleOut(targetScale = 0.72f, animationSpec = tween(140)) + fadeOut(tween(120)))
                                } else {
                                    EnterTransition.None togetherWith ExitTransition.None
                                }
                            },
                            label = "playPauseMorph"
                        ) { isPlayingNow ->
                            Icon(
                                imageVector = if (isPlayingNow) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlayingNow) "Pause" else "Play",
                                tint = PowerampTextPrimary,
                                modifier = Modifier.size(76.dp)
                            )
                        }
                    }
                    HoldToSeekTransportButton(
                        icon = Icons.Filled.FastForward,
                        holdIcon = Icons.Filled.FastForward,
                        contentDescription = "Next track",
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
                        },
                        buttonSize = 72.dp,
                        iconSize = 44.dp,
                        background = disc
                    )
                    IconButton(onClick = { controller.nextCategory() }, modifier = Modifier.size(52.dp)) {
                        Icon(
                            Icons.Filled.KeyboardDoubleArrowRight,
                            contentDescription = "Next category",
                            tint = PowerampTextPrimary.copy(alpha = 0.35f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // 6. TIMES (long-press the elapsed time for A-B repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = elapsedStr,
                    color = PowerampTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold.appWeight,
                    modifier = Modifier
                        .visChip(visOn)
                        .pointerInput(Unit) { detectTapGestures(onLongPress = { controller.cycleAbRepeat() }) }
                )
                Text(
                    text = durationStr,
                    color = PowerampTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold.appWeight,
                    modifier = Modifier.visChip(visOn)
                )
            }

            // 7. FORMAT PILL (tap = Info / Tags)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                contentAlignment = Alignment.Center
            ) {
                if (controller.showMetaLine && track != null) {
                    Text(
                        text = if (isLive) "LIVE STREAM" else track.bottomBadgeInfo,
                        color = PowerampTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold.appWeight,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(PlayerPillIdle)
                            .clickable {
                                controller.actionTargetTrack = track
                                controller.showInfoTagsSheet = true
                            }
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    )
                }
            }

            // 8. Dock
            PowerampBottomDock(
                controller = controller,
                showMiniPlayer = false
            )
            }
        }

        // Preset bar at the top while a visualization runs and the controls are showing
        androidx.compose.animation.AnimatedVisibility(
            visible = visOn && !asleep,
            enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 2 },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            VisPresetBar(controller = controller)
        }

        // While the controls sleep, the first touch only wakes them (no accidental button presses)
        if (asleep) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { controller.wakeVisControls() },
                            onLongPress = { controller.wakeVisControls() }
                        )
                    }
            )
        }

        if (controller.showVisPresetList) VisPresetListDropdown(controller)
        if (controller.showVisModeMenu) VisModeMenu(controller)
        if (controller.showRepeatMenu) RepeatModeMenu(controller)
        if (controller.showShuffleMenu) ShuffleModeMenu(controller)
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
    onSeekEnd: () -> Unit,
    buttonSize: Dp = 54.dp,
    iconSize: Dp = 34.dp,
    background: Color = Color.Transparent
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
            .size(buttonSize)
            .clip(CircleShape)
            .background(if (isSeeking) PowerampTextPrimary.copy(alpha = 0.16f) else background)
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
            tint = PowerampTextPrimary,
            modifier = Modifier.size(iconSize)
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
                append(if (parametric) " PEQ ${controller.parametricBands.count { it.enabled }}" else " EQ ${controller.bandFrequenciesHz.size}")
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
                    .background(if (controller.limiterActive) Color(0xFF4A2500) else Color(0xFF151618))
                    .border(
                        width = if (controller.limiterActive) 1.5.dp else 0.dp,
                        color = if (controller.limiterActive) Color(0xFFFF9800) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = if (controller.limiterActive) "$badge · LIMITING" else badge,
                    color = if (controller.limiterActive) Color(0xFFFFB74D) else Color(0xFFD2D4D8),
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
                    text = if (controller.limiterActive) "Limit ●" else "Limit",
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
