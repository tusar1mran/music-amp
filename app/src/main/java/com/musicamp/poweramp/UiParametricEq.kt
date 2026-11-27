package com.musicamp.poweramp

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln
import kotlin.math.pow

// ---- Log-scale slider mappings ----
private const val FREQ_MIN = 20f
private const val FREQ_MAX = 20000f
private const val Q_MIN = 0.1f
private const val Q_MAX = 10f

private fun logToFraction(value: Float, min: Float, max: Float): Float =
    (ln(value.coerceIn(min, max) / min) / ln(max / min)).coerceIn(0f, 1f)

private fun fractionToLog(fraction: Float, min: Float, max: Float): Float =
    min * (max / min).pow(fraction.coerceIn(0f, 1f))

private fun formatHz(hz: Float): String =
    if (hz >= 1000f) "%.2f kHz".format(hz / 1000f).replace(".00 ", " ") else "%d Hz".format(hz.toInt())

private val FilterType.isPass: Boolean get() = this == FilterType.LOW_PASS || this == FilterType.HIGH_PASS

/**
 * Returns a function that opens the system file picker for an AutoEQ / Equalizer APO "ParametricEQ.txt"
 * and hands the chosen file (with its display name) to [PowerampController.importAutoEq].
 */
@Composable
fun rememberAutoEqImportLauncher(controller: PowerampController, onPicked: () -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked by rememberUpdatedState(onPicked)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val name = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                    } catch (_: Exception) {
                        null
                    }
                }
                controller.importAutoEq(uri, name)
                currentOnPicked()
            }
        }
    }
    return { launcher.launch(arrayOf("text/*")) }
}

/** Short warnings shown above the equalizer when its settings cannot be heard. */
@Composable
fun EqEngineNotes(controller: PowerampController, parametric: Boolean) {
    val notes = buildList {
        if (controller.bitPerfectActive) add("Bit-perfect: all DSP is bypassed")
        if (parametric && !controller.nativeEngineActive) {
            add(
                if (!controller.useNativeEngine) "Parametric EQ needs the MusicAmp engine (Settings > Audio)"
                else "Parametric EQ needs the MusicAmp engine; the current track is not playing through it"
            )
        }
    }
    notes.forEach { note ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(ThemeBerryPrimary.copy(alpha = 0.14f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = ThemeBerryPrimary, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(note, color = PowerampTextPrimary, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold.appWeight)
        }
    }
}

/** Graphic / Parametric segmented switch for the equalizer tab. */
@Composable
fun EqModeSwitch(parametric: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(PowerampPillBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(false to "Graphic", true to "Parametric").forEach { (value, label) ->
            val selected = value == parametric
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(if (selected) ThemeBerryPrimary else PowerampPillBg)
                    .clickable { if (!selected) onChange(value) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (selected) PowerampOnAccent else PowerampTextPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
            }
        }
    }
}

/**
 * Parametric equalizer editor: profile name, preamp, Add band / Reset / Import AutoEQ actions and one card
 * per band (type, frequency, gain, Q, enable, delete).
 */
@Composable
fun ParametricEqEditor(controller: PowerampController, modifier: Modifier = Modifier) {
    val importAutoEq = rememberAutoEqImportLauncher(controller)
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Column {
                Text(
                    text = controller.parametricProfileName.ifBlank { "Custom parametric EQ" },
                    color = PowerampTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${controller.parametricBands.size} bands" +
                        if (controller.parametricProfileName.isNotBlank()) " · imported from AutoEQ" else "",
                    color = PowerampTextSecondary,
                    fontSize = 11.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PeqActionPill("Add band", Icons.Filled.Add, filled = true) { controller.addParametricBand() }
                    PeqActionPill("Import AutoEQ", Icons.Filled.FileOpen) { importAutoEq() }
                    PeqActionPill("Reset", Icons.Filled.RestartAlt) { controller.resetParametricEq() }
                }
                Spacer(modifier = Modifier.height(4.dp))
                PeqSliderRow(
                    label = "Preamp",
                    value = "%+.1f dB".format(controller.preampDb),
                    fraction = (controller.preampDb + 15f) / 30f,
                    onFractionChange = {
                        controller.preampDb = (it * 30f - 15f).let { db -> (db * 10f).toInt() / 10f }
                        controller.applyEqualizerBands()
                        controller.applyVolumeAndBalance()
                    }
                )
            }
        }

        if (controller.parametricBands.isEmpty()) {
            item {
                Text(
                    text = "No bands yet. Add a band or import an AutoEQ ParametricEQ.txt file for your headphones.",
                    color = PowerampTextSecondary,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            }
        }

        itemsIndexed(controller.parametricBands) { index, band ->
            ParametricBandCard(
                index = index,
                band = band,
                onChange = { controller.updateParametricBand(index, it) },
                onDelete = { controller.removeParametricBand(index) }
            )
        }
    }
}

