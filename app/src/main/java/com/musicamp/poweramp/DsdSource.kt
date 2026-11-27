package com.musicamp.poweramp

import android.content.Context
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * DSD playback for DSF and DSDIFF (DFF, uncompressed) files.
 *
 * Two modes:
 *  - PCM: the 1-bit stream is low-pass filtered and decimated to 88.2 kHz (FIR, table driven).
 *  - DoP: DSD bits are packed into 24-bit PCM frames with alternating 0x05/0xFA markers, so a DoP-capable
 *    DAC receiving them bit-perfect reconstructs native DSD.
 */
class DsdSource private constructor(
    private val src: SeekableSource,
    val dsdRate: Int,
    private val channels: Int,
    private val dataStart: Long,
    private val bytesPerChannel: Long,
    private val dsfBlockSize: Int,   // 0 = DSDIFF byte-interleaved
    private val lsbFirst: Boolean
) : PcmSource {

    private var dop = false
    private var pos = 0L                     // byte position per channel
    private val chunk = Array(2) { ByteArray(4096) }
    private var chunkLen = 0
    private var chunkPos = 0
    private var dopMarker = 0x05

    // PCM decimation
    private val bytesPerOut = (dsdRate / PCM_RATE / 8).coerceAtLeast(1)
    private val firBytes = 24 * bytesPerOut
    private val table: Array<FloatArray> by lazy { buildTable() }
    private val ring = Array(2) { ByteArray(firBytes) { SILENCE } }
    private var ringHead = 0

    override val sampleRate: Int get() = if (dop) dsdRate / 16 else PCM_RATE
    override val durationUs: Long get() = bytesPerChannel * 8L * 1_000_000L / dsdRate
    override val isDop: Boolean get() = dop
    override val description: String
        get() = "DSD${dsdRate / 44100} (${"%.1f".format(dsdRate / 1_000_000f)} MHz)" + if (dop) " DoP" else " → PCM"

    fun configure(dop: Boolean) {
        this.dop = dop
    }

    private fun buildTable(): Array<FloatArray> {
        val taps = firBytes * 8
        val fc = 24000.0 / dsdRate // normalised cutoff
        val h = DoubleArray(taps)
        val m = taps - 1
        var sum = 0.0
        for (n in 0 until taps) {
            val x = n - m / 2.0
            val sinc = if (x == 0.0) 2 * fc else sin(2 * PI * fc * x) / (PI * x)
            // Blackman-Harris window
            val w = 0.35875 - 0.48829 * cos(2 * PI * n / m) + 0.14128 * cos(4 * PI * n / m) - 0.01168 * cos(6 * PI * n / m)
            h[n] = sinc * w
            sum += h[n]
        }
        for (n in 0 until taps) h[n] /= sum
        return Array(firBytes) { k ->
            FloatArray(256) { v ->
                var acc = 0.0
                for (b in 0 until 8) acc += h[k * 8 + b] * (if ((v shr b) and 1 == 1) 1.0 else -1.0)
                acc.toFloat()
            }
        }
    }

    /** Refills [chunk] for every channel, MSB-first. Returns false at end of data. */
    private fun refill(): Boolean {
        val remaining = bytesPerChannel - pos
        if (remaining <= 0) return false
        if (dsfBlockSize > 0) {
            val group = pos / dsfBlockSize
            val off = (pos % dsfBlockSize).toInt()
            val n = minOf((dsfBlockSize - off).toLong(), remaining, 4096L).toInt()
            for (c in 0 until minOf(channels, 2)) {
                val b = src.read(dataStart + group * dsfBlockSize * channels + c.toLong() * dsfBlockSize + off, n)
                System.arraycopy(b, 0, chunk[c], 0, b.size)
            }
            chunkLen = n
        } else {
            val n = minOf(remaining, 4096L).toInt()
            val b = src.read(dataStart + pos * channels, n * channels)
            val frames = b.size / channels
            for (i in 0 until frames) for (c in 0 until minOf(channels, 2)) chunk[c][i] = b[i * channels + c]
            chunkLen = frames
            if (frames == 0) return false
        }
        if (channels == 1) System.arraycopy(chunk[0], 0, chunk[1], 0, chunkLen)
        if (lsbFirst) for (c in 0 until 2) for (i in 0 until chunkLen) chunk[c][i] = REVERSE[chunk[c][i].toInt() and 0xFF]
        pos += chunkLen
        chunkPos = 0
        return true
    }

    override fun read(out: FloatArray, maxFrames: Int): Int {
        val need = if (dop) 2 else bytesPerOut
        var frames = 0
        var o = 0
        while (frames < maxFrames) {
            if (chunkLen - chunkPos < need) {
                if (chunkLen - chunkPos > 0) { chunkPos = chunkLen } // drop a partial tail (sub-millisecond)
                if (!refill()) return if (frames == 0) -1 else frames
                if (chunkLen < need) return if (frames == 0) -1 else frames
            }
            if (dop) {
                for (c in 0 until 2) {
                    val hi = chunk[c][chunkPos].toInt() and 0xFF
                    val lo = chunk[c][chunkPos + 1].toInt() and 0xFF
                    var v = (dopMarker shl 16) or (hi shl 8) or lo
                    if (v and 0x800000 != 0) v = v or -0x1000000 // sign-extend 24-bit
                    out[o + c] = v / 8388608f
                }
                dopMarker = if (dopMarker == 0x05) 0xFA else 0x05
                chunkPos += 2
            } else {
                for (k in 0 until bytesPerOut) {
                    ringHead = (ringHead + 1) % firBytes
                    ring[0][ringHead] = chunk[0][chunkPos + k]
                    ring[1][ringHead] = chunk[1][chunkPos + k]
                }
                chunkPos += bytesPerOut
                val t = table
                for (c in 0 until 2) {
                    val r = ring[c]
                    var acc = 0f
                    var idx = ringHead
                    for (k in 0 until firBytes) {
                        acc += t[k][r[idx].toInt() and 0xFF]
                        idx = if (idx == 0) firBytes - 1 else idx - 1
                    }
                    out[o + c] = acc
                }
            }
            o += 2
            frames++
        }
        return frames
    }

    override fun seekTo(us: Long) {
        val align = if (dop) 2L else bytesPerOut.toLong()
        var p = (us * dsdRate / 8L / 1_000_000L).coerceIn(0L, bytesPerChannel)
        p -= p % align
        pos = p
        chunkLen = 0; chunkPos = 0
        for (r in ring) r.fill(SILENCE)
        dopMarker = 0x05
    }

    override fun release() = src.close()

    companion object {
        const val PCM_RATE = 88200
        private const val SILENCE: Byte = 0x69
        private val REVERSE = ByteArray(256) { v -> (Integer.reverse(v) ushr 24).toByte() }

        fun open(context: Context, uri: Uri?, path: String): DsdSource? {
            val src = SeekableSource.open(context, uri, path) ?: return null
            return try {
                val magic = String(src.read(0, 4), Charsets.ISO_8859_1)
                when (magic) {
                    "DSD " -> openDsf(src)
                    "FRM8" -> openDff(src)
                    else -> null
                } ?: run { src.close(); null }
            } catch (_: Exception) {
                src.close(); null
            }
        }

        private fun openDsf(src: SeekableSource): DsdSource? {
            val h = ByteBuffer.wrap(src.read(0, 92)).order(ByteOrder.LITTLE_ENDIAN)
            if (h.limit() < 92) return null
            val fmtAt = 28
            if (String(h.array(), fmtAt, 4, Charsets.ISO_8859_1) != "fmt ") return null
            val fmtSize = h.getLong(fmtAt + 4)
            val channels = h.getInt(fmtAt + 24)
            val rate = h.getInt(fmtAt + 28)
            val bits = h.getInt(fmtAt + 32)
            val sampleCount = h.getLong(fmtAt + 36)
            val blockSize = h.getInt(fmtAt + 44)
            val dataAt = fmtAt + fmtSize
            if (String(src.read(dataAt, 4), Charsets.ISO_8859_1) != "data") return null
            if (channels !in 1..6 || rate < 2_822_400 || blockSize <= 0) return null
            return DsdSource(src, rate, channels, dataAt + 12, sampleCount / 8, blockSize, lsbFirst = bits == 1)
        }

        private fun openDff(src: SeekableSource): DsdSource? {
            fun be64(b: ByteArray, o: Int) = ByteBuffer.wrap(b, o, 8).order(ByteOrder.BIG_ENDIAN).long
            if (String(src.read(12, 4), Charsets.ISO_8859_1) != "DSD ") return null
            var p = 16L
            var rate = 0
            var channels = 0
            var compressed = false
            var dataStart = -1L
            var dataSize = 0L
            val end = src.size
            while (p + 12 <= end) {
                val h = src.read(p, 12)
                val id = String(h, 0, 4, Charsets.ISO_8859_1)
                val size = be64(h, 4)
                val body = p + 12
                when (id) {
                    "PROP" -> {
                        var q = body + 4 // "SND "
                        val propEnd = body + size
                        while (q + 12 <= propEnd) {
                            val sh = src.read(q, 12)
                            val sid = String(sh, 0, 4, Charsets.ISO_8859_1)
                            val ssize = be64(sh, 4)
                            val sb = src.read(q + 12, minOf(ssize, 16L).toInt())
                            when (sid) {
                                "FS  " -> rate = ByteBuffer.wrap(sb, 0, 4).order(ByteOrder.BIG_ENDIAN).int
                                "CHNL" -> channels = ByteBuffer.wrap(sb, 0, 2).order(ByteOrder.BIG_ENDIAN).short.toInt()
                                "CMPR" -> compressed = String(sb, 0, 4, Charsets.ISO_8859_1) != "DSD "
                            }
                            q += 12 + ssize + (ssize and 1)
                        }
                    }
                    "DSD " -> { dataStart = body; dataSize = size }
                    "DST " -> compressed = true
                }
                p = body + size + (size and 1)
            }
            if (compressed || dataStart < 0 || channels !in 1..6 || rate < 2_822_400) return null
            return DsdSource(src, rate, channels, dataStart, dataSize / channels, 0, lsbFirst = false)
        }
    }
}
