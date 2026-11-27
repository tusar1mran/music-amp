package com.musicamp.poweramp

import android.content.ContentUris
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.LruCache
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteOrder
import kotlin.math.ln
import kotlin.math.sqrt
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.AudioFormat
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.MediaScannerConnection
import android.media.PlaybackParams
import android.os.Environment
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.provider.MediaStore
import android.provider.DocumentsContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/** Simple Last.fm Scrobbler broadcast states. */
private const val SCROBBLE_START = 0
private const val SCROBBLE_RESUME = 1
private const val SCROBBLE_PAUSE = 2
private const val SCROBBLE_COMPLETE = 3
/** Ids of CUE virtual tracks live far above MediaStore ids. */
private const val CUE_ID_BASE = 1L shl 50
/** Ids of DSD files found in user-granted folders. */
private const val SAF_ID_BASE = 1L shl 52

class PowerampController(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main)
    private var mediaPlayer: PlayerEngine? = null
    private var equalizerFx: Equalizer? = null
    private var virtualizerFx: Virtualizer? = null
    private var presetReverbFx: PresetReverb? = null

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs: SharedPreferences = context.getSharedPreferences("poweramp_state", Context.MODE_PRIVATE)

    // One audio session shared by every MediaPlayer, so the effect chain survives track changes and crossfades
    private val fxSessionId: Int = audioManager.generateAudioSessionId()
    private var loudnessFx: LoudnessEnhancer? = null
    private var visualizerFx: Visualizer? = null
    @Volatile private var fftBars = FloatArray(48)
    @Volatile private var fftFresh = false
    /** Set by the Activity so the controller can ask for RECORD_AUDIO (needed only to read our own output for the visualizer). */
    var recordPermissionRequester: (() -> Unit)? = null
    private var nextPlayer: PlayerEngine? = null
    private var nextPlayerTrackId = -1L
    private var nextPrepared = false
    private var fadingOutPlayer: PlayerEngine? = null
    private var fadeJob: Job? = null
    private var fadeInGain = 1f
    private var duckGain = 1f
    private var playbackSpeed = 1f
    private var rawPosMs = 0L
    private var rawPosAt = 0L
    private var lastPositionSaveAt = 0L
    private var lastStreamVolumeSet = -1
    private var lastVolumeApplyAt = 0L
    private var playbackListIds: List<Long> = emptyList()
    private val history = ArrayDeque<Long>()
    private var shuffleFromId = -1L
    private var shuffleNextId = -1L
    private var focusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var noisyRegistered = false
    private var pendingLastTrackId = -1L
    private var pendingResumePositionMs = 0L
    private var pendingQueueIds: List<Long> = emptyList()
    private var restoredTrackData: Map<Long, JSONObject> = emptyMap()
    private val editedTrackIds = HashSet<Long>()
    private val hiddenTrackIds = HashSet<Long>()
    // Bounded by memory (32 MB), not entry count: a single cover can be several MB
    private val artCache = object : LruCache<Long, Bitmap>(32 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount / 1024
    }
    private val noArtIds = HashSet<Long>()
    fun getCachedArt(trackId: Long): Bitmap? = artCache.get(trackId)

    /** Live display refresh rate (60/90/120/144 Hz), auto-adapted by MainActivity. */
    @Volatile var displayRefreshRateHz: Float = 60f

    var scanning by mutableStateOf(false)
    var libraryLoaded by mutableStateOf(false)

    /** Bumped on every play/pause/seek/track change so the notification can refresh its state. */
    var playbackEpoch by mutableIntStateOf(0)

    // Navigation State
    var currentMainTab by mutableStateOf(MainNavTab.PLAYER)
    var currentEqSubTab by mutableStateOf(EqSubTab.EQUALIZER)
    var activeLibraryCategory by mutableStateOf<LibraryCategory?>(null)
    var activeSubFilter by mutableStateOf<String?>(null) // e.g., selected Folder, Album, Artist, Genre, Year, Playlist

    // Overlays / Dialogs / Menus
    var showMainMenuSheet by mutableStateOf(false)
    var showTrackActionDialog by mutableStateOf(false)
    var actionTargetTrack by mutableStateOf<AudioTrack?>(null)
    var showInfoTagsSheet by mutableStateOf(false)
    var showEditTagsDialog by mutableStateOf(false)
    var showLyricsScreen by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    var showEqPresetSheet by mutableStateOf(false)
    var showReverbPresetSheet by mutableStateOf(false)
    var showSettingsScreen by mutableStateOf(false)
    var showChangelogDialog by mutableStateOf(false)
    var showHelpDialog by mutableStateOf(false)
    var showAddToPlaylistDialog by mutableStateOf(false)
    var showAlbumArtDialog by mutableStateOf(false)
    var showAddStreamDialog by mutableStateOf(false)

    // Toast notification banner (Poweramp bottom/upper toast style)
    var toastTitle by mutableStateOf<String?>(null)
    var toastSubtitle by mutableStateOf<String?>(null)
    private var toastJob: Job? = null

    // Library Data
    val allTracks = mutableStateListOf<AudioTrack>()
    val queueTracks = mutableStateListOf<AudioTrack>()
    val bookmarks = mutableStateListOf<BookmarkItem>()
    val playlists = mutableStateListOf<PlaylistItem>()
    val streams = mutableStateListOf<StreamItem>()

    // Selection Mode in Lists
    var isSelectionMode by mutableStateOf(false)
    val selectedTrackIds = mutableStateListOf<Long>()

    // Playback State
    var currentTrackIndex by mutableIntStateOf(0)
    val currentTrack: AudioTrack?
        get() = streamTrack ?: allTracks.getOrNull(currentTrackIndex)

    var isPlaying by mutableStateOf(false)
    var currentPositionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    // Seeking on Next / Previous button hold & waveform scrubbing
    var isSeekingFwd by mutableStateOf(false)
    var isSeekingRwd by mutableStateOf(false)
    var seekingAccumulatedDeltaMs by mutableLongStateOf(0L)
    var isScrubbing by mutableStateOf(false)

    val progressFraction: Float
        get() = if (durationMs > 0L) (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var repeatMode by mutableStateOf(RepeatMode.REPEAT_CATEGORY)
    var shuffleMode by mutableStateOf(ShuffleMode.OFF)
    var visMode by mutableStateOf(VisMode.DISABLED)
    var visPhase by mutableFloatStateOf(0f)
    val visPresets = BUILT_IN_VIS_PRESETS
    var currentVisPresetIndex by mutableIntStateOf(0)
    val currentVisPreset: VisPreset
        get() = visPresets.getOrElse(currentVisPresetIndex) { visPresets.first() }
    var showVisPresetBar by mutableStateOf(false)
    var showVisPresetListDialog by mutableStateOf(false)
    var visPresetCycleMode by mutableStateOf("Locked") // "Locked", "Shuffle", "Cycle"
    var trackTransitionDirection by mutableIntStateOf(1) // 1 = next (slide left), -1 = prev (slide right)
    val liveSpectrumAmplitudes = mutableStateListOf<Float>().apply {
        repeat(48) { add(0f) }
    }

    // Sleep Timer State
    var sleepTimerMinutes by mutableIntStateOf(0) // 0 = Disabled
    var sleepTimerRemainingSeconds by mutableIntStateOf(0)
    var sleepPlayLastSongToEnd by mutableStateOf(false)
    private var stopAfterCurrentTrack by mutableStateOf(false)

    // Equalizer & Tone State
    var eqEnabled by mutableStateOf(true)
    var toneEnabled by mutableStateOf(true)
    var limitEnabled by mutableStateOf(true)
    var preampDb by mutableFloatStateOf(0.0f) // -15f..15f
    val bandFrequencies = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
    val bandGainsDb = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    var bassPercent by mutableIntStateOf(0)   // 0..100
    var treblePercent by mutableIntStateOf(0) // 0..100
    var selectedPresetName by mutableStateOf("Preset")
    val eqPresets = mutableStateListOf<EqPreset>().apply { addAll(BUILT_IN_EQ_PRESETS) }

    // Volume / Balance / Tempo / Stereo Expand Tab State
    var balanceValue by mutableFloatStateOf(0.00f)      // -1.00f..1.00f
    var stereoExpandPercent by mutableIntStateOf(0)     // 0..100
    var tempoEnabled by mutableStateOf(false)
    var tempoMultiplier by mutableFloatStateOf(1.00f)   // 0.50f..3.00f
    var monoEnabled by mutableStateOf(false)
    var volumePercent by mutableIntStateOf(60)          // 0..100

    // Reverb Tab State
    var reverbEnabled by mutableStateOf(false)
    var reverbDamp by mutableFloatStateOf(0.00f)
    var reverbFilter by mutableFloatStateOf(0.00f)
    var reverbFade by mutableFloatStateOf(0.00f)
    var reverbPreDelay by mutableFloatStateOf(0.00f)
    var reverbPreDelayMix by mutableFloatStateOf(0.00f)
    var reverbSize by mutableFloatStateOf(0.00f)
    var reverbMix by mutableFloatStateOf(0.00f)
    var selectedReverbPreset by mutableStateOf("Preset")
    val reverbPresets = mutableStateListOf<ReverbPreset>().apply { addAll(BUILT_IN_REVERB_PRESETS) }

    // Search State
    var searchQuery by mutableStateOf("")
    var searchFilterChip by mutableStateOf("All")
    val searchFilterOptions = listOf("All", "Albums", "Artists", "Album Artists", "Folders", "Genres", "Playlists")

    // ==========================================
    // Full Authentic Poweramp Settings State
    // ==========================================
    // 1. Look and Feel
    /** Theme mode (Light / Dark / Black (OLED) / Follow system). Backed by the global [AppTheme] so every screen recolours instantly. */
    var skinTheme: String
        get() = AppTheme.mode
        set(value) { AppTheme.mode = if (value in THEME_MODES) value else "Poweramp" }
    /** Accent colour name, one of [ACCENT_COLORS]. */
    var accentColor: String
        get() = AppTheme.accent
        set(value) { AppTheme.accent = if (ACCENT_COLORS.containsKey(value)) value else POWERAMP_GREEN }
    var settingsTheme by mutableStateOf("Default") // "Default", "Light", "Dark", "Follow Day/Night"
    var settingsFont by mutableStateOf("Bold") // "Default", "Alternative font", "Bold", "Bold+"
    var smoothSeekbarAnimation by mutableStateOf(true)
    var playPauseMorphAnimation by mutableStateOf(true)
    var trackSlideTransitions by mutableStateOf(true)
    var showStatusBarOverlay by mutableStateOf(true)
    var showRatingPill by mutableStateOf(true)
    var showMetaLine by mutableStateOf(true)
    private var _filenameAsTitle by mutableStateOf(false)
    var filenameAsTitle: Boolean
        get() = _filenameAsTitle
        set(value) {
            _filenameAsTitle = value
            refreshTitles()
        }

    // 2. Audio Engine
    var crossfadeEnabled by mutableStateOf(true)
    var crossfadeDurationSec by mutableFloatStateOf(2.5f) // 0.0s .. 10.0s
    var gaplessEnabled by mutableStateOf(true)
    var replayGainMode by mutableStateOf("Track Gain") // "Off", "Track Gain", "Album Gain"
    var replayGainPreampDb by mutableFloatStateOf(0.0f) // -6f..6f
    var dvcEnabled by mutableStateOf(true)
    var hiResAudioEnabled by mutableStateOf(true)
    var audioBufferSize by mutableStateOf("Normal (50ms)") // "Small (25ms)", "Normal (50ms)", "Large (100ms)", "Huge (200ms)"
    var pauseOnHeadsetDisconnect by mutableStateOf(true)
    var resumeOnHeadsetConnect by mutableStateOf(true)

    /** Bit-perfect USB DAC output (Android 14+). Bypasses the system mixer, effects and software volume. */
    var bitPerfectEnabled by mutableStateOf(false)
        private set
    var bitPerfectActive by mutableStateOf(false)
        private set
    var bitPerfectStatus by mutableStateOf("Off")
        private set
    private var bitPerfectOut: BitPerfectOutput? = null

    private fun bitPerfectOutput(): BitPerfectOutput =
        bitPerfectOut ?: BitPerfectOutput(audioManager, focusAttributes, uiHandler) { onUsbDevicesChanged() }
            .also { bitPerfectOut = it }

    fun setBitPerfect(enabled: Boolean) {
        if (enabled == bitPerfectEnabled) return
        bitPerfectEnabled = enabled
        if (!enabled) {
            bitPerfectOut?.release()
            bitPerfectActive = false
            bitPerfectStatus = "Off"
        }
        reopenForOutputChange()
        showToast(if (enabled) "Bit-perfect mode on" else "Bit-perfect mode off", bitPerfectStatus)
    }

    /** MediaPlayer path: points the DAC at [sampleRateHz] (16-bit) or releases it, and refreshes effects. */
    private fun updateBitPerfect(sampleRateHz: Int?) {
        if (bitPerfectEnabled) {
            val out = bitPerfectOutput()
            bitPerfectActive = out.engage(sampleRateHz ?: currentTrack?.sampleRateHz ?: 44100)
            bitPerfectStatus = out.statusText
        } else {
            bitPerfectOut?.release()
            bitPerfectActive = false
            bitPerfectStatus = "Off"
        }
        applyAllEffectsNow()
    }

    /** Re-opens the current track at the same position so a new output configuration takes effect. */
    private fun reopenForOutputChange() {
        if (mediaPlayer != null && currentStream == null && allTracks.isNotEmpty()) {
            loadAndStart(currentTrackIndex, autoPlay = isPlaying, positionMs = currentPositionMs)
        } else {
            syncBitPerfectState()
            applyAllEffectsNow()
        }
    }

    private fun onUsbDevicesChanged() {
        if (!bitPerfectEnabled) return
        reopenForOutputChange()
        showToast("Bit-perfect", bitPerfectStatus)
    }
    // ------------------------------------------------------------------------------------------
    // Native audio engine (MediaCodec + AudioTrack + own DSP). Falls back to MediaPlayer per track.
    // ------------------------------------------------------------------------------------------
    /** User setting: true = MusicAmp native engine (DSP, ReplayGain, DVC, hi-res, DSD), false = Android MediaPlayer. */
    var useNativeEngine by mutableStateOf(true)
        private set
    /** True while the current track is playing through the native engine. */
    var nativeEngineActive by mutableStateOf(false)
        private set
    /** Human-readable description of the current output path, for the settings status card. */
    var engineStatus by mutableStateOf("Idle")
        private set

    fun setNativeEngine(enabled: Boolean) {
        if (enabled == useNativeEngine) return
        useNativeEngine = enabled
        reopenForOutputChange()
    }

    // ----- Direct Volume Control -----
    /** True when volume is applied in software and the system media volume is pinned to max. */
    var dvcActive by mutableStateOf(false)
        private set

    /** Volume keys / knob while DVC is active (0..100). */
    fun setDvcVolumePercent(percent: Int) {
        volumePercent = percent.coerceIn(0, 100)
        applyVolumeAndBalance()
    }

    /** Hardware volume key step while DVC is active: direction -1, 0 or +1. */
    fun adjustDvcVolume(direction: Int) {
        if (direction == 0) return
        setDvcVolumePercent(volumePercent + direction * 4)
    }

    // ----- Parametric equalizer (AutoEQ compatible) -----
    var parametricEqEnabled by mutableStateOf(false)
    val parametricBands = mutableStateListOf<ParametricBand>()
    /** Name of the imported AutoEQ profile, empty if none. */
    var parametricProfileName by mutableStateOf("")

    fun setParametricEq(enabled: Boolean) {
        parametricEqEnabled = enabled
        applyEqualizerBands()
    }

    fun addParametricBand() {
        parametricBands.add(ParametricBand(FilterType.PEAK, 1000f, 0f, 1.0f))
        applyEqualizerBands()
    }

    fun updateParametricBand(index: Int, band: ParametricBand) {
        if (index !in parametricBands.indices) return
        parametricBands[index] = band.copy(
            freqHz = band.freqHz.coerceIn(20f, 20000f),
            gainDb = band.gainDb.coerceIn(-24f, 24f),
            q = band.q.coerceIn(0.1f, 10f)
        )
        applyEqualizerBands()
    }

    fun removeParametricBand(index: Int) {
        if (index in parametricBands.indices) parametricBands.removeAt(index)
        applyEqualizerBands()
    }

    fun resetParametricEq() {
        parametricBands.clear()
        parametricProfileName = ""
        applyEqualizerBands()
    }

    /** Imports an AutoEQ / Equalizer APO "ParametricEQ.txt" picked by the user (Storage Access Framework uri). */
    fun importAutoEq(uri: Uri, displayName: String? = null) {
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { s ->
                        s.readBytes().let { b -> if (b.size > 256 * 1024) null else String(b, Charsets.UTF_8) }
                    }
                } catch (_: Exception) {
                    null
                }
            }
            val parsed = text?.let { AutoEqParser.parse(it) }
            if (parsed == null) {
                showToast("Import failed", "Not an AutoEQ ParametricEQ.txt file")
                return@launch
            }
            parametricBands.clear()
            parametricBands.addAll(parsed.bands)
            preampDb = parsed.preampDb.coerceIn(-15f, 15f)
            parametricProfileName = displayName?.substringBeforeLast('.')?.removeSuffix(" ParametricEQ")
                ?: "AutoEQ profile"
            parametricEqEnabled = true
            eqEnabled = true
            applyEqualizerBands()
            showToast("AutoEQ imported", "${parsed.bands.size} filters, preamp %.1f dB".format(parsed.preampDb) +
                if (!nativeEngineActive) " (needs the MusicAmp engine)" else "")
        }
    }

    // ----- Internet radio streams -----
    /** The stream currently playing, or null when a library track is loaded. */
    var currentStream by mutableStateOf<StreamItem?>(null)
        private set

    fun addStream(title: String, url: String) {
        val u = url.trim()
        if (!(u.startsWith("http://", true) || u.startsWith("https://", true))) {
            showToast("Invalid stream URL", "Use an http:// or https:// address")
            return
        }
        streams.add(StreamItem(System.currentTimeMillis(), title.trim().ifBlank { Uri.parse(u).host ?: "Stream" }, u))
        showToast("Stream added", title)
    }

    /** Plays an internet radio stream (stops the library track). */
    fun playStream(stream: StreamItem) {
        startStream(stream)
    }

    private fun startStream(stream: StreamItem) {
        startStreamPlayback(stream)
    }

    fun removeStream(stream: StreamItem) {
        streams.removeAll { it.id == stream.id }
    }

    // ----- Lyrics -----
    /**
     * Lyrics for [track]: user-edited text first, then a sibling .lrc file, then lyrics embedded in the tags.
     * Synced (LRC) text keeps its [mm:ss.xx] timestamps.
     */
    suspend fun loadLyrics(track: AudioTrack): String = withContext(Dispatchers.IO) {
        if (track.lyrics.isNotBlank()) return@withContext track.lyrics
        try {
            if (track.filePath.isNotBlank()) {
                val f = File(track.filePath)
                val lrc = File(f.parentFile, f.nameWithoutExtension + ".lrc")
                if (lrc.exists() && lrc.canRead() && lrc.length() < 512 * 1024) return@withContext lrc.readText()
                readSidecar(track.filePath, "lrc")?.let { return@withContext it }
            }
        } catch (_: Exception) {
        }
        TagReader.read(context, track)?.lyrics.orEmpty()
    }

    // 3. Visualization
    var visFadedControlsOpacity by mutableFloatStateOf(0.40f) // 0.10f..1.00f
    var visPresetDurationSec by mutableIntStateOf(10) // 5, 10, 15, 30, 0 (Manual)
    var visFpsTarget by mutableIntStateOf(60) // 30, 60
    var visShowPresetToast by mutableStateOf(true)

    // 4. Background
    var backgroundType by mutableStateOf("Pure Black (OLED)") // "Pure Black (OLED)", "Album Art Blur", "Blurred Mountain Glow"
    var backgroundBlurRadius by mutableFloatStateOf(0.65f) // 0.1f..1.0f
    var backgroundSaturation by mutableFloatStateOf(1.0f) // 0.0f..2.0f
    var backgroundDimOpacity by mutableFloatStateOf(0.35f) // 0.0f..0.8f

    // 5. Album Art
    /** Off by default: sends album and artist names to MusicBrainz / Cover Art Archive. */
    var autoDownloadAlbumArt by mutableStateOf(false)
    var highResAlbumArt by mutableStateOf(true)
    var preferEmbeddedArt by mutableStateOf(true)
    var albumArtOnLockscreen by mutableStateOf(true)

    // 6. Library
    var autoScanOnStartup by mutableStateOf(true)
    var ignoreShortTracks by mutableStateOf(true)
    val selectedMusicFolders = mutableStateListOf("/sdcard/Music", "/sdcard/Download")

    // 7. Headset / Bluetooth
    var headsetButtonsEnabled by mutableStateOf(true)
    var bluetoothDvcEnabled by mutableStateOf(true)

    // 8. Misc
    var androidAutoEnabled by mutableStateOf(true)
    var scrobblingEnabled by mutableStateOf(false)

    // ==========================================================================================
    // Device volume (knob <-> hardware volume keys)
    // ==========================================================================================
    private fun syncDeviceVolume() {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            volumePercent = ((curVol.toFloat() / maxVol.toFloat()) * 100f).toInt().coerceIn(0, 100)
            lastStreamVolumeSet = curVol
        } catch (_: Exception) {
            volumePercent = 60
        }
    }

    private fun registerVolumeObserver() {
        try {
            context.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true,
                object : ContentObserver(uiHandler) {
                    override fun onChange(selfChange: Boolean) {
                        // Ignore echoes of our own setStreamVolume() and active knob dragging
                        if (SystemClock.uptimeMillis() - lastVolumeApplyAt < 600L) return
                        // With DVC the system volume is held at max; the knob is the app's own volume
                        if (dvcActive) return
                        try {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            if (cur != lastStreamVolumeSet) {
                                lastStreamVolumeSet = cur
                                volumePercent = ((cur.toFloat() / maxVol) * 100f).toInt().coerceIn(0, 100)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            )
        } catch (_: Exception) {
        }
    }

    fun showToast(title: String, subtitle: String? = null) {
        toastTitle = title
        toastSubtitle = subtitle
        toastJob?.cancel()
        toastJob = scope.launch {
            delay(2200)
            toastTitle = null
            toastSubtitle = null
        }
    }

    // ==========================================================================================
    // Library scan (MediaStore, off the main thread)
    // ==========================================================================================
    fun scanMediaLibrary() {
        // Fast MediaStore query first so the library appears at once; then, if enabled, a silent
        // file-system rescan that picks up files added while the app was closed.
        refreshLibrary(showFeedback = false, deepScan = false)
        if (autoScanOnStartup) {
            scope.launch {
                while (scanning) delay(100L)
                refreshLibrary(showFeedback = false, deepScan = true)
            }
        }
    }

    /**
     * Rescans device storage & MediaStore for newly added or removed audio files.
     * Clears the negative artwork cache so freshly added covers appear immediately.
     */
    fun refreshLibrary(showFeedback: Boolean = true, deepScan: Boolean = showFeedback) {
        if (scanning) return
        scanning = true
        val prevIds = allTracks.map { it.id }.toSet()
        scope.launch {
            if (deepScan) {
                // Ask Android's MediaScanner to index any newly dropped files in Music / Download folders
                withContext(Dispatchers.IO) {
                    try {
                        val dirs = LinkedHashSet<File>()
                        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC))
                        dirs.add(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS))
                        selectedMusicFolders.forEach { folder ->
                            dirs.add(File(folder.replace("/sdcard", Environment.getExternalStorageDirectory().absolutePath)))
                        }
                        val audioExts = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus", "wma", "aiff", "dsf", "dff", "ape", "wv")
                        val pathsToScan = ArrayList<String>()
                        // The silent startup scan only re-indexes files changed since the last scan
                        val since = if (showFeedback) 0L else prefs.getLong("lastDeepScanAt", 0L)
                        prefs.edit().putLong("lastDeepScanAt", System.currentTimeMillis()).apply()
                        dirs.filter { it.exists() && it.isDirectory }.forEach { dir ->
                            dir.walkTopDown().maxDepth(4).forEach { f ->
                                if (f.isFile && f.extension.lowercase() in audioExts && f.lastModified() > since) {
                                    pathsToScan.add(f.absolutePath)
                                }
                            }
                        }
                        if (pathsToScan.isNotEmpty()) {
                            // Wait for the scanner to finish (bounded), so the requery below sees the new files
                            val done = java.util.concurrent.CountDownLatch(pathsToScan.size)
                            MediaScannerConnection.scanFile(context, pathsToScan.toTypedArray(), null) { _, _ -> done.countDown() }
                            done.await(15, java.util.concurrent.TimeUnit.SECONDS)
                        }
                    } catch (_: Exception) {
                    }
                }
            }
            noArtIds.clear()
            val found = try {
                withContext(Dispatchers.IO) { queryMediaStore() }
            } catch (_: Exception) {
                emptyList()
            }
            applyScanResult(found)
            scanning = false
            libraryLoaded = true
            if (showFeedback) {
                val added = found.count { it.id !in prevIds }
                val subtitle = when {
                    added > 0 -> "${found.size} songs (+$added new)"
                    found.isEmpty() -> "No audio files found"
                    else -> "${found.size} songs up to date"
                }
                showToast("Library Refreshed", subtitle)
            }
        }
    }

    private fun isFolderAllowed(path: String): Boolean {
        if (selectedMusicFolders.toList() == DEFAULT_MUSIC_FOLDERS) return true // untouched = scan everything
        if (selectedMusicFolders.isEmpty()) return true
        val norm = path.replace("/storage/emulated/0", "/sdcard")
        return selectedMusicFolders.any { norm.startsWith(it.replace("/storage/emulated/0", "/sdcard")) }
    }

    // ==========================================================================================
    // Extra folders granted through the Storage Access Framework. Android does not index DSD files as
    // audio and hides .cue / .lrc files from apps that only hold the audio permission; a folder the user
    // picks once can be read directly.
    // ==========================================================================================
    /** Tree URIs (as strings) the user granted. */
    val extraFolders = mutableStateListOf<String>()
    /** Lower-case file-system path of each .cue / .lrc found in [extraFolders] -> its document URI. */
    @Volatile private var safSidecars: Map<String, Uri> = emptyMap()

    fun addExtraFolder(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            showToast("Folder access denied")
            return
        }
        val s = uri.toString()
        if (s !in extraFolders) extraFolders.add(s)
        refreshLibrary(showFeedback = true, deepScan = false)
    }

    fun removeExtraFolder(uriString: String) {
        try {
            context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        extraFolders.remove(uriString)
        refreshLibrary(showFeedback = false, deepScan = false)
    }

    /** Human-readable name of a granted folder, e.g. "Music/DSD". */
    fun extraFolderLabel(uriString: String): String = try {
        DocumentsContract.getTreeDocumentId(Uri.parse(uriString)).substringAfter(':').ifBlank { "Internal storage" }
    } catch (_: Exception) {
        uriString
    }

    /** "primary:Music/Album/x.cue" -> "/storage/emulated/0/Music/Album/x.cue" (the path MediaStore reports). */
    private fun docIdToPath(docId: String): String {
        val vol = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val root = if (vol.equals("primary", true)) "/storage/emulated/0" else "/storage/$vol"
        return if (rel.isEmpty()) root else "$root/$rel"
    }

    private fun scanExtraFolders(): List<AudioTrack> {
        val tracks = ArrayList<AudioTrack>()
        val side = HashMap<String, Uri>()
        val resolver = context.contentResolver
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )
        for (s in extraFolders.toList()) {
            val tree = Uri.parse(s)
            val rootId = try { DocumentsContract.getTreeDocumentId(tree) } catch (_: Exception) { continue }
            val stack = ArrayDeque<Pair<String, Int>>()
            stack.add(rootId to 0)
            var visited = 0
            while (stack.isNotEmpty() && visited < 20_000) {
                val (docId, depth) = stack.removeLast()
                try {
                    resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId), cols, null, null, null)?.use { c ->
                        while (c.moveToNext()) {
                            visited++
                            val id = c.getString(0) ?: continue
                            val name = c.getString(1) ?: continue
                            if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                                if (depth < 6) stack.add(id to depth + 1)
                                continue
                            }
                            val docUri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                            when (name.substringAfterLast('.', "").lowercase()) {
                                "cue", "lrc" -> side[docIdToPath(id).lowercase()] = docUri
                                "dsf", "dff" -> dsdTrack(docUri, id, name, c.getLong(3), c.getLong(4))?.let { tracks.add(it) }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
        safSidecars = side
        val minDuration = if (ignoreShortTracks) 30_000L else 0L
        return tracks.filter { it.durationMs >= minDuration && it.id !in hiddenTrackIds }
    }

    private fun dsdTrack(uri: Uri, docId: String, name: String, size: Long, modified: Long): AudioTrack? {
        val dsd = DsdSource.open(context, uri, "") ?: return null
        val durationMs: Long
        val rate: Int
        try {
            durationMs = dsd.durationUs / 1000L
            rate = dsd.dsdRate
        } finally {
            dsd.release()
        }
        val path = docIdToPath(docId)
        val parent = path.substringBeforeLast('/')
        val ext = name.substringAfterLast('.').lowercase()
        val base = AudioTrack(id = 0L, title = name, artist = "", album = "", uri = uri, formatExt = ext)
        val tags = TagReader.read(context, base)
        val title = tags?.title ?: name.substringBeforeLast('.')
        val artist = tags?.artist ?: "Unknown artist"
        return AudioTrack(
            id = SAF_ID_BASE + (docId.hashCode().toLong() and 0xFFFFFFFFL),
            title = title, tagTitle = title, artist = artist, albumArtist = artist,
            album = tags?.album ?: parent.substringAfterLast('/'),
            genre = "Unknown genre", year = 0, composer = "Unknown composer",
            folderPath = parent, folderName = parent.substringAfterLast('/'), fileName = name,
            durationMs = durationMs.coerceAtLeast(1L), uri = uri, filePath = "",
            bitrateKbps = rate / 1000 * 2, sampleRateHz = rate, formatExt = ext,
            fileSizeKb = (size / 1024L).coerceAtLeast(1L), trackNumber = tags?.trackNumber ?: 0,
            dateAddedSec = modified / 1000L, waveformBars = NEUTRAL_WAVEFORM
        )
    }

    /** Reads a .cue / .lrc next to [audioPath] from a granted folder, if there is one. */
    private fun readSidecar(audioPath: String, ext: String): String? {
        if (audioPath.isBlank()) return null
        val noExt = audioPath.substringBeforeLast('.')
        val uri = safSidecars["$noExt.$ext".lowercase()] ?: safSidecars["$audioPath.$ext".lowercase()] ?: return null
        return try {
            context.contentResolver.openInputStream(uri)?.use { s ->
                val bytes = s.readBytes()
                if (bytes.size > 512 * 1024) return null
                val text = String(bytes, Charsets.UTF_8)
                if (text.contains('�')) String(bytes, charset("windows-1252")) else text
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun queryMediaStore(): List<AudioTrack> {
        val out = ArrayList<AudioTrack>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.COMPOSER
        )
        val hasR = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        if (hasR) {
            proj += MediaStore.Audio.Media.GENRE
            proj += MediaStore.Audio.Media.ALBUM_ARTIST
        }
        val minDuration = if (ignoreShortTracks) 30_000L else 0L
        val selection = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
            "${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= ?"
        try {
            context.contentResolver.query(
                base, proj.toTypedArray(), selection, arrayOf(minDuration.toString()),
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val iData = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val iSize = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val iYear = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val iName = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val iComposer = c.getColumnIndexOrThrow(MediaStore.Audio.Media.COMPOSER)
                val iGenre = if (hasR) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
                val iAlbumArtist = if (hasR) c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST) else -1

                fun clean(v: String?, fallback: String): String =
                    v?.trim()?.takeIf { it.isNotEmpty() && !it.equals("<unknown>", true) } ?: fallback

                while (c.moveToNext()) {
                    val id = c.getLong(iId)
                    if (id in hiddenTrackIds) continue
                    val path = c.getString(iData).orEmpty()
                    val parent = if (path.isNotEmpty()) File(path).parent.orEmpty() else ""
                    if (parent.isNotEmpty() && !isFolderAllowed(parent)) continue
                    val displayName = clean(c.getString(iName), "Track $id")
                    val nameNoExt = displayName.substringBeforeLast('.', displayName)
                    val ext = displayName.substringAfterLast('.', "mp3").lowercase()
                    val tagTitle = clean(c.getString(iTitle), nameNoExt)
                    val artist = clean(c.getString(iArtist), "Unknown artist")
                    val durationMs = c.getLong(iDur).coerceAtLeast(1L)
                    val sizeBytes = c.getLong(iSize)
                    val rawTrack = c.getInt(iTrack)
                    val kbps = if (durationMs > 0) ((sizeBytes * 8L) / durationMs).toInt().coerceIn(32, 9216) else 256
                    out.add(
                        AudioTrack(
                            id = id,
                            title = if (filenameAsTitle) nameNoExt else tagTitle,
                            tagTitle = tagTitle,
                            artist = artist,
                            album = clean(c.getString(iAlbum), "Unknown album"),
                            albumArtist = if (iAlbumArtist >= 0) clean(c.getString(iAlbumArtist), artist) else artist,
                            genre = if (iGenre >= 0) clean(c.getString(iGenre), "Unknown genre") else "Unknown genre",
                            year = c.getInt(iYear).coerceAtLeast(0),
                            composer = clean(c.getString(iComposer), "Unknown composer"),
                            folderPath = parent,
                            folderName = if (parent.isNotEmpty()) File(parent).name else "Music",
                            fileName = displayName,
                            durationMs = durationMs,
                            uri = ContentUris.withAppendedId(base, id),
                            filePath = path,
                            bitrateKbps = kbps,
                            sampleRateHz = 44100,
                            formatExt = ext,
                            fileSizeKb = (sizeBytes / 1024L).coerceAtLeast(1L),
                            trackNumber = (rawTrack % 1000).coerceAtLeast(0),
                            discNumber = (rawTrack / 1000).coerceAtLeast(1),
                            dateAddedSec = c.getLong(iAdded),
                            albumId = c.getLong(iAlbumId),
                            waveformBars = NEUTRAL_WAVEFORM
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // Audio permission not granted yet
        }
        return expandCueTracks(out + scanExtraFolders())
    }

    /**
     * Splits long single-file album rips into the tracks of their cue sheet: an embedded CUESHEET tag,
     * a FLAC CUESHEET block, or a .cue file next to the audio file (when the file system allows reading it).
     */
    private fun expandCueTracks(tracks: List<AudioTrack>): List<AudioTrack> {
        val out = ArrayList<AudioTrack>(tracks.size)
        for (t in tracks) {
            val cue = if (t.durationMs >= 10 * 60_000L) try { findCue(t) } catch (_: Exception) { null } else null
            if (cue == null) { out.add(t); continue }
            for ((i, ct) in cue.tracks.withIndex()) {
                val end = cue.tracks.getOrNull(i + 1)?.startMs ?: 0L
                val dur = (if (end > 0L) end else t.durationMs) - ct.startMs
                if (dur <= 0L) continue
                val title = ct.title?.takeIf { it.isNotBlank() } ?: "Track %02d".format(ct.number)
                out.add(
                    t.copy(
                        id = CUE_ID_BASE + t.id * 128 + ct.number,
                        title = title, tagTitle = title,
                        artist = ct.performer ?: cue.albumPerformer ?: t.artist,
                        albumArtist = cue.albumPerformer ?: t.albumArtist,
                        album = cue.albumTitle ?: t.album,
                        trackNumber = ct.number,
                        durationMs = dur,
                        cueStartMs = ct.startMs,
                        cueEndMs = end
                    )
                )
            }
        }
        return out
    }

    private fun findCue(t: AudioTrack): CueSheet? {
        val tags = tagsFor(t)
        tags?.cueSheet?.let { text -> CueParser.parse(text, t.fileName)?.let { return it } }
        val rate = tags?.sampleRate
        if (tags != null && rate != null && rate > 0 && tags.flacCueOffsets.size >= 2) {
            return CueSheet(null, null, tags.flacCueOffsets.map { (n, s) -> CueTrack(n, null, null, s * 1000L / rate) })
        }
        readSidecar(t.filePath, "cue")?.let { text -> CueParser.parse(text, t.fileName)?.let { return it } }
        if (t.filePath.isBlank()) return null
        val audio = File(t.filePath)
        val candidates = listOf(File(audio.parentFile, audio.nameWithoutExtension + ".cue"), File(audio.path + ".cue"))
        for (c in candidates) {
            if (!c.canRead() || c.length() > 256 * 1024) continue
            val bytes = c.readBytes()
            var text = String(bytes, Charsets.UTF_8)
            if (text.contains('�')) text = String(bytes, charset("windows-1252")) // older rippers
            CueParser.parse(text, t.fileName)?.let { return it }
        }
        return null
    }

    private fun applyScanResult(found: List<AudioTrack>) {
        val previousId = currentTrack?.id ?: pendingLastTrackId
        val merged = found.map { t ->
            val saved = restoredTrackData[t.id] ?: return@map t
            t.copy(
                title = if (!filenameAsTitle) saved.optString("title", t.title) else t.title,
                tagTitle = if (!filenameAsTitle) saved.optString("title", t.tagTitle) else t.tagTitle,
                artist = saved.optString("artist", t.artist),
                album = saved.optString("album", t.album),
                albumArtist = saved.optString("albumArtist", t.albumArtist),
                genre = saved.optString("genre", t.genre),
                year = saved.optInt("year", t.year),
                composer = saved.optString("composer", t.composer),
                lyrics = saved.optString("lyrics", t.lyrics),
                rating = saved.optInt("rating", 0),
                playCount = saved.optInt("playCount", 0),
                lastPlayedTimestamp = saved.optLong("lastPlayed", 0L)
            )
        }
        val playingNow = isPlaying && mediaPlayer != null
        val previousCurrentId = currentTrack?.id
        val previousPositionMs = currentPositionMs
        allTracks.clear()
        allTracks.addAll(merged)
        restoreQueue()

        val idx = merged.indexOfFirst { it.id == previousId }.takeIf { it >= 0 } ?: 0
        currentTrackIndex = idx
        if (merged.isEmpty()) {
            durationMs = 0L
            currentPositionMs = 0L
            return
        }
        durationMs = merged[idx].durationMs
        if (!playingNow) {
            // Keep the user's place: saved position on a fresh start, current position on a manual rescan
            val resume = when (merged[idx].id) {
                pendingLastTrackId -> pendingResumePositionMs
                previousCurrentId -> previousPositionMs
                else -> 0L
            }
            pendingResumePositionMs = 0L
            loadAndStart(idx, autoPlay = false, positionMs = resume)
        } else {
            onTrackChanged(merged[idx])
        }
        pendingLastTrackId = -1L
    }

    fun refreshTitles() {
        for (i in allTracks.indices) {
            val t = allTracks[i]
            val nameNoExt = t.fileName.substringBeforeLast('.', t.fileName)
            val wanted = if (filenameAsTitle) nameNoExt else t.tagTitle.ifBlank { t.title }
            if (wanted != t.title) allTracks[i] = t.copy(title = wanted)
        }
    }

    // ==========================================================================================
    // Album art + real waveform + audio properties
    // ==========================================================================================
    suspend fun loadArt(track: AudioTrack): Bitmap? = withContext(Dispatchers.IO) {
        artCache.get(track.id)?.let { return@withContext it }
        if (track.id in noArtIds) return@withContext null
        val maxSide = if (highResAlbumArt) 1200 else 500

        fun embedded(): Bitmap? {
            if (track.isStream) return null
            val retriever = MediaMetadataRetriever()
            return try {
                if (track.uri != null) retriever.setDataSource(context, track.uri) else retriever.setDataSource(track.filePath)
                retriever.embeddedPicture?.let { bytes ->
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    var sample = 1
                    while (bounds.outWidth / sample > maxSide) sample *= 2
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        fun folderImage(): Bitmap? {
            // MediaStore thumbnail: works with the audio permission only and also covers folder art (cover.jpg etc.)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && track.uri != null && !track.isStream) {
                try {
                    return context.contentResolver.loadThumbnail(track.uri, android.util.Size(maxSide, maxSide), null)
                } catch (_: Exception) {
                }
            }
            if (track.filePath.isNotBlank()) {
                try {
                    val dir = File(track.filePath).parentFile
                    val cover = listOf("cover.jpg", "folder.jpg", "cover.png", "folder.png", "albumart.jpg", "front.jpg")
                        .map { File(dir, it) }.firstOrNull { it.exists() }
                    if (cover != null) {
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(cover.absolutePath, bounds)
                        var sample = 1
                        while (bounds.outWidth / sample > maxSide) sample *= 2
                        return BitmapFactory.decodeFile(cover.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                    }
                } catch (_: Exception) {
                }
            }
            return null
        }

        var bmp: Bitmap? = if (preferEmbeddedArt) embedded() ?: folderImage() else folderImage() ?: embedded()
        var downloaded = false
        if (bmp == null && autoDownloadAlbumArt && !track.isStream) {
            bmp = AlbumArtFetcher.fetch(context, track.albumArtist.ifBlank { track.artist }, track.album, highResAlbumArt)
            downloaded = bmp != null
        }
        if (bmp != null) artCache.put(track.id, bmp)
        else if (!autoDownloadAlbumArt) noArtIds.add(track.id) // with downloads on, a later visit may succeed when online
        if (downloaded) withContext(Dispatchers.Main) { playbackEpoch++ } // notification and widget pick up the cover
        bmp
    }

    private fun probeAndAnalyse(track: AudioTrack) {
        if (track.isStream) return
        scope.launch {
            // 1. Real audio properties (bitrate / sample rate)
            val props = withContext(Dispatchers.IO) { probeAudioProperties(track) }
            if (props != null) {
                val idx = allTracks.indexOfFirst { it.id == track.id }
                if (idx >= 0) {
                    val t = allTracks[idx]
                    val nb = props.first ?: t.bitrateKbps
                    val ns = props.second ?: t.sampleRateHz
                    if (nb != t.bitrateKbps || ns != t.sampleRateHz) allTracks[idx] = t.copy(bitrateKbps = nb, sampleRateHz = ns)
                }
            }
            // 2. Real waveform for the waveseek bar (decoded once, then cached on disk)
            // A CUE track is a slice of a bigger file and DSD has no Android decoder: keep the neutral bar
            val bars = if (track.isCue || track.isDsd) null else withContext(Dispatchers.IO) { loadOrComputeWaveform(track) }
            if (bars != null) {
                val idx = allTracks.indexOfFirst { it.id == track.id }
                if (idx >= 0) allTracks[idx] = allTracks[idx].copy(waveformBars = bars)
            }
        }
    }

    private fun probeAudioProperties(track: AudioTrack): Pair<Int?, Int?>? {
        if (track.isDsd) {
            val d = DsdSource.open(context, track.uri, track.filePath) ?: return null
            return try { Pair(d.dsdRate / 1000 * 2, d.dsdRate) } finally { d.release() }
        }
        val r = MediaMetadataRetriever()
        return try {
            if (track.uri != null) r.setDataSource(context, track.uri) else r.setDataSource(track.filePath)
            val br = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { it / 1000 }?.takeIf { it > 0 }
            val sr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.takeIf { it > 0 }
            } else null
            Pair(br, sr)
        } catch (_: Exception) {
            null
        } finally {
            try { r.release() } catch (_: Exception) {}
        }
    }

    private fun loadOrComputeWaveform(track: AudioTrack): List<Float>? {
        val cacheFile = File(context.cacheDir, "wavr_${track.id}_${track.durationMs}.txt")
        try {
            if (cacheFile.exists()) {
                val parts = cacheFile.readText().split(',').mapNotNull { it.toFloatOrNull() }
                if (parts.size == WAVEFORM_BARS) return parts
            }
        } catch (_: Exception) {
        }
        val computed = computeWaveform(track) ?: return null
        try {
            cacheFile.writeText(computed.joinToString(","))
        } catch (_: Exception) {
        }
        return computed
    }

    /** Decodes the file to PCM and takes per-segment peaks. Runs on a background thread. */
    private fun computeWaveform(track: AudioTrack): List<Float>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (track.uri != null) extractor.setDataSource(context, track.uri, null) else extractor.setDataSource(track.filePath)
            var audioIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioIdx = i; format = f; break
                }
            }
            if (audioIdx < 0 || format == null) return null
            extractor.selectTrack(audioIdx)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
            else track.durationMs * 1000L
            if (durationUs <= 0L) return null
            var channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 2
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val sumSq = DoubleArray(WAVEFORM_BARS)
            val counts = IntArray(WAVEFORM_BARS)
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var guard = 0
            while (!outputDone && guard++ < 400_000) {
                if (!inputDone) {
                    val inIx = codec.dequeueInputBuffer(5000)
                    if (inIx >= 0) {
                        val buf = codec.getInputBuffer(inIx) ?: continue
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIx = codec.dequeueOutputBuffer(info, 5000)
                when {
                    outIx >= 0 -> {
                        if (info.size > 0) {
                            val out = codec.getOutputBuffer(outIx)
                            if (out != null) {
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                val shorts = out.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                                val bar = ((info.presentationTimeUs.toDouble() / durationUs) * WAVEFORM_BARS).toInt()
                                    .coerceIn(0, WAVEFORM_BARS - 1)
                                var i = 0
                                val n = shorts.remaining()
                                val stride = (channels.coerceAtLeast(1)) * 4
                                while (i < n) {
                                    val v = shorts.get(i).toDouble()
                                    sumSq[bar] += v * v
                                    counts[bar]++
                                    i += stride
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val of = codec.outputFormat
                        if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                }
            }
            // RMS level per bar (shows real loudness changes; peaks saturate on loud music)
            val rms = FloatArray(WAVEFORM_BARS) { i ->
                if (counts[i] > 0) kotlin.math.sqrt(sumSq[i] / counts[i]).toFloat() else 0f
            }
            val maxRms = rms.max()
            if (maxRms <= 0f) return null
            for (i in rms.indices) if (rms[i] <= 0f && i > 0) rms[i] = rms[i - 1]
            return rms.map { (it / maxRms).coerceIn(0f, 1f).let { v -> kotlin.math.sqrt(v.toDouble()).toFloat() }.coerceIn(0.10f, 1f) }
        } catch (_: Exception) {
            return null
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }
    }

    // ==========================================================================================
    // Audio focus, headset handling, foreground service
    // ==========================================================================================
    private val focusAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                if (isPlaying) pause(abandonFocus = false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (isPlaying) {
                    resumeOnFocusGain = true
                    pause(abandonFocus = false)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                duckGain = 0.25f
                pushVolumes()
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                duckGain = 1f
                pushVolumes()
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    play()
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(focusAttributes)
                .setOnAudioFocusChangeListener(focusListener, uiHandler)
                .build().also { focusRequest = it }
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) {
            true
        }
    }

    private fun abandonAudioFocus() {
        try {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } catch (_: Exception) {
        }
    }

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY && pauseOnHeadsetDisconnect && isPlaying) {
                pause()
                pausedByHeadsetDisconnect = true
                showToast("Paused", "Headset disconnected")
            }
        }
    }

    // ----- Headset (re)connection: resume, and re-evaluate DVC / bit-perfect for the new route -----
    private var pausedByHeadsetDisconnect = false
    private var deviceCallbackArmed = false
    private val headsetTypes = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_BLE_HEADSET
    )
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!deviceCallbackArmed) return
            updateDvc()
            val headset = addedDevices?.any { it.isSink && it.type in headsetTypes } == true
            if (headset && resumeOnHeadsetConnect && pausedByHeadsetDisconnect && !isPlaying) {
                pausedByHeadsetDisconnect = false
                // Bluetooth profiles connect in stages; give the route a moment before starting
                uiHandler.postDelayed({
                    if (!isPlaying) {
                        play()
                        showToast("Resumed", "Headset connected")
                    }
                }, 1200L)
            }
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (deviceCallbackArmed) updateDvc()
        }
    }

    private fun registerRouteCallback() {
        try { audioManager.registerAudioDeviceCallback(routeCallback, uiHandler) } catch (_: Exception) {}
        // The first callback lists devices that were already connected; ignore it
        uiHandler.postDelayed({ deviceCallbackArmed = true }, 1500L)
    }

    // ----- Scrobbling through Simple Last.fm Scrobbler's broadcast API (no Last.fm keys in this app) -----
    private fun scrobble(state: Int) {
        if (!scrobblingEnabled) return
        val t = currentTrack ?: return
        try {
            val i = Intent("com.adam.aslfms.notify.playstatechanged")
                .setPackage("com.adam.aslfms")
                .putExtra("state", state)
                .putExtra("app-name", "MusicAmp")
                .putExtra("app-package", context.packageName)
                .putExtra("artist", t.artist)
                .putExtra("album", t.album)
                .putExtra("albumartist", t.albumArtist)
                .putExtra("track", t.title)
                .putExtra("duration", (durationMs / 1000L).toInt())
                .putExtra("source", if (currentStream != null) "R" else "P")
            if (t.trackNumber > 0) i.putExtra("track-number", t.trackNumber)
            context.sendBroadcast(i)
        } catch (_: Exception) {
        }
    }

    private fun setNoisyReceiver(register: Boolean) {
        try {
            if (register && !noisyRegistered) {
                ContextCompat.registerReceiver(
                    context, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                noisyRegistered = true
            } else if (!register && noisyRegistered) {
                context.unregisterReceiver(noisyReceiver)
                noisyRegistered = false
            }
        } catch (_: Exception) {
        }
    }

    // ==========================================================================================
    // Player creation / track loading
    // ==========================================================================================
    /** ReplayGain and other tag data, read once per track. */
    private val tagCache = HashMap<Long, TagInfo>()

    private fun tagsFor(track: AudioTrack): TagInfo? {
        if (track.isStream) return null
        synchronized(tagCache) { tagCache[track.id]?.let { return it } }
        val info = TagReader.read(context, track) ?: TagInfo()
        synchronized(tagCache) { tagCache[track.id] = info }
        return info
    }

    /** ReplayGain to apply to [track] in dB (mode + preamp, clipped by the stored peak unless the limiter is on). */
    private fun replayGainDbFor(track: AudioTrack): Float {
        if (replayGainMode == "Off" || track.isStream) return 0f
        val tags = tagsFor(track) ?: return 0f
        val album = replayGainMode == "Album Gain"
        val gain = (if (album) tags.albumGainDb ?: tags.trackGainDb else tags.trackGainDb ?: tags.albumGainDb) ?: return 0f
        var g = gain + replayGainPreampDb
        val peak = if (album) tags.albumPeak ?: tags.trackPeak else tags.trackPeak ?: tags.albumPeak
        if (!limitEnabled && peak != null && peak > 0f) g = minOf(g, -20f * kotlin.math.log10(peak))
        return g
    }

    /** Re-reads ReplayGain for the loaded players after the mode or preamp changed. */
    fun applyReplayGain() {
        val cur = currentTrack
        val mp = mediaPlayer
        if (cur != null && mp != null && currentStream == null) {
            scope.launch {
                val g = withContext(Dispatchers.IO) { replayGainDbFor(cur) }
                if (mediaPlayer === mp) { mp.trackGainDb = g; pushVolumes() }
            }
        }
        preloadNext()
    }

    /** Decides the output format for a native-engine track once its decoded rate is known. */
    private fun planOutput(sourceRate: Int, dsdRate: Int?): OutputPlan {
        val bufferMs = when {
            audioBufferSize.startsWith("Small") -> 25
            audioBufferSize.startsWith("Large") -> 100
            audioBufferSize.startsWith("Huge") -> 200
            else -> 50
        }
        if (bitPerfectEnabled) {
            val bp = bitPerfectOutput()
            if (dsdRate != null) {
                val dopRate = dsdRate / 16
                if (bp.supportsDeepFormatAt(dopRate) && bp.engage(dopRate, preferHighRes = true)) {
                    return OutputPlan(bp.activeEncoding, bitPerfect = true, dop = true, bufferMs = bufferMs)
                }
            }
            if (bp.engage(sourceRate, preferHighRes = true)) {
                return OutputPlan(bp.activeEncoding, bitPerfect = true, bufferMs = bufferMs)
            }
        }
        val enc = if (hiResAudioEnabled) AudioFormat.ENCODING_PCM_FLOAT else AudioFormat.ENCODING_PCM_16BIT
        return OutputPlan(enc, bitPerfect = false, bufferMs = bufferMs)
    }

    private fun newEngine(track: AudioTrack, native: Boolean): PlayerEngine =
        if (native) {
            NativeEngine(
                context, fxSessionId, focusAttributes, track.uri, track.filePath, track.formatExt,
                track.cueStartMs, track.cueEndMs
            ) { rate, dsd -> planOutput(rate, dsd) }
        } else {
            MediaPlayerEngine(context, fxSessionId, focusAttributes, track.uri, track.filePath, track.cueStartMs, track.cueEndMs)
        }

    /** Native engine where possible (DSD needs it), MediaPlayer for streams or when the native engine fails. */
    private fun wantsNative(track: AudioTrack) = !track.isStream && (useNativeEngine || track.isDsd)

    private fun createPreparedPlayer(track: AudioTrack): PlayerEngine? {
        val order = if (wantsNative(track)) listOf(true, false) else listOf(false)
        for (native in order) {
            if (!native && track.isDsd) break // MediaPlayer cannot decode DSD
            val e = try { newEngine(track, native) } catch (_: Exception) { null } ?: continue
            try {
                e.prepare()
                e.trackGainDb = replayGainDbFor(track)
                return e
            } catch (_: Exception) {
                try { e.release() } catch (_: Exception) {}
            }
        }
        return null
    }

    private fun releasePlayerQuietly(mp: PlayerEngine?) {
        if (mp == null) return
        mp.onCompletion = null; mp.onError = null; mp.onPrepared = null
        try { mp.stop() } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }

    private fun discardNextPlayer() {
        releasePlayerQuietly(nextPlayer)
        nextPlayer = null
        nextPlayerTrackId = -1L
        nextPrepared = false
    }

    /** Pre-buffers the upcoming track so gapless transitions and crossfades start instantly. */
    private fun preloadNext() {
        discardNextPlayer()
        if (repeatMode == RepeatMode.REPEAT_SONG || repeatMode == RepeatMode.SINGLE_SONG) return
        if (!gaplessEnabled && !crossfadeEnabled) return
        // Bit-perfect re-tunes the DAC per track, which must not happen while the current track still plays
        if (bitPerfectEnabled || currentStream != null) return
        val plan = planNext(wrap = false) ?: return
        val t = allTracks.firstOrNull { it.id == plan.trackId } ?: return
        val mp = try { newEngine(t, wantsNative(t)) } catch (_: Exception) { return }
        mp.onPrepared = { if (nextPlayer === it) nextPrepared = true }
        mp.onError = { p -> if (nextPlayer === p) discardNextPlayer() }
        nextPlayer = mp
        nextPlayerTrackId = t.id
        try { mp.prepareAsync() } catch (_: Exception) { discardNextPlayer(); return }
        scope.launch {
            val g = withContext(Dispatchers.IO) { replayGainDbFor(t) }
            if (nextPlayer === mp) mp.trackGainDb = g
        }
    }

    private fun loadAndStart(index: Int, autoPlay: Boolean, positionMs: Long = 0L, crossfade: Boolean = false) {
        val track = allTracks.getOrNull(index) ?: return
        if (currentStream != null) {
            currentStream = null
            streamTrack = null
        }
        currentTrackIndex = index
        val old = mediaPlayer
        mediaPlayer = null
        // Bit-perfect: the previous track must let go of the DAC before the next one re-tunes it
        if (bitPerfectEnabled && old != null) releasePlayerQuietly(old)

        var mp: PlayerEngine? = null
        if (nextPlayer != null && nextPlayerTrackId == track.id && nextPrepared) {
            mp = nextPlayer
            nextPlayer = null; nextPlayerTrackId = -1L; nextPrepared = false
        } else {
            discardNextPlayer()
            mp = createPreparedPlayer(track)
        }
        cancelFade()

        if (mp == null) {
            releasePlayerQuietly(old)
            isPlaying = false
            durationMs = track.durationMs
            currentPositionMs = 0L
            nativeEngineActive = false
            engineStatus = "Can't open ${track.fileName}"
            showToast("Can't play this file", track.title)
            onTrackChanged(track)
            return
        }

        attachCurrent(mp)
        durationMs = mp.duration.takeIf { it > 0 }?.toLong() ?: track.durationMs
        currentPositionMs = positionMs.coerceIn(0L, durationMs)
        rawPosMs = currentPositionMs
        rawPosAt = SystemClock.uptimeMillis()
        if (positionMs > 0L) try { mp.seekTo(positionMs.toInt()) } catch (_: Exception) {}

        if (mp.isNative) {
            // The engine already chose its output; mirror the DAC state for the UI
            syncBitPerfectState()
        } else if (bitPerfectEnabled) {
            updateBitPerfect(mp.sampleRate ?: track.sampleRateHz)
        }
        onEngineChanged()

        val previousWasPlaying = old != null && try { old.isPlaying } catch (_: Exception) { false }
        if (autoPlay) {
            if (!requestAudioFocus()) {
                releasePlayerQuietly(old)
                isPlaying = false
                showToast("Audio focus denied", "Another app is using audio")
                onTrackChanged(track)
                return
            }
            // Crossfading mixes two streams, which bit-perfect output cannot do
            val fading = crossfade && !bitPerfectActive && previousWasPlaying
            if (fading) {
                fadeInGain = 0f
                pushVolumes()
                mp.start()
                fadingOutPlayer = old
                startFade()
            } else {
                releasePlayerQuietly(old)
                fadeInGain = 1f
                pushVolumes()
                mp.start()
            }
            applyTempoNow()
            isPlaying = true
            setNoisyReceiver(true)
            incrementPlayCount(track.id)
            PlaybackService.ensureStarted(context)
        } else {
            releasePlayerQuietly(old)
            fadeInGain = 1f
            pushVolumes()
            isPlaying = false
        }
        onTrackChanged(track)
        if (autoPlay) scrobble(SCROBBLE_START)
    }

    /** Makes [mp] the current player and routes its callbacks. */
    private fun attachCurrent(mp: PlayerEngine) {
        mp.onCompletion = { p -> if (p === mediaPlayer) onTrackCompleted() }
        mp.onError = { p ->
            if (p === mediaPlayer) {
                isPlaying = false
                showToast("Playback error", currentTrack?.title)
            }
        }
        mediaPlayer = mp
    }

    /** Re-applies effects for the engine type now playing and refreshes the status line. */
    private fun onEngineChanged() {
        val mp = mediaPlayer
        nativeEngineActive = mp?.isNative == true
        engineStatus = mp?.description ?: "Idle"
        applyAllEffectsNow()
        updateDvc()
    }

    private fun syncBitPerfectState() {
        val out = bitPerfectOut
        if (bitPerfectEnabled && out != null) {
            bitPerfectActive = out.state == BitPerfectOutput.State.ACTIVE
            bitPerfectStatus = out.statusText
        } else if (bitPerfectEnabled) {
            bitPerfectActive = false
            bitPerfectStatus = "Waiting for a USB DAC"
        } else {
            bitPerfectActive = false
            bitPerfectStatus = "Off"
        }
    }

    // ----- Internet radio streams (always through MediaPlayer, which handles buffering and ICY) -----
    /** Synthetic track describing the playing stream, so the player UI, notification and widget keep working. */
    private var streamTrack by mutableStateOf<AudioTrack?>(null)

    private fun startStreamPlayback(stream: StreamItem) {
        val uri = Uri.parse(stream.url)
        val t = AudioTrack(
            id = -1_000_000L - stream.id.mod(1_000_000L), title = stream.title, artist = stream.genre,
            album = "Radio", durationMs = 0L, uri = uri, filePath = "", formatExt = "stream",
            waveformBars = NEUTRAL_WAVEFORM
        )
        cancelFade()
        discardNextPlayer()
        releasePlayerQuietly(mediaPlayer)
        mediaPlayer = null
        currentStream = stream
        streamTrack = t
        if (bitPerfectEnabled) { bitPerfectOut?.release(); syncBitPerfectState() }
        val mp = MediaPlayerEngine(context, fxSessionId, focusAttributes, uri, "")
        attachCurrent(mp)
        mp.onCompletion = { p -> if (p === mediaPlayer) { isPlaying = false; showToast("Stream ended", stream.title) } }
        mp.onError = { p ->
            if (p === mediaPlayer) {
                isPlaying = false
                engineStatus = "Stream error"
                showToast("Can't play stream", stream.title)
            }
        }
        mp.onPrepared = { p ->
            if (p === mediaPlayer && currentStream?.id == stream.id) {
                if (requestAudioFocus()) {
                    fadeInGain = 1f
                    pushVolumes()
                    p.start()
                    isPlaying = true
                    setNoisyReceiver(true)
                    onEngineChanged()
                    engineStatus = "Internet stream · Android MediaPlayer"
                    PlaybackService.ensureStarted(context)
                    scrobble(SCROBBLE_START)
                }
                playbackEpoch++
            }
        }
        durationMs = 0L
        currentPositionMs = 0L
        isPlaying = false
        engineStatus = "Connecting to ${uri.host ?: "stream"}…"
        playbackEpoch++
        try {
            mp.prepareAsync()
            showToast("Connecting", stream.title)
        } catch (_: Exception) {
            showToast("Can't play stream", stream.title)
        }
    }

    private fun onTrackChanged(track: AudioTrack) {
        playbackEpoch++
        pendingLastTrackId = -1L
        prefs.edit().putLong("lastTrackId", track.id).apply()
        probeAndAnalyse(track)
        preloadNext()
    }

    private fun startFade() {
        if (fadingOutPlayer == null) return
        val total = (crossfadeDurationSec * 1000f).toLong().coerceAtLeast(300L)
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val start = SystemClock.uptimeMillis()
            while (isActive) {
                val f = ((SystemClock.uptimeMillis() - start).toFloat() / total).coerceIn(0f, 1f)
                fadeInGain = f
                pushVolumes()
                if (f >= 1f) break
                delay(30L)
            }
            releasePlayerQuietly(fadingOutPlayer)
            fadingOutPlayer = null
            fadeInGain = 1f
            pushVolumes()
        }
    }

    private fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
        releasePlayerQuietly(fadingOutPlayer)
        fadingOutPlayer = null
        fadeInGain = 1f
    }

    // ==========================================================================================
    // Real audio effects (one effect chain on a fixed audio session shared by every player)
    // ==========================================================================================
    private fun setupAudioEffects() {
        val s = fxSessionId
        try { equalizerFx = Equalizer(0, s) } catch (_: Exception) {}
        try { virtualizerFx = Virtualizer(0, s) } catch (_: Exception) {}
        try { presetReverbFx = PresetReverb(0, s) } catch (_: Exception) {}
        try { loudnessFx = LoudnessEnhancer(s) } catch (_: Exception) {}
        applyAllEffectsNow()
    }

    private val uiHandler = Handler(Looper.getMainLooper())
    private var effectsPending = false
    private val effectsRunnable = Runnable {
        effectsPending = false
        applyAllEffectsNow()
    }

    /** Knobs call these at touch rate; we coalesce them so the audio framework is hit at most ~40x/second. */
    private fun scheduleEffects() {
        if (!effectsPending) {
            effectsPending = true
            uiHandler.postDelayed(effectsRunnable, 24L)
        }
    }

    fun applyEqualizerBands() = scheduleEffects()
    fun applyToneAndEffects() = scheduleEffects()
    fun applyVolumeAndBalance() = scheduleEffects()
    fun applyTempo() = scheduleEffects()

    private val uiBandHz = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)

    /** Interpolated (log-frequency) gain in dB at [hz], from the 10 UI bands plus bass/treble shelves. */
    private fun gainDbAt(hz: Float): Float {
        var g = 0f
        if (eqEnabled) {
            val x = ln(hz.coerceIn(uiBandHz.first(), uiBandHz.last()) / uiBandHz.first()) / ln(2f)
            val lo = x.toInt().coerceIn(0, uiBandHz.size - 2)
            val frac = (x - lo).coerceIn(0f, 1f)
            g += bandGainsDb[lo] * (1f - frac) + bandGainsDb[lo + 1] * frac
        }
        if (toneEnabled) {
            val bassShelf = (1f - ((ln(hz.coerceAtLeast(20f) / 150f) / ln(2f)) + 1f) / 2f).coerceIn(0f, 1f) // full <150Hz, fades by ~600Hz
            val trebleShelf = ((ln(hz.coerceAtLeast(20f) / 2000f) / ln(2f)) + 1f).coerceIn(0f, 1f) // fades in 1k..2k+
            g += bassPercent / 100f * 10f * bassShelf
            g += treblePercent / 100f * 10f * trebleShelf
        }
        return g
    }

    private fun maxBoostDb(): Float {
        var m = 0f
        for (hz in uiBandHz) m = maxOf(m, gainDbAt(hz))
        return m
    }

    private fun applyAllEffectsNow() {
        if (bitPerfectActive) {
            // Bit-perfect: every DSP stage and software gain is bypassed so samples reach the DAC untouched
            for (fx in listOf(equalizerFx, loudnessFx, virtualizerFx, presetReverbFx)) {
                try { fx?.enabled = false } catch (_: Exception) {}
            }
            pushVolumes()
            applyTempoNow()
            applyDeviceVolume()
            return
        }
        DspHolder.config = buildDspConfig()
        if (mediaPlayer?.isNative == true) {
            // The native engine runs its own DSP chain; the session-wide Android effects would double it up
            for (fx in listOf(equalizerFx, loudnessFx, virtualizerFx, presetReverbFx)) {
                try { fx?.enabled = false } catch (_: Exception) {}
            }
            pushVolumes()
            applyTempoNow()
            applyDeviceVolume()
            return
        }
        // --- Equalizer (hardware bands, interpolated from the 10-band UI) ---
        try {
            val eq = equalizerFx
            if (eq != null) {
                eq.enabled = eqEnabled || toneEnabled
                val range = eq.bandLevelRange
                for (b in 0 until eq.numberOfBands.toInt()) {
                    val centerHz = eq.getCenterFreq(b.toShort()) / 1000f
                    val mb = (gainDbAt(centerHz) * 100f).toInt().coerceIn(range[0].toInt(), range[1].toInt())
                    eq.setBandLevel(b.toShort(), mb.toShort())
                }
            }
        } catch (_: Exception) {
        }
        // --- Preamp: positive gain via LoudnessEnhancer, negative via player volume (see baseGains) ---
        try {
            val le = loudnessFx
            if (le != null) {
                val boost = if (eqEnabled) preampDb.coerceAtLeast(0f) else 0f
                le.setTargetGain((boost * 100f).toInt())
                le.enabled = boost > 0.05f
            }
        } catch (_: Exception) {
        }
        // --- Stereo expand ---
        try {
            val v = virtualizerFx
            if (v != null) {
                if (v.strengthSupported) v.setStrength((stereoExpandPercent * 10).coerceIn(0, 1000).toShort())
                v.enabled = stereoExpandPercent > 0
            }
        } catch (_: Exception) {
        }
        // --- Reverb: size picks the room model, mix gates it ---
        try {
            val r = presetReverbFx
            if (r != null) {
                val on = reverbEnabled && reverbMix > 0.02f
                if (on) {
                    r.preset = when {
                        selectedReverbPreset.contains("Plate", true) -> PresetReverb.PRESET_PLATE
                        reverbSize < 0.20f -> PresetReverb.PRESET_SMALLROOM
                        reverbSize < 0.45f -> PresetReverb.PRESET_MEDIUMROOM
                        reverbSize < 0.70f -> PresetReverb.PRESET_LARGEROOM
                        reverbSize < 0.85f -> PresetReverb.PRESET_MEDIUMHALL
                        else -> PresetReverb.PRESET_LARGEHALL
                    }
                }
                r.enabled = on
            }
        } catch (_: Exception) {
        }
        pushVolumes()
        applyTempoNow()
        applyDeviceVolume()
    }

    private fun buildDspConfig() = DspConfig(
        bypass = bitPerfectActive,
        preampDb = if (eqEnabled) preampDb else 0f,
        graphicEnabled = eqEnabled && !parametricEqEnabled,
        graphicGainsDb = bandGainsDb.toList(),
        parametricEnabled = eqEnabled && parametricEqEnabled,
        parametricBands = parametricBands.toList(),
        bassDb = if (toneEnabled) bassPercent / 100f * 12f else 0f,
        trebleDb = if (toneEnabled) treblePercent / 100f * 12f else 0f,
        limiter = limitEnabled,
        mono = monoEnabled,
        stereoWidth = 1f + stereoExpandPercent / 100f,
        reverb = if (reverbEnabled && reverbMix > 0.02f) {
            ReverbParams(reverbSize, reverbDamp, reverbFilter, reverbFade, reverbPreDelay, reverbPreDelayMix, reverbMix)
        } else null
    )

    // ----- Direct Volume Control -----
    private fun outputIsBluetooth(): Boolean = try {
        val bt = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
            AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER, AudioDeviceInfo.TYPE_BLE_BROADCAST
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioManager.getAudioDevicesForAttributes(focusAttributes).any { it.type in bt }
        } else {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }
        }
    } catch (_: Exception) {
        false
    }

    /**
     * Turns DVC on or off for the current output. Order matters for hearing safety: when switching on, the
     * software gain drops first and only then the system volume goes to max; when switching off, the system
     * volume comes down first and only then the software gain returns to unity.
     */
    fun updateDvc() {
        val want = dvcEnabled && !bitPerfectActive && (!outputIsBluetooth() || bluetoothDvcEnabled)
        if (want == dvcActive) {
            pushVolumes()
            applyDeviceVolume()
            return
        }
        dvcActive = want
        if (want) {
            pushVolumes()
            applyDeviceVolume()
        } else {
            applyDeviceVolume()
            pushVolumes()
        }
    }

    /** Software volume while DVC is active: a power curve so the knob feels even (50% ≈ -15 dB). */
    private fun dvcGain(): Float = if (!dvcActive) 1f else (volumePercent / 100f).coerceIn(0f, 1f).pow(2.5f)

    private fun applyDeviceVolume() {
        try {
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val target = if (dvcActive) maxVol
                else ((volumePercent / 100f) * maxVol).let { kotlin.math.round(it).toInt() }.coerceIn(0, maxVol)
            if (target != lastStreamVolumeSet) {
                lastVolumeApplyAt = SystemClock.uptimeMillis()
                lastStreamVolumeSet = target
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun baseGains(): Pair<Float, Float> {
        if (bitPerfectActive) return Pair(duckGain, duckGain) // unity gain; ducking only while another app speaks
        var db = 0f
        if (mediaPlayer?.isNative != true) {
            // MediaPlayer path: negative preamp attenuates; with the limiter on, boosted EQ bands get headroom.
            // (The native engine applies preamp and limiting inside its own DSP.)
            db = if (eqEnabled) preampDb.coerceAtMost(0f) else 0f
            if (limitEnabled) db -= (maxBoostDb() + (if (eqEnabled) preampDb.coerceAtLeast(0f) else 0f)).coerceAtLeast(0f)
        }
        val scale = 10.0.pow(db.toDouble() / 20.0).toFloat().coerceIn(0.05f, 1f) * duckGain * dvcGain()
        val l = if (balanceValue > 0f) (1f - balanceValue).coerceIn(0f, 1f) else 1f
        val r = if (balanceValue < 0f) (1f + balanceValue).coerceIn(0f, 1f) else 1f
        return Pair(scale * l, scale * r)
    }

    private fun pushVolumes() {
        val (l, r) = baseGains()
        val inGain = sqrt(fadeInGain)
        val outGain = sqrt(1f - fadeInGain)
        try { mediaPlayer?.setVolume((l * inGain).coerceIn(0f, 1f), (r * inGain).coerceIn(0f, 1f)) } catch (_: Exception) {}
        try { fadingOutPlayer?.setVolume((l * outGain).coerceIn(0f, 1f), (r * outGain).coerceIn(0f, 1f)) } catch (_: Exception) {}
    }

    private fun applyTempoNow() {
        val speed = if (tempoEnabled && !bitPerfectActive) tempoMultiplier.coerceIn(0.5f, 3.0f) else 1.0f
        playbackSpeed = speed
        for (mp in listOf(mediaPlayer, fadingOutPlayer)) {
            try {
                if (mp != null && mp.isPlaying) mp.setSpeed(speed)
            } catch (_: Exception) {
            }
        }
    }

    // ==========================================================================================
    // Progress loop (auto-adaptive refresh rate + smooth position + crossfade trigger + spectrum)
    // ==========================================================================================
    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastTickMs = SystemClock.uptimeMillis()
            while (isActive) {
                val now = SystemClock.uptimeMillis()
                val dtSec = ((now - lastTickMs) / 1000f).coerceIn(0.004f, 0.1f)
                lastTickMs = now
                val mp = mediaPlayer
                if (isPlaying && mp != null) {
                    if (!isScrubbing && !isSeekingFwd && !isSeekingRwd) {
                        val pos = try { mp.currentPosition.toLong() } catch (_: Exception) { rawPosMs }
                        if (pos != rawPosMs) {
                            rawPosMs = pos
                            rawPosAt = now
                        }
                        // MediaPlayer reports position in coarse steps; interpolate smoothly at native refresh rate
                        val extrapolated = ((now - rawPosAt).coerceAtMost(120L) * playbackSpeed).toLong()
                        currentPositionMs = (rawPosMs + extrapolated).coerceIn(0L, durationMs)
                    }
                    checkCrossfadeTrigger()
                    if (now - lastPositionSaveAt > 5000L) savePosition()
                    val needsSpectrum = visMode != VisMode.DISABLED || currentMainTab == MainNavTab.EQUALIZER
                    if (needsSpectrum) {
                        updateSpectrum(active = true, dtSec = dtSec)
                    }
                    // Auto-adapt loop cadence to the device's live display refresh rate (60/90/120/144Hz)
                    val hz = displayRefreshRateHz.coerceIn(30f, 240f)
                    val frameDelayMs = (1000f / hz).toLong().coerceIn(4L, 33L)
                    delay(frameDelayMs)
                } else {
                    val needsSpectrum = visMode != VisMode.DISABLED || currentMainTab == MainNavTab.EQUALIZER
                    if (needsSpectrum) {
                        updateSpectrum(active = false, dtSec = dtSec)
                    }
                    delay(60L)
                }
            }
        }
    }

    private fun checkCrossfadeTrigger() {
        if (!crossfadeEnabled || bitPerfectActive || crossfadeDurationSec < 0.3f || fadingOutPlayer != null || stopAfterCurrentTrack) return
        if (repeatMode == RepeatMode.REPEAT_SONG || repeatMode == RepeatMode.SINGLE_SONG) return
        val fadeMs = (crossfadeDurationSec * 1000f).toLong()
        if (durationMs < fadeMs * 2 + 1000L) return
        val remaining = (durationMs - currentPositionMs) / playbackSpeed
        if (remaining <= fadeMs) {
            val plan = planNext(wrap = false) ?: return
            advance(plan, crossfade = true)
        }
    }

    // ----- Real FFT visualizer (falls back to the synthetic animation if permission is missing) -----
    fun refreshVisualizer() {
        if (visMode == VisMode.DISABLED) {
            releaseVisualizer()
            return
        }
        if (visualizerFx != null) return
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            recordPermissionRequester?.invoke()
            return
        }
        try {
            val v = Visualizer(fxSessionId)
            v.enabled = false
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    if (fft != null) handleFft(fft)
                }
            }, Visualizer.getMaxCaptureRate() / 2, false, true)
            v.enabled = true
            visualizerFx = v
        } catch (_: Exception) {
            visualizerFx = null
        }
    }

    private fun releaseVisualizer() {
        try { visualizerFx?.enabled = false } catch (_: Exception) {}
        try { visualizerFx?.release() } catch (_: Exception) {}
        visualizerFx = null
        fftFresh = false
    }

    /** Runs on the Visualizer thread: maps FFT bins to 48 log-spaced bars. */
    private fun handleFft(fft: ByteArray) {
        val bins = fft.size / 2 - 1
        if (bins < 8) return
        val out = FloatArray(48)
        for (b in out.indices) {
            val lo = (kotlin.math.exp(ln(bins.toFloat()) * b / out.size)).toInt().coerceIn(1, bins)
            val hi = (kotlin.math.exp(ln(bins.toFloat()) * (b + 1) / out.size)).toInt().coerceIn(lo, bins)
            var peak = 0f
            for (k in lo..hi) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                val mag = kotlin.math.hypot(re, im)
                if (mag > peak) peak = mag
            }
            val db = 20f * kotlin.math.log10(1f + peak)
            out[b] = ((db - 6f) / 32f).coerceIn(0f, 1f)
        }
        fftBars = out
        fftFresh = true
    }

    private fun updateSpectrum(active: Boolean, dtSec: Float = 1f / 60f) {
        // Normalize smoothing & phase step to 60fps baseline so animation speed is identical at 60/90/120/144Hz
        val step60 = (dtSec * 60f).coerceIn(0.25f, 3f)
        if (active && visualizerFx != null && fftFresh) {
            visPhase = (visPhase + 0.085f * step60) % (20f * Math.PI.toFloat())
            val target = fftBars
            val rise = (1f - (1f - 0.7f).pow(step60)).coerceIn(0.1f, 0.95f)
            val fall = 0.86f.pow(step60)
            for (i in 0 until liveSpectrumAmplitudes.size) {
                val t = target[i.coerceIn(0, target.lastIndex)].coerceAtLeast(0.03f)
                val c = liveSpectrumAmplitudes[i]
                liveSpectrumAmplitudes[i] = if (t > c) c + (t - c) * rise else (c * fall).coerceAtLeast(0.03f)
            }
        } else if (active) {
            visPhase = (visPhase + 0.085f * step60) % (20f * Math.PI.toFloat())
            val bars = currentTrack?.waveformBars ?: emptyList()
            val posIdx = if (bars.isNotEmpty()) (progressFraction * bars.lastIndex).toInt().coerceIn(0, bars.lastIndex) else 0
            val trackEnvelope = if (bars.isNotEmpty()) bars[posIdx] else 0.7f
            val rise = (1f - (1f - 0.65f).pow(step60)).coerceIn(0.1f, 0.95f)
            val fall = 0.84f.pow(step60)
            for (i in 0 until liveSpectrumAmplitudes.size) {
                val freqFactor = 1f - (i.toFloat() / liveSpectrumAmplitudes.size) * 0.35f
                val wave1 = sin(visPhase * 1.9f + i * 0.42f) * 0.5f + 0.5f
                val wave2 = cos(visPhase * 3.1f - i * 0.67f) * 0.5f + 0.5f
                val beatPulse = (sin(visPhase * 1.15f) * 0.5f + 0.5f).pow(2)
                val target = ((wave1 * 0.48f + wave2 * 0.32f + beatPulse * 0.20f) * trackEnvelope * freqFactor).coerceIn(0.05f, 1.0f)
                val current = liveSpectrumAmplitudes[i]
                liveSpectrumAmplitudes[i] = if (target > current) current + (target - current) * rise else (current * fall).coerceAtLeast(0.03f)
            }
        } else {
            val decay = 0.78f.pow(step60)
            for (i in 0 until liveSpectrumAmplitudes.size) {
                val v = liveSpectrumAmplitudes[i]
                if (v > 0.005f) liveSpectrumAmplitudes[i] = (v * decay).coerceAtLeast(0f)
                else if (v != 0f) liveSpectrumAmplitudes[i] = 0f
            }
        }
    }

    // ==========================================================================================
    // Transport
    // ==========================================================================================
    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun play() {
        pausedByHeadsetDisconnect = false
        val stream = currentStream
        if (stream != null && mediaPlayer == null) { startStreamPlayback(stream); return }
        if (allTracks.isEmpty() && stream == null) return
        val mp = mediaPlayer
        if (mp == null) {
            loadAndStart(currentTrackIndex, autoPlay = true)
            return
        }
        if (!requestAudioFocus()) {
            showToast("Audio focus denied", "Another app is using audio")
            return
        }
        try {
            mp.start()
            applyTempoNow()
            isPlaying = true
            rawPosMs = try { mp.currentPosition.toLong() } catch (_: Exception) { currentPositionMs }
            rawPosAt = SystemClock.uptimeMillis()
            setNoisyReceiver(true)
            currentTrack?.let { incrementPlayCount(it.id) }
            PlaybackService.ensureStarted(context)
            playbackEpoch++
            scrobble(SCROBBLE_RESUME)
        } catch (_: Exception) {
            loadAndStart(currentTrackIndex, autoPlay = true, positionMs = currentPositionMs)
        }
    }

    fun pause(abandonFocus: Boolean = true) {
        try { mediaPlayer?.pause() } catch (_: Exception) {}
        if (fadingOutPlayer != null) {
            // Finish the crossfade instantly instead of leaving a half-faded player behind
            cancelFade()
            pushVolumes()
        }
        isPlaying = false
        setNoisyReceiver(false)
        if (abandonFocus) abandonAudioFocus()
        savePosition()
        scrobble(SCROBBLE_PAUSE)
        playbackEpoch++
    }

    fun stopPlayback() {
        pause()
        seekToMs(0L)
    }

    fun playTrack(track: AudioTrack, context: List<AudioTrack>? = null) {
        val idx = allTracks.indexOfFirst { it.id == track.id }
        if (idx < 0) return
        playbackListIds = if (!context.isNullOrEmpty()) context.map { it.id } else emptyList()
        if (currentTrack?.id != track.id) currentTrack?.let { pushHistory(it.id) }
        trackTransitionDirection = if (idx >= currentTrackIndex) 1 else -1
        loadAndStart(idx, autoPlay = true)
    }

    /** Updates displayed position immediately during smooth slider drag without flooding MediaPlayer.seekTo(). */
    fun previewScrubFraction(fraction: Float) {
        val clamped = (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
        currentPositionMs = clamped
    }

    fun seekToFraction(fraction: Float) {
        seekToMs((fraction.coerceIn(0f, 1f) * durationMs).toLong())
    }

    fun seekToMs(targetMs: Long) {
        val clamped = targetMs.coerceIn(0L, durationMs)
        currentPositionMs = clamped
        rawPosMs = clamped
        rawPosAt = SystemClock.uptimeMillis()
        try {
            mediaPlayer?.seekTo(clamped.toInt())
        } catch (_: Exception) {
        }
        playbackEpoch++
    }

    /** Relative seeking used by Next / Previous press-and-hold. */
    fun seekRelative(deltaMs: Long) {
        seekToMs((currentPositionMs + deltaMs).coerceIn(0L, durationMs.coerceAtLeast(1L)))
    }

    fun previousTrackOrRestart() {
        if (currentPositionMs > 3000L) {
            seekToMs(0L)
            showToast("Rewind", "Track restarted")
        } else {
            previousTrack()
        }
    }

    private fun savePosition() {
        lastPositionSaveAt = SystemClock.uptimeMillis()
        prefs.edit().putLong("lastPositionMs", currentPositionMs).apply()
    }

    fun onAppStopped() {
        savePosition()
        val json = buildStateJson()
        scope.launch(Dispatchers.IO) { prefs.edit().putString("state", json).commit() }
    }

    fun resetSettingsToDefault() {
        skinTheme = "Poweramp"
        accentColor = POWERAMP_GREEN
        settingsTheme = "Default"
        settingsFont = "Bold"
        smoothSeekbarAnimation = true
        playPauseMorphAnimation = true
        trackSlideTransitions = true
        showStatusBarOverlay = true
        showRatingPill = true
        showMetaLine = true
        filenameAsTitle = false
        crossfadeEnabled = true
        crossfadeDurationSec = 2.5f
        gaplessEnabled = true
        replayGainMode = "Track Gain"
        replayGainPreampDb = 0.0f
        dvcEnabled = true
        hiResAudioEnabled = true
        audioBufferSize = "Normal (50ms)"
        setBitPerfect(false)
        pauseOnHeadsetDisconnect = true
        resumeOnHeadsetConnect = true
        visFadedControlsOpacity = 0.40f
        visPresetDurationSec = 10
        visFpsTarget = 60
        visShowPresetToast = true
        backgroundType = "Pure Black (OLED)"
        backgroundBlurRadius = 0.65f
        backgroundSaturation = 1.0f
        backgroundDimOpacity = 0.35f
        autoDownloadAlbumArt = false
        highResAlbumArt = true
        preferEmbeddedArt = true
        albumArtOnLockscreen = true
        autoScanOnStartup = true
        ignoreShortTracks = true
        headsetButtonsEnabled = true
        bluetoothDvcEnabled = true
        androidAutoEnabled = true
        scrobblingEnabled = false
        showToast("Settings Reset", "Default configuration restored")
    }

    // ==========================================================================================
    // Next / previous / repeat / shuffle
    // ==========================================================================================
    private data class NextPlan(val trackId: Long, val fromQueue: Boolean)

    /** The ordered list playback walks through: the list the user started from, else the whole library. */
    private fun currentList(): List<AudioTrack> {
        if (playbackListIds.isEmpty()) return allTracks.toList()
        val byId = allTracks.associateBy { it.id }
        val ctx = playbackListIds.mapNotNull { byId[it] }
        return ctx.ifEmpty { allTracks.toList() }
    }

    private fun planNext(wrap: Boolean): NextPlan? {
        queueTracks.firstOrNull { q -> allTracks.any { it.id == q.id } }?.let { return NextPlan(it.id, true) }
        val list = currentList()
        if (list.isEmpty()) return null
        val curId = currentTrack?.id ?: -1L
        val pos = list.indexOfFirst { it.id == curId }
        if (shuffleMode != ShuffleMode.OFF && list.size > 1) {
            if (shuffleFromId == curId && shuffleNextId != -1L && list.any { it.id == shuffleNextId }) {
                return NextPlan(shuffleNextId, false)
            }
            val pick = list.filter { it.id != curId }.random()
            shuffleFromId = curId
            shuffleNextId = pick.id
            return NextPlan(pick.id, false)
        }
        if (pos in 0 until list.lastIndex) return NextPlan(list[pos + 1].id, false)
        val loops = wrap || repeatMode == RepeatMode.REPEAT_LIST ||
            repeatMode == RepeatMode.REPEAT_CATEGORY || repeatMode == RepeatMode.ADVANCE_CATEGORY
        return if (loops) NextPlan(list.first().id, false) else null
    }

    private fun pushHistory(id: Long) {
        if (history.lastOrNull() != id) history.addLast(id)
        while (history.size > 200) history.removeFirst()
    }

    private fun advance(plan: NextPlan, crossfade: Boolean = false) {
        val idx = allTracks.indexOfFirst { it.id == plan.trackId }
        if (idx < 0) return
        currentTrack?.id?.let { pushHistory(it) }
        if (plan.fromQueue) {
            val q = queueTracks.indexOfFirst { it.id == plan.trackId }
            if (q >= 0) queueTracks.removeAt(q)
        }
        shuffleNextId = -1L
        trackTransitionDirection = 1
        loadAndStart(idx, autoPlay = true, crossfade = crossfade)
    }

    fun nextTrack() {
        if (allTracks.isEmpty()) return
        val plan = planNext(wrap = true) ?: return
        advance(plan, crossfade = false)
    }

    fun previousTrack() {
        if (allTracks.isEmpty()) return
        if (currentPositionMs > 4000L) {
            seekToMs(0L)
            return
        }
        trackTransitionDirection = -1
        // Walk back through what was actually played (essential when shuffling)
        while (history.isNotEmpty()) {
            val id = history.removeLast()
            val idx = allTracks.indexOfFirst { it.id == id }
            if (idx >= 0 && id != currentTrack?.id) {
                shuffleNextId = -1L
                loadAndStart(idx, autoPlay = true)
                return
            }
        }
        val list = currentList()
        val pos = list.indexOfFirst { it.id == currentTrack?.id }
        val prev = if (pos <= 0) list.last() else list[pos - 1]
        val idx = allTracks.indexOfFirst { it.id == prev.id }
        if (idx >= 0) loadAndStart(idx, autoPlay = true)
    }

    /** The track that will play next (used for the artwork peek card). */
    fun peekNext(): AudioTrack? {
        val plan = planNext(wrap = true) ?: return null
        return allTracks.firstOrNull { it.id == plan.trackId }
    }

    /** The track that Previous will go to (used for the artwork peek card). */
    fun peekPrevious(): AudioTrack? {
        history.lastOrNull()?.let { id -> allTracks.firstOrNull { it.id == id && it.id != currentTrack?.id }?.let { return it } }
        val list = currentList()
        if (list.isEmpty()) return null
        val pos = list.indexOfFirst { it.id == currentTrack?.id }
        return if (pos <= 0) list.last() else list[pos - 1]
    }

    /** Jump to the first track of the previous album in the current list. */
    fun prevCategory() {
        val list = currentList()
        val pos = list.indexOfFirst { it.id == currentTrack?.id }
        if (pos < 0) return previousTrack()
        val curAlbum = list[pos].album
        var start = pos
        while (start > 0 && list[start - 1].album == curAlbum) start--
        val target = if (start == 0) list.last().let { last -> list.first { it.album == last.album } } else {
            val prevAlbum = list[start - 1].album
            var s = start - 1
            while (s > 0 && list[s - 1].album == prevAlbum) s--
            list[s]
        }
        showToast("Previous Album", target.album)
        playTrack(target, list)
    }

    /** Jump to the first track of the next album in the current list. */
    fun nextCategory() {
        val list = currentList()
        val pos = list.indexOfFirst { it.id == currentTrack?.id }
        if (pos < 0) return nextTrack()
        val curAlbum = list[pos].album
        val target = (pos + 1 until list.size).firstOrNull { list[it].album != curAlbum }?.let { list[it] } ?: list.first()
        showToast("Next Album", target.album)
        playTrack(target, list)
    }

    private fun onTrackCompleted() {
        scrobble(SCROBBLE_COMPLETE)
        if (stopAfterCurrentTrack) {
            stopAfterCurrentTrack = false
            pause()
            seekToMs(0L)
            showToast("Sleep Timer", "Playback stopped")
            return
        }
        when (repeatMode) {
            RepeatMode.SINGLE_SONG -> {
                pause()
                seekToMs(0L)
            }
            RepeatMode.REPEAT_SONG -> {
                seekToMs(0L)
                try { mediaPlayer?.start() } catch (_: Exception) {}
                applyTempoNow()
                isPlaying = true
            }
            else -> {
                val plan = planNext(wrap = false)
                if (plan == null) {
                    pause()
                    seekToMs(0L)
                    showToast("End of list")
                } else {
                    advance(plan)
                }
            }
        }
    }

    fun cycleRepeatMode() {
        val modes = RepeatMode.entries
        repeatMode = modes[(repeatMode.ordinal + 1) % modes.size]
        preloadNext()
        showToast(repeatMode.label, "Long press for more options")
    }

    fun cycleShuffleMode() {
        val modes = ShuffleMode.entries
        shuffleMode = modes[(shuffleMode.ordinal + 1) % modes.size]
        shuffleNextId = -1L
        preloadNext()
        showToast(shuffleMode.label, "Long press for more options")
    }

    fun cycleVisMode() {
        val modes = VisMode.entries
        visMode = modes[(visMode.ordinal + 1) % modes.size]
        showVisPresetBar = visMode != VisMode.DISABLED
        refreshVisualizer()
        showToast(visMode.label, "Long press for more options")
    }

    fun nextVisPreset() {
        currentVisPresetIndex = (currentVisPresetIndex + 1) % visPresets.size
        if (visShowPresetToast) showToast("Preset: ${currentVisPreset.name}")
    }

    fun prevVisPreset() {
        currentVisPresetIndex = if (currentVisPresetIndex - 1 < 0) visPresets.lastIndex else currentVisPresetIndex - 1
        if (visShowPresetToast) showToast("Preset: ${currentVisPreset.name}")
    }

    fun cycleVisPresetMode() {
        val modes = listOf("Locked", "Shuffle", "Cycle")
        val nextIdx = (modes.indexOf(visPresetCycleMode) + 1) % modes.size
        visPresetCycleMode = modes[nextIdx]
        showToast("Visualization Preset Mode: $visPresetCycleMode")
    }

    fun toggleLikeCurrentTrack() {
        val track = currentTrack ?: return
        val newRating = if (track.rating == 1) 0 else 1
        updateTrack(track.copy(rating = newRating))
        showToast(if (newRating == 1) "Liked" else "Rating Cleared", track.title)
    }

    fun toggleUnlikeCurrentTrack() {
        val track = currentTrack ?: return
        val newRating = if (track.rating == -1) 0 else -1
        updateTrack(track.copy(rating = newRating))
        showToast(if (newRating == -1) "Unliked" else "Rating Cleared", track.title)
    }

    fun updateTrack(updated: AudioTrack) {
        val idx = allTracks.indexOfFirst { it.id == updated.id }
        if (idx >= 0) {
            val old = allTracks[idx]
            if (old.title != updated.title || old.artist != updated.artist || old.album != updated.album ||
                old.albumArtist != updated.albumArtist || old.genre != updated.genre ||
                old.year != updated.year || old.composer != updated.composer
            ) {
                editedTrackIds.add(updated.id)
            }
            // A manual title edit becomes the new tag title (unless titles are being shown from file names)
            allTracks[idx] = if (!filenameAsTitle && old.title != updated.title) updated.copy(tagTitle = updated.title) else updated
        }
        if (actionTargetTrack?.id == updated.id) {
            actionTargetTrack = updated
        }
    }

    private fun incrementPlayCount(trackId: Long) {
        val idx = allTracks.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            val t = allTracks[idx]
            allTracks[idx] = t.copy(
                playCount = t.playCount + 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        }
    }

    fun addBookmarkForTrack(track: AudioTrack) {
        val pos = if (currentTrack?.id == track.id) currentPositionMs else 0L
        bookmarks.add(
            0,
            BookmarkItem(
                id = System.currentTimeMillis(),
                trackId = track.id,
                trackTitle = track.title,
                artist = track.artist,
                positionMs = pos
            )
        )
        showToast("Bookmark Added", "${track.title} @ ${bookmarks.first().formattedPosition}")
    }

    fun addToQueue(track: AudioTrack) {
        queueTracks.add(track)
        if (queueTracks.size == 1) preloadNext()
        showToast("Added to Queue", track.title)
    }

    fun addTrackToPlaylist(track: AudioTrack, playlistId: Long) {
        val idx = playlists.indexOfFirst { it.id == playlistId }
        if (idx >= 0) {
            val pl = playlists[idx]
            if (track.id !in pl.trackIds) {
                playlists[idx] = pl.copy(trackIds = pl.trackIds + track.id)
            }
            showToast("Added to ${pl.name}", track.title)
        }
    }

    fun createPlaylist(name: String, initialTrack: AudioTrack? = null) {
        if (name.isBlank()) return
        val newPl = PlaylistItem(
            id = System.currentTimeMillis(),
            name = name.trim(),
            trackIds = if (initialTrack != null) listOf(initialTrack.id) else emptyList()
        )
        playlists.add(newPl)
        showToast("Playlist Created", newPl.name)
    }

    /** Opens the system share sheet with the audio file (the user chooses the destination). */
    fun shareTrack(track: AudioTrack) {
        val uri = track.uri ?: run {
            showToast("Can't share", "File not available")
            return
        }
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, track.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share ${track.title}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            showToast("Can't share", track.title)
        }
    }

    /** Removes the track from the library. The audio file itself is never deleted from storage. */
    fun deleteTrack(track: AudioTrack) {
        val wasCurrent = currentTrack?.id == track.id
        val wasPlaying = isPlaying
        hiddenTrackIds.add(track.id)
        allTracks.removeAll { it.id == track.id }
        queueTracks.removeAll { it.id == track.id }
        bookmarks.removeAll { it.trackId == track.id }
        playlists.indices.forEach { i ->
            val pl = playlists[i]
            if (track.id in pl.trackIds) playlists[i] = pl.copy(trackIds = pl.trackIds - track.id)
        }
        if (wasCurrent) {
            releasePlayerQuietly(mediaPlayer)
            mediaPlayer = null
            if (allTracks.isEmpty()) {
                isPlaying = false
                durationMs = 0L
                currentPositionMs = 0L
            } else {
                currentTrackIndex = currentTrackIndex.coerceIn(0, allTracks.lastIndex)
                loadAndStart(currentTrackIndex, autoPlay = wasPlaying)
            }
        } else if (currentTrackIndex >= allTracks.size) {
            currentTrackIndex = allTracks.lastIndex.coerceAtLeast(0)
        }
        showToast("Removed from library", track.title)
    }

    // Sleep Timer Control
    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        sleepTimerMinutes = minutes.coerceAtLeast(0)
        stopAfterCurrentTrack = false
        if (minutes <= 0) {
            sleepTimerRemainingSeconds = 0
            showToast("Sleep Timer: disabled")
            return
        }
        sleepTimerRemainingSeconds = minutes * 60
        showToast("Sleep Timer: $minutes min.")
        sleepTimerJob = scope.launch {
            while (sleepTimerRemainingSeconds > 0 && isActive) {
                delay(1000L)
                sleepTimerRemainingSeconds--
            }
            if (sleepTimerRemainingSeconds <= 0) {
                sleepTimerMinutes = 0
                if (sleepPlayLastSongToEnd && isPlaying) {
                    stopAfterCurrentTrack = true
                    showToast("Sleep Timer", "Playing last song to end...")
                } else {
                    if (isPlaying) togglePlayPause()
                    showToast("Sleep Timer", "Playback stopped")
                }
            }
        }
    }

    // Equalizer Preset Application
    fun applyEqPreset(preset: EqPreset) {
        selectedPresetName = preset.name
        // A graphic preset switches back to graphic mode; the "Parametric" entry switches to the band editor
        parametricEqEnabled = preset.isParametric
        preampDb = preset.preamp
        for (i in 0 until minOf(bandGainsDb.size, preset.bands.size)) {
            bandGainsDb[i] = preset.bands[i]
        }
        bassPercent = (preset.bass * 100).toInt().coerceIn(0, 100)
        treblePercent = (preset.treble * 100).toInt().coerceIn(0, 100)
        applyToneAndEffects()
        applyVolumeAndBalance()
        showToast("Preset: ${preset.name}")
    }

    fun resetEqualizer() {
        preampDb = 0f
        for (i in bandGainsDb.indices) bandGainsDb[i] = 0f
        bassPercent = 0
        treblePercent = 0
        selectedPresetName = "Preset"
        applyToneAndEffects()
        applyVolumeAndBalance()
        showToast("Equalizer Reset")
    }

    fun resetVolumeTab() {
        balanceValue = 0.00f
        stereoExpandPercent = 0
        tempoEnabled = false
        tempoMultiplier = 1.00f
        monoEnabled = false
        applyVolumeAndBalance()
        applyToneAndEffects()
        applyTempo()
        showToast("Volume / DSP Controls Reset")
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        reverbEnabled = true
        selectedReverbPreset = preset.name
        reverbDamp = preset.damp
        reverbFilter = preset.filter
        reverbFade = preset.fade
        reverbPreDelay = preset.preDelay
        reverbPreDelayMix = preset.preDelayMix
        reverbSize = preset.size
        reverbMix = preset.mix
        applyToneAndEffects()
        showToast("Reverb Preset: ${preset.name}")
    }

    fun saveCustomReverbPreset() {
        val name = "User Reverb ${reverbPresets.size - BUILT_IN_REVERB_PRESETS.size + 1}"
        val preset = ReverbPreset(
            name = name,
            damp = reverbDamp,
            filter = reverbFilter,
            fade = reverbFade,
            preDelay = reverbPreDelay,
            preDelayMix = reverbPreDelayMix,
            size = reverbSize,
            mix = reverbMix
        )
        reverbPresets.add(0, preset)
        selectedReverbPreset = name
        showToast("Saved Reverb Preset", name)
    }

    fun resetReverbTab() {
        reverbDamp = 0f
        reverbFilter = 0f
        reverbFade = 0f
        reverbPreDelay = 0f
        reverbPreDelayMix = 0f
        reverbSize = 0f
        reverbMix = 0f
        selectedReverbPreset = "Preset"
        applyToneAndEffects()
        showToast("Reverb Parameters Reset")
    }

    // ==========================================================================================
    // Persistence (SharedPreferences JSON). Debounced; position is saved separately.
    // ==========================================================================================
    private fun buildStateJson(): String {
        val o = JSONObject()
        o.put("eqEnabled", eqEnabled)
        o.put("toneEnabled", toneEnabled)
        o.put("limitEnabled", limitEnabled)
        o.put("preampDb", preampDb.toDouble())
        o.put("bands", JSONArray().also { a -> bandGainsDb.forEach { a.put(it.toDouble()) } })
        o.put("bassPercent", bassPercent)
        o.put("treblePercent", treblePercent)
        o.put("selectedPresetName", selectedPresetName)
        o.put("balance", balanceValue.toDouble())
        o.put("stereoExpand", stereoExpandPercent)
        o.put("tempoEnabled", tempoEnabled)
        o.put("tempo", tempoMultiplier.toDouble())
        o.put("mono", monoEnabled)
        o.put("reverbEnabled", reverbEnabled)
        o.put("reverbDamp", reverbDamp.toDouble())
        o.put("reverbFilter", reverbFilter.toDouble())
        o.put("reverbFade", reverbFade.toDouble())
        o.put("reverbPreDelay", reverbPreDelay.toDouble())
        o.put("reverbPreDelayMix", reverbPreDelayMix.toDouble())
        o.put("reverbSize", reverbSize.toDouble())
        o.put("reverbMix", reverbMix.toDouble())
        o.put("selectedReverbPreset", selectedReverbPreset)
        o.put("repeatMode", repeatMode.name)
        o.put("shuffleMode", shuffleMode.name)
        o.put("visMode", visMode.name)
        o.put("visPresetIndex", currentVisPresetIndex)
        o.put("visPresetCycleMode", visPresetCycleMode)

        // Settings
        o.put("skinTheme", skinTheme); o.put("accentColor", accentColor); o.put("settingsTheme", settingsTheme); o.put("settingsFont", settingsFont)
        o.put("smoothSeekbarAnimation", smoothSeekbarAnimation); o.put("playPauseMorphAnimation", playPauseMorphAnimation)
        o.put("trackSlideTransitions", trackSlideTransitions); o.put("showStatusBarOverlay", showStatusBarOverlay)
        o.put("showRatingPill", showRatingPill); o.put("showMetaLine", showMetaLine); o.put("filenameAsTitle", filenameAsTitle)
        o.put("crossfadeEnabled", crossfadeEnabled); o.put("crossfadeDurationSec", crossfadeDurationSec.toDouble())
        o.put("gaplessEnabled", gaplessEnabled); o.put("replayGainMode", replayGainMode)
        o.put("replayGainPreampDb", replayGainPreampDb.toDouble()); o.put("dvcEnabled", dvcEnabled)
        o.put("hiResAudioEnabled", hiResAudioEnabled); o.put("audioBufferSize", audioBufferSize)
        o.put("bitPerfectEnabled", bitPerfectEnabled)
        o.put("extraFolders", JSONArray(extraFolders.toList()))
        o.put("useNativeEngine", useNativeEngine); o.put("parametricEqEnabled", parametricEqEnabled)
        o.put("parametricProfileName", parametricProfileName)
        o.put("parametricBands", JSONArray().also { arr ->
            parametricBands.forEach { b ->
                arr.put(JSONObject().put("type", b.type.name).put("f", b.freqHz.toDouble()).put("g", b.gainDb.toDouble())
                    .put("q", b.q.toDouble()).put("on", b.enabled))
            }
        })
        o.put("pauseOnHeadsetDisconnect", pauseOnHeadsetDisconnect); o.put("resumeOnHeadsetConnect", resumeOnHeadsetConnect)
        o.put("visFadedControlsOpacity", visFadedControlsOpacity.toDouble()); o.put("visPresetDurationSec", visPresetDurationSec)
        o.put("visFpsTarget", visFpsTarget); o.put("visShowPresetToast", visShowPresetToast)
        o.put("backgroundType", backgroundType); o.put("backgroundBlurRadius", backgroundBlurRadius.toDouble())
        o.put("backgroundSaturation", backgroundSaturation.toDouble()); o.put("backgroundDimOpacity", backgroundDimOpacity.toDouble())
        o.put("autoDownloadAlbumArtNet", autoDownloadAlbumArt); o.put("highResAlbumArt", highResAlbumArt)
        o.put("preferEmbeddedArt", preferEmbeddedArt); o.put("albumArtOnLockscreen", albumArtOnLockscreen)
        o.put("autoScanOnStartup", autoScanOnStartup); o.put("ignoreShortTracks", ignoreShortTracks)
        o.put("folders", JSONArray().also { a -> selectedMusicFolders.forEach { a.put(it) } })
        o.put("headsetButtonsEnabled", headsetButtonsEnabled); o.put("bluetoothDvcEnabled", bluetoothDvcEnabled)
        o.put("androidAutoEnabled", androidAutoEnabled); o.put("scrobblingEnabled", scrobblingEnabled)

        // User presets
        val builtInEq = BUILT_IN_EQ_PRESETS.map { it.name }.toSet()
        o.put("userEqPresets", JSONArray().also { arr ->
            eqPresets.filter { it.name !in builtInEq }.forEach { p ->
                arr.put(JSONObject().put("name", p.name).put("category", p.category).put("preamp", p.preamp.toDouble())
                    .put("bass", p.bass.toDouble()).put("treble", p.treble.toDouble())
                    .put("bands", JSONArray().also { a -> p.bands.forEach { a.put(it.toDouble()) } }))
            }
        })
        val builtInRev = BUILT_IN_REVERB_PRESETS.map { it.name }.toSet()
        o.put("userReverbPresets", JSONArray().also { arr ->
            reverbPresets.filter { it.name !in builtInRev }.forEach { p ->
                arr.put(JSONObject().put("name", p.name).put("damp", p.damp.toDouble()).put("filter", p.filter.toDouble())
                    .put("fade", p.fade.toDouble()).put("preDelay", p.preDelay.toDouble())
                    .put("preDelayMix", p.preDelayMix.toDouble()).put("size", p.size.toDouble()).put("mix", p.mix.toDouble()))
            }
        })

        // Library data
        o.put("playlists", JSONArray().also { arr ->
            playlists.forEach { p ->
                arr.put(JSONObject().put("id", p.id).put("name", p.name).put("tracks", JSONArray().also { a -> p.trackIds.forEach { a.put(it) } }))
            }
        })
        o.put("bookmarks", JSONArray().also { arr ->
            bookmarks.forEach { b ->
                arr.put(JSONObject().put("id", b.id).put("trackId", b.trackId).put("title", b.trackTitle)
                    .put("artist", b.artist).put("pos", b.positionMs).put("created", b.createdAt))
            }
        })
        o.put("streams", JSONArray().also { arr ->
            streams.forEach { s -> arr.put(JSONObject().put("id", s.id).put("title", s.title).put("url", s.url).put("genre", s.genre)) }
        })
        o.put("queue", JSONArray().also { a -> queueTracks.forEach { a.put(it.id) } })
        o.put("hidden", JSONArray().also { a -> hiddenTrackIds.forEach { a.put(it) } })

        val data = JSONObject()
        for (t in allTracks) {
            if (t.rating != 0 || t.playCount > 0 || t.lastPlayedTimestamp > 0L || t.lyrics.isNotBlank() || t.id in editedTrackIds) {
                data.put(t.id.toString(), JSONObject()
                    .put("rating", t.rating).put("playCount", t.playCount).put("lastPlayed", t.lastPlayedTimestamp)
                    .put("lyrics", t.lyrics).also { j ->
                        if (t.id in editedTrackIds) {
                            if (!filenameAsTitle) j.put("title", t.title)
                            j.put("artist", t.artist).put("album", t.album).put("albumArtist", t.albumArtist)
                                .put("genre", t.genre).put("year", t.year).put("composer", t.composer)
                        }
                    })
            }
        }
        o.put("trackData", data)
        // Keep previously-saved data for tracks that are not in the library right now (e.g. permission revoked / SD card out)
        for (k in restoredTrackData.keys) if (!data.has(k.toString()) && allTracks.none { it.id == k }) data.put(k.toString(), restoredTrackData[k])
        return o.toString()
    }

    private fun restoreState() {
        val raw = prefs.getString("state", null)
        pendingLastTrackId = prefs.getLong("lastTrackId", -1L)
        pendingResumePositionMs = prefs.getLong("lastPositionMs", 0L)
        if (raw == null) return
        try {
            val o = JSONObject(raw)
            fun f(k: String, d: Float) = o.optDouble(k, d.toDouble()).toFloat()
            eqEnabled = o.optBoolean("eqEnabled", eqEnabled)
            toneEnabled = o.optBoolean("toneEnabled", toneEnabled)
            limitEnabled = o.optBoolean("limitEnabled", limitEnabled)
            preampDb = f("preampDb", preampDb)
            o.optJSONArray("bands")?.let { a -> for (i in 0 until minOf(a.length(), bandGainsDb.size)) bandGainsDb[i] = a.optDouble(i, 0.0).toFloat() }
            bassPercent = o.optInt("bassPercent", bassPercent)
            treblePercent = o.optInt("treblePercent", treblePercent)
            selectedPresetName = o.optString("selectedPresetName", selectedPresetName)
            balanceValue = f("balance", balanceValue)
            stereoExpandPercent = o.optInt("stereoExpand", stereoExpandPercent)
            tempoEnabled = o.optBoolean("tempoEnabled", tempoEnabled)
            tempoMultiplier = f("tempo", tempoMultiplier)
            monoEnabled = o.optBoolean("mono", monoEnabled)
            reverbEnabled = o.optBoolean("reverbEnabled", reverbEnabled)
            reverbDamp = f("reverbDamp", reverbDamp); reverbFilter = f("reverbFilter", reverbFilter)
            reverbFade = f("reverbFade", reverbFade); reverbPreDelay = f("reverbPreDelay", reverbPreDelay)
            reverbPreDelayMix = f("reverbPreDelayMix", reverbPreDelayMix); reverbSize = f("reverbSize", reverbSize)
            reverbMix = f("reverbMix", reverbMix)
            selectedReverbPreset = o.optString("selectedReverbPreset", selectedReverbPreset)
            repeatMode = RepeatMode.entries.firstOrNull { it.name == o.optString("repeatMode") } ?: repeatMode
            shuffleMode = ShuffleMode.entries.firstOrNull { it.name == o.optString("shuffleMode") } ?: shuffleMode
            visMode = VisMode.entries.firstOrNull { it.name == o.optString("visMode") } ?: visMode
            currentVisPresetIndex = o.optInt("visPresetIndex", 0).coerceIn(0, visPresets.lastIndex)
            visPresetCycleMode = o.optString("visPresetCycleMode", visPresetCycleMode)
            showVisPresetBar = visMode != VisMode.DISABLED

            skinTheme = o.optString("skinTheme", skinTheme); accentColor = o.optString("accentColor", accentColor); settingsTheme = o.optString("settingsTheme", settingsTheme)
            settingsFont = o.optString("settingsFont", settingsFont)
            smoothSeekbarAnimation = o.optBoolean("smoothSeekbarAnimation", smoothSeekbarAnimation)
            playPauseMorphAnimation = o.optBoolean("playPauseMorphAnimation", playPauseMorphAnimation)
            trackSlideTransitions = o.optBoolean("trackSlideTransitions", trackSlideTransitions)
            showStatusBarOverlay = o.optBoolean("showStatusBarOverlay", showStatusBarOverlay)
            showRatingPill = o.optBoolean("showRatingPill", showRatingPill)
            showMetaLine = o.optBoolean("showMetaLine", showMetaLine)
            _filenameAsTitle = o.optBoolean("filenameAsTitle", _filenameAsTitle)
            crossfadeEnabled = o.optBoolean("crossfadeEnabled", crossfadeEnabled)
            crossfadeDurationSec = f("crossfadeDurationSec", crossfadeDurationSec)
            gaplessEnabled = o.optBoolean("gaplessEnabled", gaplessEnabled)
            replayGainMode = o.optString("replayGainMode", replayGainMode)
            replayGainPreampDb = f("replayGainPreampDb", replayGainPreampDb)
            dvcEnabled = o.optBoolean("dvcEnabled", dvcEnabled)
            hiResAudioEnabled = o.optBoolean("hiResAudioEnabled", hiResAudioEnabled)
            audioBufferSize = o.optString("audioBufferSize", audioBufferSize)
            bitPerfectEnabled = o.optBoolean("bitPerfectEnabled", bitPerfectEnabled)
            o.optJSONArray("extraFolders")?.let { arr -> extraFolders.clear(); for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { extraFolders.add(it) } }
            useNativeEngine = o.optBoolean("useNativeEngine", useNativeEngine)
            parametricEqEnabled = o.optBoolean("parametricEqEnabled", parametricEqEnabled)
            parametricProfileName = o.optString("parametricProfileName", parametricProfileName)
            o.optJSONArray("parametricBands")?.let { arr ->
                parametricBands.clear()
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    val type = try { FilterType.valueOf(b.optString("type", "PEAK")) } catch (_: Exception) { FilterType.PEAK }
                    parametricBands.add(
                        ParametricBand(type, b.optDouble("f", 1000.0).toFloat(), b.optDouble("g", 0.0).toFloat(),
                            b.optDouble("q", 0.71).toFloat(), b.optBoolean("on", true))
                    )
                }
            }
            pauseOnHeadsetDisconnect = o.optBoolean("pauseOnHeadsetDisconnect", pauseOnHeadsetDisconnect)
            resumeOnHeadsetConnect = o.optBoolean("resumeOnHeadsetConnect", resumeOnHeadsetConnect)
            visFadedControlsOpacity = f("visFadedControlsOpacity", visFadedControlsOpacity)
            visPresetDurationSec = o.optInt("visPresetDurationSec", visPresetDurationSec)
            visFpsTarget = o.optInt("visFpsTarget", visFpsTarget)
            visShowPresetToast = o.optBoolean("visShowPresetToast", visShowPresetToast)
            backgroundType = o.optString("backgroundType", backgroundType)
            backgroundBlurRadius = f("backgroundBlurRadius", backgroundBlurRadius)
            backgroundSaturation = f("backgroundSaturation", backgroundSaturation)
            backgroundDimOpacity = f("backgroundDimOpacity", backgroundDimOpacity)
            autoDownloadAlbumArt = o.optBoolean("autoDownloadAlbumArtNet", autoDownloadAlbumArt)
            highResAlbumArt = o.optBoolean("highResAlbumArt", highResAlbumArt)
            preferEmbeddedArt = o.optBoolean("preferEmbeddedArt", preferEmbeddedArt)
            albumArtOnLockscreen = o.optBoolean("albumArtOnLockscreen", albumArtOnLockscreen)
            autoScanOnStartup = o.optBoolean("autoScanOnStartup", autoScanOnStartup)
            ignoreShortTracks = o.optBoolean("ignoreShortTracks", ignoreShortTracks)
            o.optJSONArray("folders")?.let { a ->
                selectedMusicFolders.clear()
                for (i in 0 until a.length()) selectedMusicFolders.add(a.getString(i))
            }
            headsetButtonsEnabled = o.optBoolean("headsetButtonsEnabled", headsetButtonsEnabled)
            bluetoothDvcEnabled = o.optBoolean("bluetoothDvcEnabled", bluetoothDvcEnabled)
            androidAutoEnabled = o.optBoolean("androidAutoEnabled", androidAutoEnabled)
            scrobblingEnabled = o.optBoolean("scrobblingEnabled", scrobblingEnabled)

            o.optJSONArray("userEqPresets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val bands = p.optJSONArray("bands")?.let { a -> List(a.length()) { a.optDouble(it, 0.0).toFloat() } } ?: List(10) { 0f }
                    eqPresets.add(0, EqPreset(p.optString("name"), p.optString("category", "User"), false,
                        p.optDouble("preamp", 0.0).toFloat(), bands, p.optDouble("bass", 0.0).toFloat(), p.optDouble("treble", 0.0).toFloat()))
                }
            }
            o.optJSONArray("userReverbPresets")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    reverbPresets.add(0, ReverbPreset(p.optString("name"), p.optDouble("damp", 0.0).toFloat(), p.optDouble("filter", 0.0).toFloat(),
                        p.optDouble("fade", 0.0).toFloat(), p.optDouble("preDelay", 0.0).toFloat(), p.optDouble("preDelayMix", 0.0).toFloat(),
                        p.optDouble("size", 0.0).toFloat(), p.optDouble("mix", 0.0).toFloat()))
                }
            }
            if (o.has("playlists")) {
                playlists.clear()
                val arr = o.getJSONArray("playlists")
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONObject(i)
                    val ids = p.optJSONArray("tracks")?.let { a -> List(a.length()) { a.getLong(it) } } ?: emptyList()
                    playlists.add(PlaylistItem(p.getLong("id"), p.getString("name"), ids))
                }
            }
            o.optJSONArray("bookmarks")?.let { arr ->
                bookmarks.clear()
                for (i in 0 until arr.length()) {
                    val b = arr.getJSONObject(i)
                    bookmarks.add(BookmarkItem(b.getLong("id"), b.getLong("trackId"), b.optString("title"), b.optString("artist"), b.optLong("pos"), b.optLong("created")))
                }
            }
            o.optJSONArray("streams")?.let { arr ->
                streams.clear()
                for (i in 0 until arr.length()) {
                    val s = arr.getJSONObject(i)
                    streams.add(StreamItem(s.getLong("id"), s.getString("title"), s.getString("url"), s.optString("genre", "Stream")))
                }
            }
            o.optJSONArray("hidden")?.let { a -> for (i in 0 until a.length()) hiddenTrackIds.add(a.getLong(i)) }
            pendingQueueIds = o.optJSONArray("queue")?.let { a -> List(a.length()) { a.getLong(it) } } ?: emptyList()
            o.optJSONObject("trackData")?.let { d ->
                val m = HashMap<Long, JSONObject>()
                d.keys().forEach { k -> k.toLongOrNull()?.let { id -> m[id] = d.getJSONObject(k) } }
                restoredTrackData = m
                editedTrackIds.addAll(m.filter { it.value.has("artist") }.keys)
            }
        } catch (_: Exception) {
            // Corrupt state: start from defaults rather than crash
        }
    }

    @OptIn(FlowPreview::class)
    private fun startStatePersistence() {
        scope.launch {
            snapshotFlow { buildStateJson() }
                .debounce(700L)
                .collect { json -> withContext(Dispatchers.IO) { prefs.edit().putString("state", json).apply() } }
        }
    }

    /** Called once the library is loaded: re-attach the saved queue. */
    private fun restoreQueue() {
        if (pendingQueueIds.isEmpty()) return
        val byId = allTracks.associateBy { it.id }
        queueTracks.clear()
        pendingQueueIds.mapNotNull { byId[it] }.forEach { queueTracks.add(it) }
        pendingQueueIds = emptyList()
    }

    fun releaseMediaPlayer() {
        cancelFade()
        discardNextPlayer()
        releasePlayerQuietly(mediaPlayer)
        mediaPlayer = null
        isPlaying = false
        setNoisyReceiver(false)
        abandonAudioFocus()
        releaseVisualizer()
        bitPerfectOut?.dispose()
        bitPerfectOut = null
        bitPerfectActive = false
    }

    /** Reacts to audio settings changed from the Settings screen (they are plain state the UI writes to). */
    @OptIn(FlowPreview::class)
    private fun startSettingWatchers() {
        scope.launch {
            snapshotFlow { Triple(replayGainMode, replayGainPreampDb, limitEnabled) }.drop(1).debounce(250L)
                .collect { applyReplayGain() }
        }
        scope.launch {
            snapshotFlow { dvcEnabled to bluetoothDvcEnabled }.drop(1).collect { updateDvc() }
        }
        scope.launch {
            // Output format / buffer changes need the AudioTrack reopened
            snapshotFlow { hiResAudioEnabled to audioBufferSize }.drop(1).debounce(300L)
                .collect { if (mediaPlayer?.isNative == true) reopenForOutputChange() }
        }
        scope.launch {
            snapshotFlow { headsetButtonsEnabled }.drop(1).collect { playbackEpoch++ }
        }
    }

    init {
        restoreState()
        syncDeviceVolume()
        registerVolumeObserver()
        setupAudioEffects()
        startProgressLoop()
        startStatePersistence()
        scanMediaLibrary()
        registerRouteCallback()
        startSettingWatchers()
    }
}
