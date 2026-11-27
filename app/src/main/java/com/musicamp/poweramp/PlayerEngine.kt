package com.musicamp.poweramp

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import java.io.File
import kotlin.math.pow

/**
 * What the controller needs from a player. Two implementations: [MediaPlayerEngine] (Android's player,
 * also used for internet streams) and [NativeEngine] (MediaCodec + own DSP + AudioTrack).
 *
 * Positions and durations are relative to the segment, so a CUE track behaves like a normal file.
 * Callbacks are always delivered on the main thread.
 */
abstract class PlayerEngine {
    var onCompletion: ((PlayerEngine) -> Unit)? = null
    var onError: ((PlayerEngine) -> Unit)? = null
    var onPrepared: ((PlayerEngine) -> Unit)? = null

    /** ReplayGain for this track in dB (positive values are applied only by the native engine). */
    @Volatile var trackGainDb: Float = 0f

    abstract val isNative: Boolean
    abstract fun prepare()
    abstract fun prepareAsync()
    abstract fun start()
    abstract fun pause()
    abstract fun stop()
    abstract fun release()
    abstract fun seekTo(ms: Int)
    abstract val currentPosition: Int
    abstract val duration: Int
    abstract val isPlaying: Boolean
    abstract fun setVolume(left: Float, right: Float)
    abstract fun setSpeed(speed: Float)
    /** Decoded sample rate of the source, once prepared. */
    abstract val sampleRate: Int?
    /** One line describing the decode/output path for the status card. */
    abstract val description: String

    protected val main = Handler(Looper.getMainLooper())
    protected fun postCompletion() = main.post { onCompletion?.invoke(this) }
    protected fun postError() = main.post { onError?.invoke(this) }
    protected fun postPrepared() = main.post { onPrepared?.invoke(this) }
}

/** Wraps [MediaPlayer]. Segment end is enforced by polling, since MediaPlayer has no stop position. */
class MediaPlayerEngine(
    private val context: Context,
    private val sessionId: Int,
    private val attributes: AudioAttributes,
    private val uri: Uri?,
    private val path: String,
    private val startMs: Long = 0L,
    private val endMs: Long = 0L
) : PlayerEngine() {
    private val mp = MediaPlayer()
    private var released = false
    private var segmentDone = false

    override val isNative = false

    private val segmentWatch = object : Runnable {
        override fun run() {
            if (released || endMs <= 0L) return
            val pos = try { mp.currentPosition.toLong() } catch (_: Exception) { return }
            if (pos >= endMs && !segmentDone) {
                segmentDone = true
                try { mp.pause() } catch (_: Exception) {}
                onCompletion?.invoke(this@MediaPlayerEngine)
                return
            }
            if (isPlaying) main.postDelayed(this, 40L)
        }
    }

    private fun configure() {
        mp.setAudioSessionId(sessionId)
        mp.setAudioAttributes(attributes)
        mp.setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
        when {
            uri != null -> mp.setDataSource(context, uri)
            path.isNotBlank() && File(path).exists() -> mp.setDataSource(path)
            else -> throw IllegalStateException("No source")
        }
        mp.setOnCompletionListener { if (!segmentDone) { segmentDone = true; onCompletion?.invoke(this) } }
        mp.setOnErrorListener { _, _, _ -> onError?.invoke(this); true }
    }

    override fun prepare() {
        configure()
        mp.prepare()
        if (startMs > 0L) mp.seekTo(startMs.toInt())
    }

    override fun prepareAsync() {
        configure()
        mp.setOnPreparedListener {
            if (startMs > 0L) try { mp.seekTo(startMs.toInt()) } catch (_: Exception) {}
            onPrepared?.invoke(this)
        }
        mp.prepareAsync()
    }

    override fun start() {
        mp.start()
        if (endMs > 0L) { main.removeCallbacks(segmentWatch); main.post(segmentWatch) }
    }

    override fun pause() = mp.pause()
    override fun stop() {
        main.removeCallbacks(segmentWatch)
        mp.stop()
    }

    override fun release() {
        released = true
        main.removeCallbacks(segmentWatch)
        try { mp.setOnCompletionListener(null); mp.setOnErrorListener(null); mp.setOnPreparedListener(null) } catch (_: Exception) {}
        try { mp.release() } catch (_: Exception) {}
    }

    override fun seekTo(ms: Int) {
        segmentDone = false
        mp.seekTo((startMs + ms).toInt())
    }

    override val currentPosition: Int
        get() = (mp.currentPosition - startMs).toInt().coerceAtLeast(0)

    override val duration: Int
        get() {
            val full = mp.duration
            if (full <= 0) return full
            val end = if (endMs > 0L) endMs else full.toLong()
            return (end - startMs).toInt().coerceAtLeast(0)
        }

    override val isPlaying: Boolean get() = try { mp.isPlaying } catch (_: Exception) { false }

    override fun setVolume(left: Float, right: Float) {
        // MediaPlayer can only attenuate, so positive ReplayGain is not applied here
        val rg = 10f.pow(trackGainDb.coerceAtMost(0f) / 20f)
        mp.setVolume((left * rg).coerceIn(0f, 1f), (right * rg).coerceIn(0f, 1f))
    }

    override fun setSpeed(speed: Float) {
        if (mp.isPlaying) mp.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1.0f)
    }

    override val sampleRate: Int?
        get() = try {
            mp.trackInfo
                ?.firstOrNull { it.trackType == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_AUDIO }
                ?.format
                ?.takeIf { it.containsKey(MediaFormat.KEY_SAMPLE_RATE) }
                ?.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        } catch (_: Exception) {
            null
        }

    override val description: String
        get() = "Android MediaPlayer" + (sampleRate?.let { " · ${formatKhz(it)}" } ?: "")
}

fun formatKhz(hz: Int): String = if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)
