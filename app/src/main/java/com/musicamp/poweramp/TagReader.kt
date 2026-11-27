package com.musicamp.poweramp

import android.content.Context
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/** Tag fields MediaStore does not expose: ReplayGain, lyrics, cue sheets and stream properties. */
data class TagInfo(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null,
    val lyrics: String? = null,
    /** Text cue sheet embedded as a CUESHEET comment. */
    val cueSheet: String? = null,
    /** FLAC binary CUESHEET block: track number to start offset in samples. */
    val flacCueOffsets: List<Pair<Int, Long>> = emptyList(),
    val sampleRate: Int? = null,
    val bitsPerSample: Int? = null,
    // Basic text tags, used for files MediaStore does not index (DSD)
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val trackNumber: Int? = null
)

/** Random-access reader over a file descriptor. */
class SeekableSource private constructor(private val stream: FileInputStream, private val owner: Closeable? = null) : Closeable {
    val channel: FileChannel = stream.channel
    val size: Long get() = channel.size()

    fun read(pos: Long, len: Int): ByteArray {
        val bb = ByteBuffer.allocate(len.coerceAtLeast(0))
        var p = pos
        while (bb.hasRemaining()) {
            val n = channel.read(bb, p)
            if (n <= 0) break
            p += n
        }
        return bb.array().copyOf(bb.position())
    }

    override fun close() {
        try { stream.close() } catch (_: Exception) {}
        try { owner?.close() } catch (_: Exception) {}
    }

    companion object {
        fun open(context: Context, track: AudioTrack): SeekableSource? = open(context, track.uri, track.filePath)

        fun open(context: Context, uri: Uri?, path: String): SeekableSource? {
            try {
                if (uri != null && uri.scheme != "http" && uri.scheme != "https") {
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
                    return SeekableSource(FileInputStream(pfd.fileDescriptor), pfd)
                }
                if (path.isNotBlank() && File(path).canRead()) return SeekableSource(FileInputStream(path))
            } catch (_: Exception) {
            }
            return null
        }

    }
}

object TagReader {
    private const val MAX_BLOCK = 4 * 1024 * 1024

    fun read(context: Context, track: AudioTrack): TagInfo? {
        if (track.uri?.scheme == "http" || track.uri?.scheme == "https") return null
        val src = SeekableSource.open(context, track) ?: return null
        return try {
            val head = src.read(0, 12)
            when {
                head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC" -> readFlac(src)
                head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "DSD " -> {
                    val ptr = ByteBuffer.wrap(src.read(20, 8)).order(ByteOrder.LITTLE_ENDIAN).long
                    if (ptr > 0) readId3(src, ptr) else TagInfo()
                }
                head.size >= 3 && String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" -> readId3(src)
                head.size >= 4 && String(head, 0, 4, Charsets.ISO_8859_1) == "OggS" -> readOgg(src)
                head.size >= 8 && String(head, 4, 4, Charsets.ISO_8859_1) == "ftyp" -> readMp4(src)
                else -> null
            }
        } catch (_: Exception) {
            null
        } finally {
            src.close()
        }
    }

    // ---------------------------------------------------------------- Vorbis comments (FLAC / Ogg)
    private fun fromComments(c: Map<String, String>, base: TagInfo = TagInfo()): TagInfo {
        fun db(k: String) = c[k]?.let { parseDb(it) }
        fun num(k: String) = c[k]?.trim()?.toFloatOrNull()
        var track = db("REPLAYGAIN_TRACK_GAIN")
        var album = db("REPLAYGAIN_ALBUM_GAIN")
        // Opus R128 gains are Q7.8 dB relative to -23 LUFS; ReplayGain reference is -18 LUFS (+5 dB)
        if (track == null) c["R128_TRACK_GAIN"]?.trim()?.toIntOrNull()?.let { track = it / 256f + 5f }
        if (album == null) c["R128_ALBUM_GAIN"]?.trim()?.toIntOrNull()?.let { album = it / 256f + 5f }
        return base.copy(
            trackGainDb = track, albumGainDb = album,
            trackPeak = num("REPLAYGAIN_TRACK_PEAK"), albumPeak = num("REPLAYGAIN_ALBUM_PEAK"),
            lyrics = c["LYRICS"] ?: c["UNSYNCEDLYRICS"] ?: c["UNSYNCED LYRICS"],
            cueSheet = c["CUESHEET"]
        )
    }

