package com.musicamp.poweramp

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimRepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Animated launch splash screen for MusicAmp:
 * - Expanding ambient soundwave rings
 * - Bouncy glowing emblem with live dancing equalizer bars
 * - Staggered "MusicAmp" title & subtitle reveal
 * - Smooth scale + fade exit into the main app
 */
@Composable
fun MusicAmpSplashOverlay(
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(visible) {
        if (visible) {
            delay(1450L)
            onFinished()
        }
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(durationMillis = 360, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 1.07f, animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        MusicAmpSplashContent()
    }
}

@Composable
private fun MusicAmpSplashContent() {
    val accent = ThemeBerryPrimary
    val accentLight = ThemeBerryLight
    val accentDark = ThemeBerryDark
    val bg = PowerampBg
    val textPrimary = PowerampTextPrimary
    val textSecondary = PowerampTextSecondary

    // Choreographed entrance animations
    val logoScale = remember { Animatable(0.55f) }
    val logoAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleOffsetY = remember { Animatable(28f) }
    val subtitleAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            logoAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            logoScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.62f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        delay(220L)
        launch {
            titleAlpha.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
        }
        launch {
            titleOffsetY.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
        }
        delay(180L)
        subtitleAlpha.animateTo(1f, tween(360, easing = FastOutSlowInEasing))
    }

    // Continuous smooth 60/90/120Hz+ soundwave & equalizer bar phase
    val infinite = rememberInfiniteTransition(label = "splashLoop")
    val wavePhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "eqWavePhase"
    )
    val rippleProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = FastOutSlowInEasing),
            repeatMode = AnimRepeatMode.Restart
        ),
        label = "rippleProgress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        bg,
                        if (AppTheme.isDark) Color(0xFF0A0B0D) else bg,
                        bg
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glow & expanding soundwave ripple rings behind the center logo
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height * 0.44f)
            val maxGlowRadius = minOf(size.width, size.height) * 0.44f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.22f * logoAlpha.value),
                        accent.copy(alpha = 0.06f * logoAlpha.value),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxGlowRadius
                ),
                radius = maxGlowRadius,
                center = center
            )

            // Two staggered expanding soundwave rings
            for (ring in 0..1) {
                val p = (rippleProgress + ring * 0.5f) % 1f
                val r = 56.dp.toPx() + p * 110.dp.toPx()
                val ringAlpha = (1f - p).coerceIn(0f, 1f) * 0.42f * logoAlpha.value
                drawCircle(
                    color = accent.copy(alpha = ringAlpha),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Glowing circular emblem with live dancing 5-bar equalizer icon
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                        alpha = logoAlpha.value
                    }
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(accentLight, accent, accentDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(46.dp)) {
                        val barCount = 5
                        val gap = 4.dp.toPx()
                        val barW = (size.width - gap * (barCount - 1)) / barCount
                        val centerY = size.height / 2f
                        val baseWeights = floatArrayOf(0.48f, 0.82f, 1.0f, 0.78f, 0.52f)
                        val barColor = PowerampOnAccent

                        for (i in 0 until barCount) {
                            val osc = 0.55f + 0.45f * sin(wavePhase + i * 0.95f)
                            val barH = (size.height * baseWeights[i] * osc).coerceIn(8.dp.toPx(), size.height * 0.94f)
                            val x = i * (barW + gap)
                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(x, centerY - barH / 2f),
                                size = Size(barW, barH),
                                cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // App Name: "MusicAmp" with dual-tone highlight
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = textPrimary, fontWeight = FontWeight.ExtraBold)) {
                        append("Music")
                    }
                    withStyle(SpanStyle(color = accent, fontWeight = FontWeight.Black)) {
                        append("Amp")
                    }
                },
                fontSize = 36.sp,
                letterSpacing = 1.2.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = titleAlpha.value
                    translationY = titleOffsetY.value
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtle tagline
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.graphicsLayer { alpha = subtitleAlpha.value }
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Text(
                    text = "HI-RES AUDIO ENGINE",
                    color = textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.4.sp
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
    }
}
