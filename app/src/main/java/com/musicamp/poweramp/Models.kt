package com.musicamp.poweramp

import android.net.Uri
import androidx.compose.ui.graphics.Color

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String = artist,
    val genre: String = "Electronic",
    val year: Int = 2026,
    val composer: String = "Unknown composer",
    val folderPath: String = "/storage/emulated/0/Music",
    val folderName: String = "Music",
    val fileName: String = title,
    val durationMs: Long = 72000L,
    val uri: Uri? = null,
    val filePath: String = "",
    val bitrateKbps: Int = 256,
    val sampleRateHz: Int = 44100,
    val formatExt: String = "mp3",
    val fileSizeKb: Long = 2251L,
    val trackNumber: Int = 1,
    val discNumber: Int = 1,
    val rating: Int = 0, // 0 = neutral, 1 = liked (thumb up), -1 = unliked (thumb down)
    val playCount: Int = 0,
    val lastPlayedTimestamp: Long = 0L,
    val dateAddedSec: Long = System.currentTimeMillis() / 1000L,
    val lyrics: String = "",
    val waveformBars: List<Float> = emptyList(),
    val tagTitle: String = "",
    val albumId: Long = 0L,
    /** CUE virtual track: start/end inside [filePath] in ms (end 0 = to the end of the file). */
    val cueStartMs: Long = 0L,
    val cueEndMs: Long = 0L
) {
    /** True for a track cut out of a larger file by a cue sheet. */
    val isCue: Boolean get() = cueStartMs > 0L || cueEndMs > 0L
    /** True for an internet radio stream. */
    val isStream: Boolean get() = uri?.scheme == "http" || uri?.scheme == "https"
    val isDsd: Boolean get() = formatExt.equals("dsf", true) || formatExt.equals("dff", true)

    val formattedDuration: String
        get() {
            val totalSeconds = (durationMs / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }

    val metaLine: String
        get() = "$formattedDuration | ${formatExt.lowercase()}"

    val bottomBadgeInfo: String
        get() {
            val khz = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000}" else "%.1f".format(sampleRateHz / 1000f)
            return "$khz KHZ  $bitrateKbps KBPS  ${formatExt.uppercase()}"
        }
}

data class BookmarkItem(
    val id: Long,
    val trackId: Long,
    val trackTitle: String,
    val artist: String,
    val positionMs: Long,
    val createdAt: Long = System.currentTimeMillis()
) {
    val formattedPosition: String
        get() {
            val totalSec = (positionMs / 1000).coerceAtLeast(0)
            return "%d:%02d".format(totalSec / 60, totalSec % 60)
        }
}

data class PlaylistItem(
    val id: Long,
    val name: String,
    val trackIds: List<Long> = emptyList()
)

data class StreamItem(
    val id: Long,
    val title: String,
    val url: String,
    val genre: String = "Stream"
)

data class EqPreset(
    val name: String,
    val category: String, // "User", "Built-in", "AutoEq"
    val isParametric: Boolean = false,
    val preamp: Float = 0f,
    val bands: List<Float>, // 10 bands: 31, 62, 125, 250, 500, 1K, 2K, 4K, 8K, 16K (-15f..15f dB)
    val bass: Float = 0f,   // 0f..1f
    val treble: Float = 0f, // 0f..1f
    val isAssignedDevice: Boolean = false
)

data class ReverbPreset(
    val name: String,
    val damp: Float,
    val filter: Float,
    val fade: Float,
    val preDelay: Float,
    val preDelayMix: Float,
    val size: Float,
    val mix: Float
)

enum class RepeatMode(val label: String) {
    OFF("Repeat Off"),
    REPEAT_LIST("Repeat List"),
    REPEAT_CATEGORY("Repeat Category"),
    REPEAT_SONG("Repeat Song"),
    SINGLE_SONG("Single Song"),
    ADVANCE_CATEGORY("Advance Category")
}

enum class ShuffleMode(val label: String) {
    OFF("Shuffle Off"),
    ALL_SONGS("Shuffle All Available Songs"),
    JUST_SONGS("Shuffle Just Songs"),
    CATEGORIES("Shuffle Categories"),
    SONGS_AND_CATEGORIES("Shuffle Songs/Categories")
}

enum class VisMode(val label: String) {
    DISABLED("Visualization - Disabled"),
    FADE_CONTROLS("Visualization - Fade"),
    FULL_SCREEN("Visualization - Full Screen")
}

