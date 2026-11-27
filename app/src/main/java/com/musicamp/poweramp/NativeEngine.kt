package com.musicamp.poweramp

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.os.Process
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** A decoded audio source producing interleaved stereo float frames. */
interface PcmSource {
    val sampleRate: Int
    val durationUs: Long
    val description: String
    /** True when frames are DoP-packed DSD and must reach the DAC bit-exact. */
    val isDop: Boolean get() = false
    /** Reads up to [maxFrames] stereo frames into [out]. Returns frames read, 0 to retry, -1 at end of stream. */
    fun read(out: FloatArray, maxFrames: Int): Int
    /** Sample-accurate seek. */
    fun seekTo(us: Long)
    fun release()
}

/** How the engine should open its AudioTrack. Decided by the controller once the source rate is known. */
data class OutputPlan(
    val encoding: Int,          // AudioFormat.ENCODING_PCM_*
    val bitPerfect: Boolean,    // bypass all DSP and gain
    val dop: Boolean = false,   // DSD over PCM (only with bitPerfect)
    val bufferMs: Int = 50
)

/** Shared, lock-free DSP settings: the controller replaces [config]; every engine picks it up on its next buffer. */
object DspHolder {
    @Volatile var config: DspConfig = DspConfig()
}

/** MediaExtractor + MediaCodec decoder with float output where the codec supports it. */
class MediaCodecSource(context: Context, uri: Uri?, path: String, private val ext: String = "") : PcmSource {
    private val extractor = MediaExtractor()
    private val codec: MediaCodec
    private val inFormat: MediaFormat
    private var channels: Int
    override var sampleRate: Int
        private set
    private var pcmEncoding = AudioFormat.ENCODING_PCM_16BIT
    override val durationUs: Long
    private var inputDone = false
    private var outputDone = false
    private var pending: java.nio.ByteBuffer? = null
    private var pendingIndex = -1
    private var skipUntilUs = -1L
    private val info = MediaCodec.BufferInfo()
    private val mime: String

