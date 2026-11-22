package com.musicamp.poweramp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Theme modes shown in Settings > Look and Feel. "Poweramp" is the default (black, charcoal, neon green). */
val THEME_MODES = listOf("Poweramp", "Light", "Dark", "Black (OLED)", "Follow system")

const val POWERAMP_GREEN = "Poweramp Green"

/** Accent colours: name -> (dark shade, main shade, light shade). */
val ACCENT_COLORS: LinkedHashMap<String, Triple<Color, Color, Color>> = linkedMapOf(
    POWERAMP_GREEN to Triple(Color(0xFF00B812), Color(0xFF00FF19), Color(0xFF6BFF7B)),
    "Berry" to Triple(Color(0xFF7A0B42), Color(0xFF9C1554), Color(0xFFB81B6C)),
    "Blue" to Triple(Color(0xFF0D3B8E), Color(0xFF1565C0), Color(0xFF3B86E8)),
    "Green" to Triple(Color(0xFF0B5D2A), Color(0xFF1B8A3F), Color(0xFF34B85E)),
    "Orange" to Triple(Color(0xFFA33A00), Color(0xFFD9540B), Color(0xFFF58030)),
    "Teal" to Triple(Color(0xFF00575A), Color(0xFF00838F), Color(0xFF1CB3C0)),
    "Purple" to Triple(Color(0xFF4A1A8C), Color(0xFF6A2BC0), Color(0xFF8D55E6)),
    "Red" to Triple(Color(0xFF8E1010), Color(0xFFC62828), Color(0xFFE85050))
)

/** Global, observable theme state. Compose recomposes every screen that reads a colour below when it changes. */
object AppTheme {
    var mode by mutableStateOf("Poweramp")
    var accent by mutableStateOf(POWERAMP_GREEN)
    var systemDark by mutableStateOf(false)

    val isDark: Boolean
        get() = when (mode) {
            "Poweramp", "Dark", "Black (OLED)" -> true
            "Follow system" -> systemDark
            else -> false
        }

    val isPoweramp: Boolean get() = mode == "Poweramp"

    /** Pure black backgrounds (saves power on OLED panels). */
    val isOled: Boolean get() = mode == "Black (OLED)"

    internal fun accentShades(): Triple<Color, Color, Color> = ACCENT_COLORS[accent] ?: ACCENT_COLORS.getValue(POWERAMP_GREEN)
}

// ---- Accent colours (names kept from the original design so every screen picks them up) ----
val ThemeBerryDark: Color get() = AppTheme.accentShades().first
val ThemeBerryPrimary: Color
    get() = if (AppTheme.isDark && AppTheme.accent != POWERAMP_GREEN) AppTheme.accentShades().third else AppTheme.accentShades().second
val ThemeBerryLight: Color get() = AppTheme.accentShades().third
val ThemeBerryAccent: Color get() = ThemeBerryPrimary
val ThemeBerryGlow: Color get() = ThemeBerryPrimary.copy(alpha = 0.2f)

/** Header behind the top of Home / Player: charcoal in the Poweramp theme, accent gradient elsewhere. */
val ThemeBerryHeaderGradient: Brush
    get() = if (AppTheme.isPoweramp) {
        Brush.verticalGradient(listOf(Color(0xFF1C1D20), Color(0xFF141416), Color(0xFF0C0C0D)))
    } else {
        AppTheme.accentShades().let { (d, p, l) -> Brush.verticalGradient(listOf(d, p, l)) }
    }

/** Colours for the featured card on Home. */
val ThemeCardColors: List<Color>
    get() = if (AppTheme.isPoweramp) listOf(Color(0xFF202125), Color(0xFF17181B), Color(0xFF101112))
    else listOf(ThemeBerryDark, ThemeBerryPrimary.copy(alpha = 0.85f), ThemeBerryDark.copy(alpha = 0.9f))

/** Tile colours for artist / genre tiles (white text on top). */
val ThemeTileShades: List<Color>
    get() = if (AppTheme.isPoweramp) listOf(Color(0xFF2A2B30), Color(0xFF232428), Color(0xFF33343A))
    else listOf(ThemeBerryDark, ThemeBerryPrimary, ThemeBerryLight)

/** Text / icon colour that stays readable on top of the accent colour (black on bright accents like neon green). */
val PowerampOnAccent: Color get() = if (ThemeBerryPrimary.luminance() > 0.45f) Color.Black else Color.White

val PowerampNeonGreen: Color get() = ThemeBerryPrimary
val PowerampNeonGlow: Color get() = ThemeBerryPrimary.copy(alpha = if (AppTheme.isPoweramp) 0.4f else 0.2f)
val ThemeWavePlayed: Color get() = ThemeBerryPrimary
val ThemeWaveUnplayed: Color get() = if (AppTheme.isDark) Color(0xFF3A3C42) else Color(0xFFC5CAD4)

// ---- Surfaces and text ----
val PowerampBg: Color
    get() = when {
        AppTheme.isPoweramp || AppTheme.isOled -> Color(0xFF000000)
        AppTheme.isDark -> Color(0xFF131416)
        else -> Color(0xFFFFFFFF)
    }
val PowerampSurface: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFF141416)
        AppTheme.isOled -> Color(0xFF0E0E10)
        AppTheme.isDark -> Color(0xFF1C1D21)
        else -> Color(0xFFF7F8FA)
    }
val PowerampPillBg: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFF1A1B1E)
        AppTheme.isOled -> Color(0xFF17181B)
        AppTheme.isDark -> Color(0xFF26272C)
        else -> Color(0xFFF0F2F6)
    }
val PowerampDialogBg: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFF212226)
        AppTheme.isOled -> Color(0xFF111113)
        AppTheme.isDark -> Color(0xFF202125)
        else -> Color(0xFFFFFFFF)
    }
val PowerampBorderGray: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFF424347)
        AppTheme.isDark -> Color(0xFF34363C)
        else -> Color(0xFFE2E4E9)
    }
val PowerampTextPrimary: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFFFFFFFF)
        AppTheme.isDark -> Color(0xFFF2F3F5)
        else -> Color(0xFF16171A)
    }
val PowerampTextSecondary: Color
    get() = when {
        AppTheme.isPoweramp -> Color(0xFF9EA0A5)
        AppTheme.isDark -> Color(0xFF9DA1AB)
        else -> Color(0xFF767B86)
    }

/** Strong icon colour for transport controls: dark accent on light themes, near-white on dark themes. */
val PowerampIconStrong: Color get() = if (AppTheme.isDark) PowerampTextPrimary else ThemeBerryDark
