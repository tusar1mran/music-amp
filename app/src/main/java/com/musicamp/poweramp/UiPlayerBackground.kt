package com.musicamp.poweramp

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Values of [PowerampController.backgroundType]. */
const val PLAYER_BG_BLACK = "Pure Black (OLED)"
const val PLAYER_BG_ART_BLUR = "Album Art Blur"
const val PLAYER_BG_GLOW = "Blurred Mountain Glow"

/**
 * Player screen background from Settings > Background:
 * - Pure Black: the theme background (pure black on the dark themes).
 * - Album Art Blur: the current cover, blurred, with the saturation and dim settings applied.
 *   Uses a real blur on Android 12+; below that, a heavily down-scaled cover drawn with bilinear filtering.
 *   Falls back to the glow when the track has no artwork.
 * - Blurred Mountain Glow: soft radial glows in the accent colour.
 */
@Composable
fun PlayerBackground(controller: PowerampController, track: AudioTrack?, modifier: Modifier = Modifier) {
    val type = controller.backgroundType
    val base = if (AppTheme.isDark) Color.Black else PowerampBg
    if (type != PLAYER_BG_ART_BLUR && type != PLAYER_BG_GLOW) {
        Box(modifier = modifier.background(base))
        return
    }

    val blur = controller.backgroundBlurRadius.coerceIn(0f, 1f)
    val saturation = controller.backgroundSaturation.coerceIn(0f, 2f)
    val dim = controller.backgroundDimOpacity.coerceIn(0f, 0.8f)
    val art = if (type == PLAYER_BG_ART_BLUR) rememberTrackArt(controller, track) else null

    Box(modifier = modifier.background(base)) {
        if (art != null) {
            BlurredArt(art = art, blur = blur, saturation = saturation)
        } else {
            GlowBackground(blur = blur, saturation = saturation)
        }
        // Dim towards the theme background so the controls stay readable; stronger at the bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(base.copy(alpha = dim * 0.7f), base.copy(alpha = dim), base.copy(alpha = (dim + 0.25f).coerceAtMost(0.95f)))
                    )
                )
        )
    }
}

@Composable
private fun BlurredArt(art: ImageBitmap, blur: Float, saturation: Float) {
    val filter = remember(saturation) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
                .blur((8f + 72f * blur).dp, BlurredEdgeTreatment.Rectangle)
        )
    } else {
        // No RenderEffect before Android 12: shrink the cover to a few pixels and let bilinear upscaling smear it
        val tiny = remember(art, blur) {
            val side = (48f - 40f * blur).roundToInt().coerceIn(6, 48)
            try {
                Bitmap.createScaledBitmap(art.asAndroidBitmap(), side, side, true).asImageBitmap()
            } catch (_: Exception) {
                art
            }
        }
        Image(
            bitmap = tiny,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            filterQuality = FilterQuality.High,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.1f
                    scaleY = 1.1f
                }
        )
    }
}

@Composable
private fun GlowBackground(blur: Float, saturation: Float) {
    // Saturation 0 = grey glow, 1 = accent, 2 = accent pushed further from grey
    val accent = ThemeBerryPrimary
    val grey = Color(0xFF808080)
    val tint = if (saturation <= 1f) lerp(grey, accent, saturation) else accent
    val glowAlpha = if (saturation > 1f) 0.55f + 0.2f * (saturation - 1f) else 0.55f
    val second = ThemeBerryDark
    Canvas(modifier = Modifier.fillMaxSize()) {
        val spread = 0.55f + 0.45f * blur // softer and wider with more blur
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = glowAlpha), Color.Transparent),
                center = Offset(size.width * 0.5f, size.height * 0.22f),
                radius = size.maxDimension * 0.55f * spread
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(second.copy(alpha = glowAlpha * 0.6f), Color.Transparent),
                center = Offset(size.width * 0.1f, size.height * 0.75f),
                radius = size.maxDimension * 0.5f * spread
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(tint.copy(alpha = glowAlpha * 0.4f), Color.Transparent),
                center = Offset(size.width * 0.95f, size.height * 0.55f),
                radius = size.maxDimension * 0.4f * spread
            )
        )
    }
}