    init {
        if (uri != null) extractor.setDataSource(context, uri, null) else extractor.setDataSource(path)
        var track = -1
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        }
        if (track < 0) { extractor.release(); throw IllegalStateException("No audio track") }
        extractor.selectTrack(track)
        inFormat = extractor.getTrackFormat(track)
        mime = inFormat.getString(MediaFormat.KEY_MIME)!!
        channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        durationUs = if (inFormat.containsKey(MediaFormat.KEY_DURATION)) inFormat.getLong(MediaFormat.KEY_DURATION) else 0L
        codec = MediaCodec.createDecoderByType(mime)
        val f = MediaFormat(inFormat)
        f.setInteger(MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_FLOAT)
        try {
            codec.configure(f, null, null, 0)
        } catch (_: Exception) {
            codec.configure(inFormat, null, null, 0) // decoder refuses float output: take its default
        }
        codec.start()
        readOutputFormat(codec.outputFormat)
    }

    private fun readOutputFormat(f: MediaFormat) {
        if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) pcmEncoding = f.getInteger(MediaFormat.KEY_PCM_ENCODING)
        if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    }

    override val description: String
        get() {
            val codecName = if (mime == "audio/raw" && ext.isNotBlank()) ext.uppercase()
                else mime.substringAfter("audio/").uppercase().replace("MPEG", "MP3").replace("MP4A-LATM", "AAC")
            val bits = when (pcmEncoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> "float"
                AudioFormat.ENCODING_PCM_16BIT -> "16-bit"
                AudioFormat.ENCODING_PCM_8BIT -> "8-bit"
                else -> "int"
            }
            return "$codecName ${formatKhz(sampleRate)} ($bits decode)"
        }

    override fun read(out: FloatArray, maxFrames: Int): Int {
        while (true) {
            val buf = pending
            if (buf != null && buf.hasRemaining()) return convert(buf, out, maxFrames)
            if (buf != null) {
                codec.releaseOutputBuffer(pendingIndex, false)
                pending = null; pendingIndex = -1
            }
            if (outputDone) return -1
            feedInput()
            val idx = codec.dequeueOutputBuffer(info, 10_000)
            when {
                idx >= 0 -> {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true
                    val ob = codec.getOutputBuffer(idx)
                    if (ob == null || info.size <= 0) { codec.releaseOutputBuffer(idx, false); continue }
                    ob.position(info.offset); ob.limit(info.offset + info.size)
                    ob.order(ByteOrder.nativeOrder())
                    if (skipUntilUs >= 0) {
                        // Drop decoded audio before the seek target (sample-accurate seeking)
                        val frameBytes = bytesPerSample() * channels
                        val framesInBuf = info.size / frameBytes
                        val endUs = info.presentationTimeUs + framesInBuf * 1_000_000L / sampleRate
                        if (endUs <= skipUntilUs) { codec.releaseOutputBuffer(idx, false); continue }
                        val skipFrames = ((skipUntilUs - info.presentationTimeUs) * sampleRate / 1_000_000L).coerceIn(0L, framesInBuf.toLong())
                        ob.position(info.offset + (skipFrames * frameBytes).toInt())
                        skipUntilUs = -1L
                    }
                    pending = ob; pendingIndex = idx
                }
                idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> readOutputFormat(codec.outputFormat)
                else -> return 0
            }
        }
    }

    private fun feedInput() {
        if (inputDone) return
        val idx = codec.dequeueInputBuffer(0)
        if (idx < 0) return
        val ib = codec.getInputBuffer(idx) ?: return
        val n = extractor.readSampleData(ib, 0)
        if (n < 0) {
            codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun bytesPerSample() = when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_8BIT -> 1
        else -> 2
    }

    private fun sampleAt(b: java.nio.ByteBuffer): Float = when (pcmEncoding) {
        AudioFormat.ENCODING_PCM_FLOAT -> b.float
        AudioFormat.ENCODING_PCM_32BIT -> b.int / 2147483648f
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
            val lo = b.get().toInt() and 0xFF; val mid = b.get().toInt() and 0xFF; val hi = b.get().toInt()
            ((hi shl 16) or (mid shl 8) or lo) / 8388608f
        }
        AudioFormat.ENCODING_PCM_8BIT -> ((b.get().toInt() and 0xFF) - 128) / 128f
        else -> b.short / 32768f
    }

    /** Converts to interleaved stereo float; multichannel is downmixed (FL FR FC LFE BL BR order). */
    private fun convert(b: java.nio.ByteBuffer, out: FloatArray, maxFrames: Int): Int {
        val frameBytes = bytesPerSample() * channels
        val frames = minOf(maxFrames, b.remaining() / frameBytes)
        if (frames <= 0) { b.position(b.limit()); return 0 }
        var o = 0
        val tmp = FloatArray(channels)
        for (n in 0 until frames) {
            for (c in 0 until channels) tmp[c] = sampleAt(b)
            when (channels) {
                1 -> { out[o] = tmp[0]; out[o + 1] = tmp[0] }
                2 -> { out[o] = tmp[0]; out[o + 1] = tmp[1] }
                else -> {
                    val c = if (channels > 2) tmp[2] * 0.707f else 0f
                    val bl = if (channels > 4) tmp[4] * 0.707f else 0f
                    val br = if (channels > 5) tmp[5] * 0.707f else 0f
                    out[o] = (tmp[0] + c + bl) * 0.414f
                    out[o + 1] = (tmp[1] + c + br) * 0.414f
                }
            }
            o += 2
        }
        return frames
    }

    override fun seekTo(us: Long) {
        pending?.let { codec.releaseOutputBuffer(pendingIndex, false) }
        pending = null; pendingIndex = -1
        extractor.seekTo(us, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        codec.flush()
        inputDone = false; outputDone = false
        skipUntilUs = us
    }

    override fun release() {
        try { codec.stop() } catch (_: Exception) {}
        try { codec.release() } catch (_: Exception) {}
        try { extractor.release() } catch (_: Exception) {}
    }
}

/**
 * MusicAmp's own playback engine: decode -> [DspProcessor] -> AudioTrack, on a dedicated audio thread.
 * Writes are non-blocking so pause, seek and release are always responsive.
 */
