package com.musicamp.poweramp

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
fun PowerampPlayerScreen(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    val track = controller.currentTrack
    val elapsedSec = (controller.currentPositionMs / 1000L).coerceAtLeast(0L)
    val elapsedStr = "%d:%02d".format(elapsedSec / 60, elapsedSec % 60)
    val durationSec = (controller.durationMs / 1000L).coerceAtLeast(0L)
    val durationStr = "%d:%02d".format(durationSec / 60, durationSec % 60)
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        // 1. Large Album Art, Top Visualization Preset Bar & Rating / 3-dot Menu Overlay Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(Unit) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDx = 0f },
                        onDragEnd = {
                            if (totalDx > 110f) {
                                controller.previousTrack()
                            } else if (totalDx < -110f) {
                                controller.nextTrack()
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDx += dragAmount
                        }
                    )
                }
        ) {
            // Smooth horizontal track slide + scale animation when switching songs
            AnimatedContent(
                targetState = track?.id ?: 0L,
                transitionSpec = {
                    val dir = controller.trackTransitionDirection
                    (slideInHorizontally(
                        animationSpec = tween(320, easing = FastOutSlowInEasing),
                        initialOffsetX = { fullWidth -> dir * (fullWidth / 2) }
                    ) + fadeIn(tween(260)) + scaleIn(initialScale = 0.92f, animationSpec = tween(320)))
                        .togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(280, easing = FastOutSlowInEasing),
                                targetOffsetX = { fullWidth -> -dir * (fullWidth / 2) }
                            ) + fadeOut(tween(220)) + scaleOut(targetScale = 0.92f, animationSpec = tween(280))
                        )
                },
                label = "albumArtSlideTransition"
            ) { _ ->
                PowerampMountainArtwork(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    visMode = controller.visMode,
                    visPresetId = controller.currentVisPreset.id,
                    visPhase = controller.visPhase,
                    isPlaying = controller.isPlaying,
                    spectrumBars = controller.liveSpectrumAmplitudes
                )
            }

            // Top Floating Visualization Preset Selector Bar (`02_playing_t1.png`: "spectrum-centered Built-in")
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.visMode != VisMode.DISABLED && controller.showVisPresetBar,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(tween(220)),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(tween(180)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xEE1A1B1E))
                        .pointerInput(Unit) {
                            var dx = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dx = 0f },
                                onDragEnd = {
                                    if (dx > 60f) controller.prevVisPreset()
                                    else if (dx < -60f) controller.nextVisPreset()
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    dx += dragAmount
                                }
                            )
                        }
                        .clickable { controller.nextVisPreset() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Preset",
                        tint = Color(0xFFB5B7BC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = controller.currentVisPreset.name,
                            color = Color(0xFFD0D2D6),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = controller.currentVisPreset.category,
                            color = Color(0xFFA0A2A7),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    // Preset mode button (clock + arrow icon in `02_playing_t1.png`)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { controller.cycleVisPresetMode() }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Preset Mode",
                            tint = Color(0xFFB5B7BC),
                            modifier = Modifier.size(15.dp)
                        )
                        Icon(
                            imageVector = Icons.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFFB5B7BC),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Preset Bar",
                        tint = Color(0xFFB5B7BC),
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { controller.showVisPresetBar = false }
                    )
                }
            }

            // Bottom row of Album Art box: Like & Unlike pill buttons on left, 3-dot circle on right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .alpha(controlsAlpha)
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isLiked = track?.rating == 1
                    val isUnliked = track?.rating == -1

                    // Like pill button
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (isLiked) Color(0xFF2C2E33) else Color(0xFF151618))
                            .clickable { controller.toggleLikeCurrentTrack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbUp,
                            contentDescription = "Like",
                            tint = if (isLiked) PowerampNeonGreen else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Unlike pill button
                    Box(
                        modifier = Modifier
                            .size(width = 52.dp, height = 38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (isUnliked) Color(0xFF2C2E33) else Color(0xFF151618))
                            .clickable { controller.toggleUnlikeCurrentTrack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ThumbDown,
                            contentDescription = "Unlike",
                            tint = if (isUnliked) Color(0xFFFF5252) else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // 3-dot Menu circular button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF151618))
                        .clickable {
                            controller.actionTargetTrack = track
                            controller.showTrackActionDialog = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 2. Track Title Pill & Artist Pill (`01_current.png`) with animated track transition
        AnimatedContent(
            targetState = track,
            transitionSpec = {
                val dir = controller.trackTransitionDirection
                (slideInHorizontally(tween(280)) { dir * it / 3 } + fadeIn(tween(240)))
                    .togetherWith(slideOutHorizontally(tween(220)) { -dir * it / 3 } + fadeOut(tween(180)))
            },
            label = "trackTitleArtistSlide"
        ) { currentTrackItem ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .alpha(controlsAlpha)
                    .padding(horizontal = 14.dp, vertical = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF121315))
                        .clickable {
                            controller.currentMainTab = MainNavTab.LIBRARY
                            controller.activeLibraryCategory = LibraryCategory.ALL_SONGS
                        }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = currentTrackItem?.title ?: "luminewave-this-is-home-637005.mp3",
                        color = PowerampTextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121315))
                        .clickable {
                            controller.currentMainTab = MainNavTab.LIBRARY
                            controller.activeLibraryCategory = LibraryCategory.ARTISTS
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = currentTrackItem?.artist ?: "Unknown artist",
                        color = Color(0xFFE4E6EA),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 3. Sub-AA Buttons Row: Visualization, Sleep Timer on Left | Repeat, Shuffle on Right (`01_current.png`)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left group: Visualization + Sleep Timer
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Visualization pill button
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF151618))
                        .clickable { controller.cycleVisMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.GraphicEq,
                        contentDescription = "Visualization",
                        tint = if (controller.visMode != VisMode.DISABLED) Color.White else Color(0xFF8C8E93),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Sleep Timer pill button
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF151618))
                        .clickable { controller.showSleepTimerDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Schedule,
                            contentDescription = "Sleep Timer",
                            tint = if (controller.sleepTimerMinutes > 0) PowerampNeonGreen else Color(0xFF8C8E93),
                            modifier = Modifier.size(21.dp)
                        )
                        if (controller.sleepTimerMinutes > 0) {
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "${(controller.sleepTimerRemainingSeconds / 60).coerceAtLeast(1)}m",
                                color = PowerampNeonGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Right group: Repeat + Shuffle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Repeat pill button
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF151618))
                        .clickable { controller.cycleRepeatMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (controller.repeatMode == RepeatMode.REPEAT_SONG || controller.repeatMode == RepeatMode.SINGLE_SONG) {
                            Icons.Filled.RepeatOne
                        } else {
                            Icons.Filled.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (controller.repeatMode != RepeatMode.OFF) Color(0xFFB0B2B7) else Color(0xFF55565A),
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Shuffle pill button
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 38.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .background(Color(0xFF151618))
                        .clickable { controller.cycleShuffleMode() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (controller.shuffleMode != ShuffleMode.OFF) Color.White else Color(0xFF8C8E93),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 4. Waveseek Scroller & Transport Controls Section (`01_current.png`, `02_playing_t1.png`, `04_playing_vis1.png`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(215.dp)
        ) {
            // Dynamic Scrolling Waveform Seekbar spanning behind transport controls
            PowerampWaveseekBar(
                bars = track?.waveformBars ?: emptyList(),
                progress = controller.progressFraction,
                isPlaying = controller.isPlaying,
                visPhase = controller.visPhase,
                onSeek = { controller.seekToFraction(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .align(Alignment.Center)
            )

            // Dynamic Floating Seeking Indicator Badge
            androidx.compose.animation.AnimatedVisibility(
                visible = controller.isSeekingFwd || controller.isSeekingRwd,
                enter = fadeIn(tween(140)) + scaleIn(initialScale = 0.85f),
                exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE1E2024))
                        .border(1.2.dp, PowerampNeonGreen, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    val deltaSec = (controller.seekingAccumulatedDeltaMs / 1000L).coerceAtLeast(0L)
                    Text(
                        text = if (controller.isSeekingFwd) "▸▸ FAST FORWARD  +${deltaSec}s" else "◂◂ FAST REWIND  -${deltaSec}s",
                        color = PowerampNeonGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
            }

            // 5 Transport Buttons overlaid horizontally: <<< (Prev Category), << (Prev Track), Play/Pause, >> (Next Track), >>> (Next Category)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(horizontal = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Category (<<<)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { controller.prevCategory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "◂◂◂",
                        color = Color(0xFF5C5E63),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    )
                }

                // Previous Track (<<) - Tap skips/restarts, Press-and-hold seeks backward smoothly
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (controller.isSeekingRwd) Color(0x3300FF19) else Color.Transparent)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    var isHeld = false
                                    controller.seekingAccumulatedDeltaMs = 0L
                                    val job = coroutineScope.launch {
                                        delay(240)
                                        isHeld = true
                                        controller.isSeekingRwd = true
                                        var holdElapsed = 0L
                                        while (isActive) {
                                            val step = when {
                                                holdElapsed > 2500L -> 4000L
                                                holdElapsed > 1000L -> 2000L
                                                else -> 1000L
                                            }
                                            controller.seekingAccumulatedDeltaMs += step
                                            controller.seekRelative(-step)
                                            delay(80)
                                            holdElapsed += 80
                                        }
                                    }
                                    val released = tryAwaitRelease()
                                    job.cancel()
                                    controller.isSeekingRwd = false
                                    if (!isHeld) {
                                        controller.previousTrackOrRestart()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastRewind,
                        contentDescription = "Previous track",
                        tint = if (controller.isSeekingRwd) PowerampNeonGreen else Color.White,
                        modifier = Modifier.size(if (controller.isSeekingRwd) 46.dp else 42.dp)
                    )
                }

                // Large Play / Pause Button with Poweramp's signature spring morph animation:
                // Note: We keep the center area transparent to Waveseek drags except direct clicks on the button!
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = controller.isPlaying,
                        transitionSpec = {
                            (scaleIn(initialScale = 0.45f, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                fadeIn(tween(180)))
                                .togetherWith(
                                    scaleOut(targetScale = 0.45f, animationSpec = tween(180)) +
                                        fadeOut(tween(150))
                                )
                        },
                        label = "playPauseMorph"
                    ) { playing ->
                        if (playing) {
                            // Exact Poweramp double-pillar rounded Pause bars (`04_playing_vis1.png`)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                )
                                Box(
                                    modifier = Modifier
                                        .width(12.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(68.dp)
                            )
                        }
                    }
                }

                // Next Track (>>) - Tap skips track, Press-and-hold seeks forward smoothly
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(if (controller.isSeekingFwd) Color(0x3300FF19) else Color.Transparent)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    var isHeld = false
                                    controller.seekingAccumulatedDeltaMs = 0L
                                    val job = coroutineScope.launch {
                                        delay(240)
                                        isHeld = true
                                        controller.isSeekingFwd = true
                                        var holdElapsed = 0L
                                        while (isActive) {
                                            val step = when {
                                                holdElapsed > 2500L -> 4000L
                                                holdElapsed > 1000L -> 2000L
                                                else -> 1000L
                                            }
                                            controller.seekingAccumulatedDeltaMs += step
                                            controller.seekRelative(+step)
                                            delay(80)
                                            holdElapsed += 80
                                        }
                                    }
                                    val released = tryAwaitRelease()
                                    job.cancel()
                                    controller.isSeekingFwd = false
                                    if (!isHeld) {
                                        controller.nextTrack()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FastForward,
                        contentDescription = "Next track",
                        tint = if (controller.isSeekingFwd) PowerampNeonGreen else Color.White,
                        modifier = Modifier.size(if (controller.isSeekingFwd) 46.dp else 42.dp)
                    )
                }

                // Next Category (>>>)
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { controller.nextCategory() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "▸▸▸",
                        color = Color(0xFF5C5E63),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-2).sp
                    )
                }
            }

            // Elapsed time (bottom-left) & Track duration (bottom-right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = elapsedStr,
                    color = PowerampTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = durationStr,
                    color = PowerampTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        // 5. Audio Meta Info Pill (`44.1 KHZ 256 KBPS MP3`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF131416))
                    .clickable {
                        controller.actionTargetTrack = track
                        controller.showInfoTagsSheet = true
                    }
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = track?.bottomBadgeInfo ?: "44.1 KHZ 256 KBPS MP3",
                    color = Color(0xFFD2D4D8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.4.sp
                )
            }
        }

        // 6. Bottom Navigation Bar (4 buttons without duplicate mini-player)
        PowerampBottomDock(
            controller = controller,
            showMiniPlayer = false
        )
    }
}