data class VisPreset(
    val id: String,
    val name: String,
    val category: String = "Built-in"
)

val BUILT_IN_VIS_PRESETS = listOf(
    // Spectrum family (drawn by drawVisualization)
    VisPreset("spectrum-centered", "spectrum-centered", "Built-in"),
    VisPreset("spectrum-centered-narrow", "spectrum-centered-narrow", "Built-in"),
    VisPreset("spectrum-centered-reflected", "spectrum-centered-reflected", "Built-in"),
    VisPreset("spectrum-centered-wide", "spectrum-centered-wide", "Built-in"),
    VisPreset("spectrum-dots", "spectrum-dots", "Built-in"),
    VisPreset("spectrum-narrow", "spectrum-narrow", "Built-in"),
    VisPreset("spectrum-plain", "spectrum-plain", "Built-in"),
    VisPreset("spectrum-quads-narrow", "spectrum-quads-narrow", "Built-in"),
    VisPreset("spectrum-quads-reflected", "spectrum-quads-reflected", "Built-in"),
    VisPreset("spectrum-reflected-wide", "spectrum-reflected-wide", "Built-in"),
    VisPreset("spectrum-wide", "spectrum-wide", "Built-in"),
    // Extra MusicAmp styles
    VisPreset("milkdrop_neon_wave", "neon-horizon", "MusicAmp"),
    VisPreset("bars_mirrored_eq", "bars-symmetrical-hd", "MusicAmp"),
    VisPreset("plasma_radial_pulse", "radial-bass-reactor", "MusicAmp"),
    VisPreset("oscilloscope_ribbon", "stereo-oscilloscope", "MusicAmp")
)

enum class MainNavTab {
    PLAYER,
    LIBRARY,
    EQUALIZER,
    SEARCH
}

enum class SortOrder(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    ALBUM_ARTIST("Album Artist"),
    YEAR("Year"),
    DURATION("Duration"),
    DATE_ADDED("Date Added"),
    PLAY_COUNT("Play Count"),
    RATING("Rating"),
    FILE_NAME("File Name"),
    TRACK_NUMBER("Track Number"),
    LAST_PLAYED("Last Played")
}

enum class LibraryViewStyle(val label: String) {
    LIST("List"),
    LIST_COMPACT("Compact"),
    GRID_2("Grid 2"),
    GRID_3("Grid 3")
}

enum class OutputKind(val label: String) {
    SPEAKER("Speaker"),
    WIRED("Wired"),
    BLUETOOTH("Bluetooth"),
    USB("USB"),
    OTHER("Other")
}

enum class MediaButtonAction(val label: String) {
    PREVIOUS("Previous"),
    PLAY_PAUSE("Play/Pause"),
    NEXT("Next"),
    SHUFFLE("Shuffle"),
    REPEAT("Repeat"),
    LIKE("Like"),
    REWIND_10("Rewind 10s"),
    FORWARD_10("Forward 10s")
}

enum class EqSubTab {
    EQUALIZER,
    VOLUME,
    REVERB
}

enum class LibraryCategory(
    val title: String,
    val circleColor: Color,
    val iconType: String
) {
    ALL_SONGS("All Songs", Color(0xFF6B85E8), "treble_clef"),
    FOLDERS("Folders", Color(0xFF5A91E8), "folder"),
    FOLDERS_HIERARCHY("Folders Hierarchy", Color(0xFF5370E6), "folder_tree"),
    ALBUMS("Albums", Color(0xFF6761DC), "album"),
    ARTISTS("Artists", Color(0xFF7B70B6), "mic"),
    ALBUM_ARTISTS("Album Artists", Color(0xFF7B70B6), "mic_star"),
    GENRES("Genres", Color(0xFF6D4569), "guitar"),
    YEARS("Years", Color(0xFF5F9EA8), "years"),
    COMPOSERS("Composers", Color(0xFF336B42), "person"),
    PLAYLISTS("Playlists", Color(0xFF7A5346), "playlist"),
    STREAMS("Streams", Color(0xFF7FA982), "stream"),
    QUEUE("Queue", Color(0xFF817A4A), "queue"),
    BOOKMARKS("Bookmarks", Color(0xFF4D77E8), "bookmark"),
    MOST_PLAYED("Most Played", Color(0xFFA77DC2), "most_played"),
    TOP_RATED("Top Rated", Color(0xFF5D737E), "thumb_up"),
    LOW_RATED("Low Rated", Color(0xFF6E6E6E), "thumb_down"),
    RECENTLY_PLAYED("Recently Played", Color(0xFF5258B8), "recent_played"),
    RECENTLY_ADDED("Recently Added", Color(0xFF687864), "plus"),
    LONG("Long", Color(0xFF5B48A8), "dots")
}

