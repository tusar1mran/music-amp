package com.musicamp.poweramp

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Optional full-screen lockscreen player shown over the keyguard when playback is active
 * and [PowerampController.lockscreenPlayerEnabled] is enabled.
 */
class LockscreenActivity : ComponentActivity() {

    private var unlockRegistered = false
    private val unlockReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == android.content.Intent.ACTION_USER_PRESENT) finish()
        }
    }

    override fun onDestroy() {
        if (unlockRegistered) {
            try { unregisterReceiver(unlockReceiver) } catch (_: Exception) {}
            unlockRegistered = false
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val controller = (application as PowerampApp).controller
        if (!controller.lockscreenPlayerEnabled || !controller.isPlaying) {
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            // No setTurnScreenOn: the activity starts as the screen goes off and must not wake it again
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
            )
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Close as soon as the user unlocks the device the normal way
        try {
            registerReceiver(unlockReceiver, android.content.IntentFilter(android.content.Intent.ACTION_USER_PRESENT))
            unlockRegistered = true
        } catch (_: Exception) {
        }

        setContent {
            LookAndFeelScope(controller) {
                LockscreenPlayerContent(
                    controller = controller,
                    onDismiss = { finish() },
                    onUnlockAndOpen = {
                        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                        if (km != null && km.isKeyguardLocked) {
                            km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
                                override fun onDismissSucceeded() {
                                    startActivity(Intent(this@LockscreenActivity, MainActivity::class.java))
                                    finish()
                                }
                            })
                        } else {
                            startActivity(Intent(this, MainActivity::class.java))
                            finish()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LockscreenPlayerContent(
    controller: PowerampController,
    onDismiss: () -> Unit,
    onUnlockAndOpen: () -> Unit
) {
    val track = controller.currentTrack
    val stream = controller.currentStream
    var art by remember(track?.id) { mutableStateOf<Bitmap?>(track?.let { controller.getCachedArt(it.id) }) }
    LaunchedEffect(track?.id) {
        if (track != null && art == null) {
            art = controller.loadArt(track)
        }
    }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF141519), Color(0xFF0A0B0E), Color(0xFF000000))
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragAccum < -140f) onDismiss()
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                    onVerticalDrag = { _, dragAmount -> dragAccum += dragAmount }
                )
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "MusicAmp",
                color = Color(0x99FFFFFF),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold.appWeight,
                modifier = Modifier.clickable { onUnlockAndOpen() }
            )

            // Album Art
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF1F2026)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = art
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Album art",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color(0x66FFFFFF),
                        modifier = Modifier.size(72.dp)
                    )
                }
            }

            // Title & Artist
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stream?.title ?: track?.title ?: "Nothing playing",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stream?.genre ?: track?.artist.orEmpty(),
                    color = Color(0xB3FFFFFF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold.appWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Seekbar
            Column(modifier = Modifier.fillMaxWidth()) {
                val frac = if (controller.durationMs > 0L) {
                    (controller.currentPositionMs.toFloat() / controller.durationMs).coerceIn(0f, 1f)
                } else 0f
                // Seek once when the drag ends, not on every movement
                var dragFrac by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Float?>(null) }
                Slider(
                    value = dragFrac ?: frac,
                    onValueChange = { if (controller.durationMs > 0L) dragFrac = it },
                    onValueChangeFinished = {
                        dragFrac?.let { controller.seekToFraction(it) }
                        dragFrac = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = ThemeBerryPrimary,
                        activeTrackColor = ThemeBerryPrimary,
                        inactiveTrackColor = Color(0x40FFFFFF)
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatLockTime(controller.currentPositionMs), color = Color(0x99FFFFFF), fontSize = 12.sp)
                    Text(formatLockTime(controller.durationMs), color = Color(0x99FFFFFF), fontSize = 12.sp)
                }
            }

            // Transport buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { controller.previousTrack() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(ThemeBerryPrimary)
                        .clickable { controller.togglePlayPause() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (controller.isPlaying) "Pause" else "Play",
                        tint = PowerampOnAccent,
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(onClick = { controller.nextTrack() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
                }
            }

            // Swipe up hint
            Row(
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = Color(0x80FFFFFF), modifier = Modifier.size(18.dp))
                Text("Swipe up to dismiss", color = Color(0x80FFFFFF), fontSize = 12.sp)
            }
        }
    }
}

private fun formatLockTime(ms: Long): String {
    val sec = (ms / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(sec / 60L, sec % 60L)
}