/**
 * 100% Pixel-Perfect Poweramp Equalizer / Volume / Reverb Screen (`03_eq_tab1.png`, `07_eq_volume_tab.png`, `08_eq_reverb_tab.png`).
 */
@Composable
fun PowerampEqualizerScreen(
    controller: PowerampController,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PowerampBg)
    ) {
        // Top Action Mode Bar: 3 Sub-Tabs (Equalizer Sliders Icon | Volume Knob Icon | Reverb Speaker Icon)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF18191B)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sub-tab 1: Equalizer
            val eqActive = controller.currentEqSubTab == EqSubTab.EQUALIZER
            val eqTint by animateColorAsState(if (eqActive) Color.White else Color(0xFF8E9095), label = "eqTabTint")
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
            val volTint by animateColorAsState(if (volActive) Color.White else Color(0xFF8E9095), label = "volTabTint")
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
                            .background(Color.Black)
                    )
                }
            }

            // Sub-tab 3: Reverb
            val revActive = controller.currentEqSubTab == EqSubTab.REVERB
            val revTint by animateColorAsState(if (revActive) Color.White else Color(0xFF8E9095), label = "revTabTint")
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp)
    ) {
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
            LazyRow(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(controller.bandFrequencies) { index, freqLabel ->
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

        // 3. Info Badge (`NO DVC EQ 10 TON LMT`)
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
                    text = "NO DVC EQ 10 TON LMT",
                    color = Color(0xFFD2D4D8),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
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
                            fontWeight = FontWeight.Bold,
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
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${controller.bassPercent}%",
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${controller.treblePercent}%",
                                color = PowerampTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
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
            fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "%.2f".format(controller.balanceValue),
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${controller.stereoExpandPercent}%",
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                        fontWeight = FontWeight.Bold
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
                        fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "${controller.volumePercent}%",
                    color = PowerampTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold,
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.Bold
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
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "%.2f".format(controller.reverbMix),
                    color = if (controller.reverbEnabled) PowerampTextSecondary else Color(0xFF424347),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
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
            fontWeight = FontWeight.ExtraBold
        )
        Text(
            text = "%.2f".format(value),
            color = if (enabled) PowerampTextSecondary else Color(0xFF424347),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