val BUILT_IN_EQ_PRESETS = listOf(
    EqPreset("Graphic", "User", false, 0f, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0f, 0f, true),
    EqPreset("Parametric", "User", true, 0f, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0f, 0f),
    EqPreset("Bass", "Built-in", false, 0f, listOf(6.5f, 5.2f, 3.8f, 1.5f, 0f, 0f, 0f, 0f, 0f, 0f), 0.45f, 0f),
    EqPreset("Bass & Treble", "Built-in", false, 0f, listOf(6.0f, 4.8f, 3.0f, 0.8f, -0.5f, 0f, 1.5f, 3.5f, 5.2f, 6.0f), 0.40f, 0.35f),
    EqPreset("Bass Extreme", "Built-in", false, -2f, listOf(9.5f, 8.2f, 6.0f, 3.0f, 0f, -1.0f, 0f, 0f, 0f, 0f), 0.75f, 0.1f),
    EqPreset("Classical", "Built-in", false, 0f, listOf(4.5f, 3.5f, 2.0f, 0.5f, -1.5f, -1.5f, 0f, 2.5f, 3.8f, 4.5f), 0.15f, 0.25f),
    EqPreset("Dance", "Built-in", false, 0f, listOf(5.8f, 7.0f, 4.5f, 0f, 1.5f, 3.5f, 4.5f, 3.8f, 2.5f, 0f), 0.50f, 0.30f),
    EqPreset("Flat", "Built-in", false, 0f, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f), 0f, 0f),
    EqPreset("Pop", "Built-in", false, 0f, listOf(-1.5f, 1.5f, 3.8f, 5.0f, 3.8f, 1.0f, -1.0f, -1.0f, 1.5f, 2.5f), 0.20f, 0.25f),
    EqPreset("Rock", "Built-in", false, 0f, listOf(5.2f, 3.8f, 1.5f, -2.0f, -3.0f, -1.0f, 2.5f, 4.5f, 5.8f, 6.0f), 0.35f, 0.40f),
    EqPreset("Techno", "Built-in", false, 0f, listOf(6.2f, 5.5f, 2.0f, -2.0f, -1.5f, 1.5f, 4.2f, 5.5f, 5.8f, 5.2f), 0.55f, 0.45f),
    EqPreset("Treble", "Built-in", false, 0f, listOf(0f, 0f, 0f, 0f, 0f, 1.2f, 3.0f, 5.0f, 6.5f, 7.5f), 0f, 0.50f),
    EqPreset("Vocal", "Built-in", false, 0f, listOf(-3.0f, -2.5f, -1.0f, 2.5f, 5.2f, 5.5f, 4.0f, 1.8f, 0f, -1.5f), 0f, 0.15f)
)

const val WAVEFORM_BARS = 84
val NEUTRAL_WAVEFORM: List<Float> = List(WAVEFORM_BARS) { 0.3f }
val DEFAULT_MUSIC_FOLDERS = listOf("/sdcard/Music", "/sdcard/Download")

val BUILT_IN_REVERB_PRESETS = listOf(
    ReverbPreset("Small Room", 0.35f, 0.40f, 0.25f, 0.15f, 0.20f, 0.30f, 0.25f),
    ReverbPreset("Medium Room", 0.45f, 0.50f, 0.45f, 0.30f, 0.35f, 0.55f, 0.35f),
    ReverbPreset("Large Hall", 0.60f, 0.55f, 0.75f, 0.50f, 0.45f, 0.85f, 0.50f),
    ReverbPreset("Plate", 0.25f, 0.70f, 0.60f, 0.10f, 0.50f, 0.65f, 0.40f),
    ReverbPreset("Cathedral", 0.75f, 0.45f, 0.90f, 0.70f, 0.60f, 0.95f, 0.60f)
)

/** Display label for a release year; 0 means the file has no year tag. */
fun yearLabel(year: Int): String = if (year > 0) year.toString() else "Unknown year"