    private fun parseDb(v: String): Float? = v.trim().replace(Regex("(?i)\\s*dB$"), "").replace(',', '.').toFloatOrNull()

    private fun parseVorbisComment(b: ByteArray, offset: Int): Map<String, String> {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN)
        bb.position(offset)
        val vendorLen = bb.int
        if (vendorLen < 0 || vendorLen > bb.remaining()) return emptyMap()
        bb.position(bb.position() + vendorLen)
        val n = bb.int
        val out = HashMap<String, String>()
        repeat(n.coerceIn(0, 10_000)) {
            if (bb.remaining() < 4) return out
            val len = bb.int
            if (len < 0 || len > bb.remaining()) return out
            val s = String(b, bb.position(), len, Charsets.UTF_8)
            bb.position(bb.position() + len)
            val eq = s.indexOf('=')
            if (eq > 0) out[s.substring(0, eq).uppercase()] = s.substring(eq + 1)
        }
        return out
    }

    // ---------------------------------------------------------------- FLAC
    private fun readFlac(src: SeekableSource): TagInfo {
        var pos = 4L
        var info = TagInfo()
        var comments: Map<String, String> = emptyMap()
        var last = false
        while (!last && pos < src.size) {
            val h = src.read(pos, 4)
            if (h.size < 4) break
            last = (h[0].toInt() and 0x80) != 0
            val type = h[0].toInt() and 0x7F
            val len = ((h[1].toInt() and 0xFF) shl 16) or ((h[2].toInt() and 0xFF) shl 8) or (h[3].toInt() and 0xFF)
            pos += 4
            when (type) {
                0 -> { // STREAMINFO
                    val s = src.read(pos, 18)
                    if (s.size >= 18) {
                        val rate = ((s[10].toInt() and 0xFF) shl 12) or ((s[11].toInt() and 0xFF) shl 4) or ((s[12].toInt() and 0xF0) shr 4)
                        val bps = (((s[12].toInt() and 0x01) shl 4) or ((s[13].toInt() and 0xF0) shr 4)) + 1
                        info = info.copy(sampleRate = rate, bitsPerSample = bps)
                    }
                }
                4 -> if (len in 1..MAX_BLOCK) comments = parseVorbisComment(src.read(pos, len), 0)
                5 -> if (len in 1..MAX_BLOCK) info = info.copy(flacCueOffsets = parseFlacCueBlock(src.read(pos, len)))
            }
            pos += len
        }
        return fromComments(comments, info)
    }

    /** FLAC CUESHEET metadata block: 128 catalog + 8 lead-in + 1 flags + 258 reserved + 1 track count. */
    private fun parseFlacCueBlock(b: ByteArray): List<Pair<Int, Long>> {
        val bb = ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN)
        if (b.size < 396) return emptyList()
        bb.position(395)
        val count = bb.get().toInt() and 0xFF
        val out = ArrayList<Pair<Int, Long>>()
        repeat(count) {
            if (bb.remaining() < 36) return out
            val offset = bb.long
            val num = bb.get().toInt() and 0xFF
            bb.position(bb.position() + 12 + 1 + 13)
            val nIdx = bb.get().toInt() and 0xFF
            var start = offset
            repeat(nIdx) {
                if (bb.remaining() < 12) return out
                val io = bb.long
                val inum = bb.get().toInt() and 0xFF
                bb.position(bb.position() + 3)
                if (inum == 1) start = offset + io
            }
            if (num in 1..99) out.add(num to start) // 170/255 = lead-out
        }
        return out
    }

    // ---------------------------------------------------------------- Ogg (Vorbis / Opus)
    private fun readOgg(src: SeekableSource): TagInfo {
        // Reassemble the second logical packet (comment header) from the first pages
        var pos = 0L
        val packets = ArrayList<ByteArray>()
        var cur = java.io.ByteArrayOutputStream()
        var pages = 0
        while (packets.size < 2 && pages < 64 && pos < src.size) {
            val h = src.read(pos, 27)
            if (h.size < 27 || String(h, 0, 4, Charsets.ISO_8859_1) != "OggS") break
            val nSeg = h[26].toInt() and 0xFF
            val table = src.read(pos + 27, nSeg)
            var dataPos = pos + 27 + nSeg
            for (seg in table) {
                val l = seg.toInt() and 0xFF
                if (cur.size() + l > MAX_BLOCK) return TagInfo()
                cur.write(src.read(dataPos, l))
                dataPos += l
                if (l < 255) {
                    packets.add(cur.toByteArray()); cur = java.io.ByteArrayOutputStream()
                    if (packets.size >= 2) break
                }
            }
            pos = dataPos
            pages++
        }
        if (packets.size < 2) return TagInfo()
        val id = packets[0]
        val cm = packets[1]
        var info = TagInfo()
        if (id.size >= 16 && String(id, 0, 8, Charsets.ISO_8859_1) == "OpusHead") {
            val rate = ByteBuffer.wrap(id, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
            info = info.copy(sampleRate = rate)
        } else if (id.size >= 16 && String(id, 1, 6, Charsets.ISO_8859_1) == "vorbis") {
            info = info.copy(sampleRate = ByteBuffer.wrap(id, 12, 4).order(ByteOrder.LITTLE_ENDIAN).int)
        }
        val off = when {
            cm.size > 8 && String(cm, 0, 8, Charsets.ISO_8859_1) == "OpusTags" -> 8
            cm.size > 7 && String(cm, 1, 6, Charsets.ISO_8859_1) == "vorbis" -> 7
            else -> return info
        }
        return fromComments(parseVorbisComment(cm, off), info)
    }

    // ---------------------------------------------------------------- ID3v2 (MP3, also AIFF/WAV id3 chunks)
    private fun syncsafe(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int) =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)

    private fun decodeText(enc: Int, b: ByteArray, from: Int, to: Int): String {
        if (from >= to) return ""
        val cs = when (enc) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
        return String(b, from, to - from, cs).trimEnd('\u0000')
    }

    /** Index just past the terminator of a string in [enc] starting at [from]. */
    private fun skipString(enc: Int, b: ByteArray, from: Int, to: Int): Int {
        var i = from
        if (enc == 1 || enc == 2) {
            while (i + 1 < to) { if (b[i].toInt() == 0 && b[i + 1].toInt() == 0) return i + 2; i += 2 }
        } else {
            while (i < to) { if (b[i].toInt() == 0) return i + 1; i++ }
        }
        return to
    }

    /** Reads an ID3v2 tag that starts at [base] (0 for MP3; DSF stores one at its metadata pointer). */
    fun readId3At(src: SeekableSource, base: Long): TagInfo = readId3(src, base)

    private fun readId3(src: SeekableSource, base: Long = 0L): TagInfo {
        val h = src.read(base, 10)
        if (h.size < 10 || String(h, 0, 3, Charsets.ISO_8859_1) != "ID3") return TagInfo()
        val ver = h[3].toInt()
        val flags = h[5].toInt()
        val size = syncsafe(h, 6)
        if (size <= 0 || size > MAX_BLOCK * 4) return TagInfo()
        var b = src.read(base + 10, size)
        val text = HashMap<String, String>()
        if (ver < 4 && (flags and 0x80) != 0) b = unsync(b)
        var p = 0
        if ((flags and 0x40) != 0 && b.size >= 4) p += if (ver >= 4) syncsafe(b, 0) else be32(b, 0) + 4
        val txxx = HashMap<String, String>()
        var lyrics: String? = null
        while (p + 10 <= b.size) {
            val id = String(b, p, 4, Charsets.ISO_8859_1)
            if (id[0] == '\u0000') break
            val fs = if (ver >= 4) syncsafe(b, p + 4) else be32(b, p + 4)
            val start = p + 10
            val end = (start + fs).coerceAtMost(b.size)
            if (fs <= 0 || start >= b.size) break
            val enc = b[start].toInt() and 0xFF
            when (id) {
                "TIT2", "TPE1", "TALB", "TRCK" -> text[id] = decodeText(enc, b, start + 1, end)
                "TXXX" -> {
                    val descEnd = skipString(enc, b, start + 1, end)
                    val desc = decodeText(enc, b, start + 1, descEnd).trimEnd('\u0000').uppercase()
                    txxx[desc] = decodeText(enc, b, descEnd, end)
                }
                "USLT" -> if (lyrics == null) {
                    val descEnd = skipString(enc, b, start + 4, end) // enc + 3-byte language + descriptor
                    lyrics = decodeText(enc, b, descEnd, end)
                }
            }
            p = end
        }
        return TagInfo(
            trackGainDb = txxx["REPLAYGAIN_TRACK_GAIN"]?.let { parseDb(it) },
            albumGainDb = txxx["REPLAYGAIN_ALBUM_GAIN"]?.let { parseDb(it) },
            trackPeak = txxx["REPLAYGAIN_TRACK_PEAK"]?.trim()?.toFloatOrNull(),
            albumPeak = txxx["REPLAYGAIN_ALBUM_PEAK"]?.trim()?.toFloatOrNull(),
            lyrics = lyrics?.takeIf { it.isNotBlank() },
            title = text["TIT2"]?.takeIf { it.isNotBlank() },
            artist = text["TPE1"]?.takeIf { it.isNotBlank() },
            album = text["TALB"]?.takeIf { it.isNotBlank() },
            trackNumber = text["TRCK"]?.substringBefore('/')?.trim()?.toIntOrNull()
        )
    }

    private fun unsync(b: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(b.size)
        var i = 0
        while (i < b.size) {
            out.write(b[i].toInt())
            if ((b[i].toInt() and 0xFF) == 0xFF && i + 1 < b.size && b[i + 1].toInt() == 0) i++
            i++
        }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- MP4 / M4A
    private fun readMp4(src: SeekableSource): TagInfo {
        val ilst = findAtom(src, 0, src.size, listOf("moov", "udta", "meta", "ilst")) ?: return TagInfo()
        val free = HashMap<String, String>()
        var lyrics: String? = null
        var p = ilst.first
        while (p + 8 <= ilst.second) {
            val h = src.read(p, 8)
            val size = be32(h, 0).toLong()
            if (size < 8) break
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            val body = p + 8
            val end = p + size
            if (type == "----" && size < MAX_BLOCK) {
                val data = src.read(body, (size - 8).toInt())
                var name: String? = null
                var value: String? = null
                var q = 0
                while (q + 8 <= data.size) {
                    val s = be32(data, q)
                    if (s < 8 || q + s > data.size) break
                    when (String(data, q + 4, 4, Charsets.ISO_8859_1)) {
                        "name" -> name = String(data, q + 12, s - 12, Charsets.UTF_8)
                        "data" -> value = String(data, q + 16, s - 16, Charsets.UTF_8)
                    }
                    q += s
                }
                if (name != null && value != null) free[name.uppercase()] = value
            } else if (type == "©lyr" && size < MAX_BLOCK) {
                val data = src.read(body, (size - 8).toInt())
                if (data.size > 16) lyrics = String(data, 16, data.size - 16, Charsets.UTF_8)
            }
            p = end
        }
        return TagInfo(
            trackGainDb = free["REPLAYGAIN_TRACK_GAIN"]?.let { parseDb(it) },
            albumGainDb = free["REPLAYGAIN_ALBUM_GAIN"]?.let { parseDb(it) },
            trackPeak = free["REPLAYGAIN_TRACK_PEAK"]?.trim()?.toFloatOrNull(),
            albumPeak = free["REPLAYGAIN_ALBUM_PEAK"]?.trim()?.toFloatOrNull(),
            lyrics = lyrics
        )
    }

    /** Returns the body range of the atom at [path] inside [start, end). */
    private fun findAtom(src: SeekableSource, start: Long, end: Long, path: List<String>): Pair<Long, Long>? {
        var p = start
        while (p + 8 <= end) {
            val h = src.read(p, 16)
            if (h.size < 8) return null
            var size = be32(h, 0).toLong() and 0xFFFFFFFFL
            var header = 8L
            if (size == 1L && h.size >= 16) {
                size = ByteBuffer.wrap(h, 8, 8).order(ByteOrder.BIG_ENDIAN).long; header = 16
            } else if (size == 0L) size = end - p
            if (size < header) return null
            val type = String(h, 4, 4, Charsets.ISO_8859_1)
            if (type == path[0]) {
                var bodyStart = p + header
                if (type == "meta") bodyStart += 4 // version + flags
                return if (path.size == 1) bodyStart to (p + size) else findAtom(src, bodyStart, p + size, path.drop(1))
            }
            p += size
        }
        return null
    }
}