class NativeEngine(
    private val context: Context,
    private val sessionId: Int,
    private val attributes: AudioAttributes,
    private val uri: Uri?,
    private val path: String,
    private val formatExt: String,
    private val startMs: Long = 0L,
    private val endMs: Long = 0L,
    /** Called on the preparing thread with (decoded rate, native DSD rate or null); returns the output to open. */
    private val planOutput: (sourceRate: Int, dsdRate: Int?) -> OutputPlan
) : PlayerEngine() {

    override val isNative = true
    override val isLimiting: Boolean get() = dsp?.limiting == true
    override val peakLevel: Float get() = dsp?.peakLevel ?: 0f
    override val outputEncoding: Int get() = plan?.encoding ?: AudioFormat.ENCODING_PCM_16BIT
    override val isBitPerfectOutput: Boolean get() = plan?.bitPerfect == true

    private var source: PcmSource? = null
    private var track: AudioTrack? = null
    private var plan: OutputPlan? = null
    private var dsp: DspProcessor? = null
    private var thread: Thread? = null
    private val lock = Object()

    @Volatile private var playing = false
    @Volatile private var released = false
    @Volatile private var seekTargetMs = -1L
    @Volatile private var eos = false
    @Volatile private var completed = false
    @Volatile private var basePosMs = 0L
    @Volatile private var headBase = 0L
    @Volatile private var framesWritten = 0L
    @Volatile private var volL = 1f
    @Volatile private var volR = 1f
    private var outRate = 44100
    private var segmentFrames = Long.MAX_VALUE
    private var framesFromBase = 0L

    override fun prepare() {
        val isDsd = formatExt.equals("dsf", true) || formatExt.equals("dff", true)
        val src: PcmSource = if (isDsd) {
            val probe = DsdSource.open(context, uri, path) ?: throw IllegalStateException("Unreadable DSD file")
            val p = planOutput(DsdSource.PCM_RATE, probe.dsdRate)
            probe.configure(dop = p.dop)
            plan = p
            probe
        } else {
            MediaCodecSource(context, uri, path, formatExt).also { plan = planOutput(it.sampleRate, null) }
        }
        source = src
        val p = plan!!
        outRate = src.sampleRate
        val bytesPerSample = when (p.encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            else -> 2
        }
        val minBuf = AudioTrack.getMinBufferSize(outRate, AudioFormat.CHANNEL_OUT_STEREO, p.encoding)
        if (minBuf <= 0) { src.release(); throw IllegalStateException("Output format not supported") }
        val wanted = outRate * 2 * bytesPerSample * p.bufferMs / 1000
        val builder = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(p.encoding).setSampleRate(outRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, wanted))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setSessionId(sessionId)
        if (p.bufferMs >= 200) builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
        val t = try { builder.build() } catch (e: Exception) { src.release(); throw e }
        if (t.state != AudioTrack.STATE_INITIALIZED) { t.release(); src.release(); throw IllegalStateException("AudioTrack init failed") }
        track = t
        dsp = DspProcessor(outRate)
        segmentFrames = if (endMs > 0L) (endMs - startMs) * outRate / 1000 else Long.MAX_VALUE
        if (startMs > 0L) src.seekTo(startMs * 1000)
        basePosMs = 0L
        headBase = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        thread = Thread({ loop() }, "MusicAmp-audio").also {
            it.priority = Thread.MAX_PRIORITY
            it.start()
        }
    }

    override fun prepareAsync() {
        Thread({
            try {
                prepare()
                if (!released) postPrepared()
            } catch (_: Exception) {
                if (!released) postError()
            }
        }, "MusicAmp-prepare").start()
    }

    private fun wake() = synchronized(lock) { lock.notifyAll() }

    private fun waitMs(ms: Long) = synchronized(lock) { try { lock.wait(ms) } catch (_: InterruptedException) {} }

    private fun loop() {
        try { Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO) } catch (_: Exception) {}
        val frames = 1024
        val fbuf = FloatArray(frames * 2)
        val p = plan ?: return
        val shorts = if (p.encoding == AudioFormat.ENCODING_PCM_16BIT) ShortArray(frames * 2) else null
        val bytes = when (p.encoding) {
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> ByteArray(frames * 2 * 3)
            AudioFormat.ENCODING_PCM_32BIT -> ByteArray(frames * 2 * 4)
            else -> null
        }
        var rng = 0x12345678
        try {
            while (!released) {
                handleSeek()
                if (eos) {
                    drainAndComplete()
                    continue
                }
                if (!playing && bufferFull()) { waitMs(100); continue }
                val src = source ?: break
                val want = minOf(frames.toLong(), segmentFrames - framesFromBase).toInt()
                val n = if (want <= 0) -1 else src.read(fbuf, want)
                if (n < 0) { eos = true; continue }
                if (n == 0) continue
                framesFromBase += n
                val d = dsp!!
                if (!p.bitPerfect) {
                    d.update(DspHolder.config)
                    d.trackGainDb = trackGainDb
                    d.volL = volL; d.volR = volR
                    d.process(fbuf, n)
                }
                val t = track ?: break
                when (p.encoding) {
                    AudioFormat.ENCODING_PCM_FLOAT -> {
                        if (!p.bitPerfect) for (i in 0 until n * 2) fbuf[i] = fbuf[i].coerceIn(-1f, 1f)
                        writeAll(n * 2) { off -> t.write(fbuf, off, n * 2 - off, AudioTrack.WRITE_NON_BLOCKING) }
                    }
                    AudioFormat.ENCODING_PCM_16BIT -> {
                        val s = shorts!!
                        for (i in 0 until n * 2) {
                            var v = fbuf[i] * 32767f
                            if (!p.bitPerfect) {
                                // TPDF dither when reducing to 16 bits
                                rng = rng * 1664525 + 1013904223; val r1 = (rng ushr 8) / 16777216f
                                rng = rng * 1664525 + 1013904223; val r2 = (rng ushr 8) / 16777216f
                                v += r1 - r2
                            } else {
                                v = fbuf[i] * 32768f
                            }
                            s[i] = v.roundToInt().coerceIn(-32768, 32767).toShort()
                        }
                        writeAll(n * 2) { off -> t.write(s, off, n * 2 - off, AudioTrack.WRITE_NON_BLOCKING) }
                    }
                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                        val b = bytes!!
                        var o = 0
                        for (i in 0 until n * 2) {
                            val v = (fbuf[i].toDouble() * 8388608.0).roundToInt().coerceIn(-8388608, 8388607)
                            b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte(); b[o + 2] = (v shr 16).toByte(); o += 3
                        }
                        writeAll(n * 6) { off -> t.write(b, off, n * 6 - off, AudioTrack.WRITE_NON_BLOCKING) }
                    }
                    AudioFormat.ENCODING_PCM_32BIT -> {
                        val b = bytes!!
                        var o = 0
                        for (i in 0 until n * 2) {
                            val v = (fbuf[i].toDouble() * 2147483648.0).coerceIn(-2147483648.0, 2147483647.0).toLong().toInt()
                            b[o] = v.toByte(); b[o + 1] = (v shr 8).toByte(); b[o + 2] = (v shr 16).toByte(); b[o + 3] = (v shr 24).toByte(); o += 4
                        }
                        writeAll(n * 8) { off -> t.write(b, off, n * 8 - off, AudioTrack.WRITE_NON_BLOCKING) }
                    }
                }
                framesWritten += n
            }
        } catch (_: Exception) {
            if (!released) postError()
        }
    }

    private fun bufferFull(): Boolean {
        val t = track ?: return true
        val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        val queued = framesWritten - (head - headBase)
        return queued >= t.bufferSizeInFrames
    }

    /** Writes until everything is queued, waiting (not spinning) while the buffer is full. */
    private inline fun writeAll(total: Int, write: (offset: Int) -> Int) {
        var off = 0
        while (off < total && !released && seekTargetMs < 0) {
            val w = write(off)
            if (w < 0) throw IllegalStateException("AudioTrack write error $w")
            off += w
            if (w == 0) waitMs(if (playing) 8L else 100L)
        }
    }

    private fun handleSeek() {
        val target = seekTargetMs
        if (target < 0) return
        val t = track ?: return
        val src = source ?: return
        try { t.pause(); t.flush() } catch (_: Exception) {}
        src.seekTo((startMs + target) * 1000)
        basePosMs = target
        headBase = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
        framesWritten = 0L
        framesFromBase = target * outRate / 1000
        eos = false
        completed = false
        if (seekTargetMs == target) seekTargetMs = -1L
        if (playing) try { t.play() } catch (_: Exception) {}
    }

    private fun drainAndComplete() {
        val t = track ?: return
        var lastHead = -1L
        var stuckSince = 0L
        while (!released && seekTargetMs < 0 && !completed) {
            val head = t.playbackHeadPosition.toLong() and 0xFFFFFFFFL
            if (head - headBase >= framesWritten) break
            if (!playing) { waitMs(100); stuckSince = 0L; continue }
            if (head == lastHead) {
                if (stuckSince == 0L) stuckSince = System.currentTimeMillis()
                // A track shorter than the start threshold never starts: stop() plays out what is queued
                if (System.currentTimeMillis() - stuckSince > 300L) try { t.stop() } catch (_: Exception) {}
                if (System.currentTimeMillis() - stuckSince > 1500L) break
            } else stuckSince = 0L
            lastHead = head
            waitMs(15)
        }
        if (!released && seekTargetMs < 0 && !completed) {
            completed = true
            playing = false
            postCompletion()
        }
        if (completed) waitMs(200)
    }

    override fun start() {
        if (completed) return
        playing = true
        try { track?.play() } catch (_: Exception) {}
        wake()
    }

    override fun pause() {
        playing = false
        try { track?.pause() } catch (_: Exception) {}
        wake()
    }

    override fun stop() = pause()

    override fun release() {
        released = true
        playing = false
        wake()
        try { track?.pause(); track?.flush() } catch (_: Exception) {}
        try { thread?.join(500) } catch (_: Exception) {}
        try { track?.release() } catch (_: Exception) {}
        track = null
        try { source?.release() } catch (_: Exception) {}
        source = null
    }

    override fun seekTo(ms: Int) {
        seekTargetMs = ms.toLong().coerceAtLeast(0L)
        completed = false
        wake()
    }

    override val currentPosition: Int
        get() {
            val pending = seekTargetMs
            if (pending >= 0) return pending.toInt()
            val t = track ?: return basePosMs.toInt()
            val head = (t.playbackHeadPosition.toLong() and 0xFFFFFFFFL) - headBase
            return (basePosMs + head.coerceAtLeast(0L) * 1000L / outRate).toInt()
        }

    override val duration: Int
        get() {
            val full = ((source?.durationUs ?: 0L) / 1000L)
            val end = if (endMs > 0L) endMs else full
            return (end - startMs).toInt().coerceAtLeast(0)
        }

    override val isPlaying: Boolean get() = playing

    override fun setVolume(left: Float, right: Float) {
        volL = left; volR = right
    }

    override fun setSpeed(speed: Float) {
        if (plan?.bitPerfect == true) return
        try { track?.playbackParams = PlaybackParams().setSpeed(speed).setPitch(1.0f) } catch (_: Exception) {}
    }

    override val sampleRate: Int? get() = source?.sampleRate

    override val description: String
        get() {
            val p = plan ?: return "MusicAmp engine"
            val out = when (p.encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> "32-bit float"
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> "24-bit"
                AudioFormat.ENCODING_PCM_32BIT -> "32-bit"
                else -> "16-bit"
            }
            val mode = when {
                p.dop -> "DoP bit-perfect"
                p.bitPerfect -> "bit-perfect"
                else -> "DSP"
            }
            return "MusicAmp engine · ${source?.description ?: ""} → $out ${formatKhz(outRate)} · $mode"
        }

    companion object {
        /** True when this device can open a 32-bit integer AudioTrack. */
        val supportsInt32: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    }
}