@Composable
private fun PeqActionPill(text: String, icon: ImageVector, filled: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (filled) ThemeBerryPrimary else PowerampPillBg)
            .border(1.dp, if (filled) ThemeBerryPrimary else PowerampBorderGray, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val tint = if (filled) PowerampOnAccent else PowerampTextPrimary
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text, color = tint, fontSize = 12.sp, fontWeight = FontWeight.Bold.appWeight)
    }
}

@Composable
private fun ParametricBandCard(
    index: Int,
    band: ParametricBand,
    onChange: (ParametricBand) -> Unit,
    onDelete: () -> Unit
) {
    val contentAlpha = if (band.enabled) 1f else 0.45f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PowerampSurface)
            .border(1.dp, PowerampBorderGray, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (band.enabled) ThemeBerryPrimary else PowerampPillBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    color = if (band.enabled) PowerampOnAccent else PowerampTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold.appWeight
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(FilterType.entries.toList()) { type ->
                    val selected = type == band.type
                    Box(
                        modifier = Modifier
                            .height(26.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (selected) ThemeBerryPrimary else PowerampPillBg)
                            .clickable { onChange(band.copy(type = type)) }
                            .padding(horizontal = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = type.label,
                            color = if (selected) PowerampOnAccent else PowerampTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = (if (selected) FontWeight.Bold else FontWeight.Medium).appWeight
                        )
                    }
                }
            }
            Switch(
                checked = band.enabled,
                onCheckedChange = { onChange(band.copy(enabled = it)) },
                colors = SwitchDefaults.colors(checkedTrackColor = ThemeBerryPrimary, checkedThumbColor = PowerampOnAccent),
                modifier = Modifier.scale(0.75f)
            )
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete band ${index + 1}",
                tint = PowerampTextSecondary,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() }
                    .padding(6.dp)
            )
        }
        Column {
            PeqSliderRow(
                label = "Freq",
                value = formatHz(band.freqHz),
                fraction = logToFraction(band.freqHz, FREQ_MIN, FREQ_MAX),
                onFractionChange = { onChange(band.copy(freqHz = fractionToLog(it, FREQ_MIN, FREQ_MAX))) },
                alpha = contentAlpha
            )
            if (!band.type.isPass) {
                PeqSliderRow(
                    label = "Gain",
                    value = "%+.1f dB".format(band.gainDb),
                    fraction = (band.gainDb + 24f) / 48f,
                    onFractionChange = { onChange(band.copy(gainDb = ((it * 48f - 24f) * 10f).toInt() / 10f)) },
                    alpha = contentAlpha
                )
            }
            PeqSliderRow(
                label = "Q",
                value = "%.2f".format(band.q),
                fraction = logToFraction(band.q, Q_MIN, Q_MAX),
                onFractionChange = { onChange(band.copy(q = fractionToLog(it, Q_MIN, Q_MAX))) },
                alpha = contentAlpha
            )
        }
    }
}

@Composable
private fun PeqSliderRow(
    label: String,
    value: String,
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    alpha: Float = 1f
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.width(76.dp)) {
            Text(label, color = PowerampTextSecondary.copy(alpha = alpha), fontSize = 10.5.sp, fontWeight = FontWeight.Bold.appWeight)
            Text(value, color = PowerampTextPrimary.copy(alpha = alpha), fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold.appWeight, maxLines = 1)
        }
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = onFractionChange,
            colors = SliderDefaults.colors(
                thumbColor = ThemeBerryPrimary.copy(alpha = alpha),
                activeTrackColor = ThemeBerryPrimary.copy(alpha = alpha),
                inactiveTrackColor = PowerampBorderGray
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

/** Preset-sheet row: "Import AutoEQ profile", or the currently loaded profile when [profileName] is set. */
@Composable
fun AutoEqSheetRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(76.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PowerampPillBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = ThemeBerryPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = PowerampTextPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.ExtraBold.appWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = PowerampTextSecondary,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold.appWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = ThemeBerryPrimary, modifier = Modifier.size(20.dp))
        }
    }
}
