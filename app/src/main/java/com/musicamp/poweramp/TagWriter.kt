package com.musicamp.poweramp

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** A tag field the user can change. */
enum class TagField { TITLE, ARTIST, ALBUM, ALBUM_ARTIST, GENRE, YEAR, COMPOSER, TRACK_NUMBER }

/**
 * Only the fields the user actually changed. Fields that are absent are left exactly as they are in the
 * file. Values are never library placeholders such as "Unknown artist".
 */
data class TagChanges(val values: Map<TagField, String>) {
    val isEmpty: Boolean get() = values.isEmpty()
}

sealed class TagWriteResult {
    data object Written : TagWriteResult()
    data class Unsupported(val reason: String) : TagWriteResult()
    data class Failed(val reason: String) : TagWriteResult()
}

/**
 * Dependency-free tag writer for FLAC (VORBIS_COMMENT) and MP3 (ID3v2.3 / v2.4).
 *
 * Safety model, because this rewrites the user's own files:
 * 1. The original is copied to a backup in the cache, and the new file is built in a second temp file.
 *    Nothing is written to the original until the new file has been read back and checked: every changed
 *    field has its new value, and the audio that follows the metadata is byte-for-byte the same length.
 * 2. Replacing: when the folder is writable, the new file is written next to the original and renamed over
 *    it (atomic). Otherwise (content URIs under scoped storage) the original is overwritten in place, read
 *    back and checked, and restored from the backup if anything fails.
 * 3. Anything the parser does not fully understand (ID3 unsynchronisation, ID3v2.2, compressed or
 *    encrypted frames, malformed frame headers, an ID3 tag in front of a FLAC stream) is refused rather
 *    than written.
 */
object TagWriter {

    private val PLACEHOLDERS = setOf(
        "unknown artist", "unknown album", "unknown genre", "unknown composer", "music", "electronic", ""
    )

    fun canWriteFormat(track: AudioTrack): Boolean {
        if (track.isCue || track.isStream) return false
        val ext = track.formatExt.lowercase()
        return ext == "flac" || ext == "mp3"
    }

    /** Fields of [updated] that differ from [old], ignoring library placeholders and blanks. */
    fun diff(old: AudioTrack, updated: AudioTrack): TagChanges {
        val out = LinkedHashMap<TagField, String>()
        fun text(field: TagField, before: String, after: String) {
            val a = after.trim()
            if (a == before.trim()) return
            if (a.lowercase() in PLACEHOLDERS) return // a cleared field is filled with a placeholder by the dialog
            out[field] = a
        }
        text(TagField.TITLE, old.tagTitle.ifBlank { old.title }, updated.title)
        text(TagField.ARTIST, old.artist, updated.artist)
        text(TagField.ALBUM, old.album, updated.album)
        text(TagField.ALBUM_ARTIST, old.albumArtist, updated.albumArtist)
        text(TagField.GENRE, old.genre, updated.genre)
        text(TagField.COMPOSER, old.composer, updated.composer)
        if (updated.year != old.year && updated.year > 0) out[TagField.YEAR] = updated.year.toString()
        if (updated.trackNumber != old.trackNumber && updated.trackNumber > 0) out[TagField.TRACK_NUMBER] = updated.trackNumber.toString()
        return TagChanges(out)
    }

    fun writeToFile(context: Context, track: AudioTrack, changes: TagChanges): TagWriteResult {
        if (track.isCue) return TagWriteResult.Unsupported("Saved in MusicAmp only (CUE track tags live in the cue sheet)")
        if (track.isStream) return TagWriteResult.Unsupported("Saved in MusicAmp only (streams can't be written)")
        if (!canWriteFormat(track)) return TagWriteResult.Unsupported("Saved in MusicAmp only (this format can't be written yet)")
        if (changes.isEmpty) return TagWriteResult.Written

        var backup: File? = null
        var built: File? = null
        try {
            val sizeHint = track.fileSizeKb * 1024L
            if (context.cacheDir.usableSpace < sizeHint * 2 + 8L * 1024 * 1024) {
                return TagWriteResult.Failed("Not enough free storage to edit this file safely")
            }
            backup = File.createTempFile("tag_backup_", ".tmp", context.cacheDir)
            built = File.createTempFile("tag_new_", ".tmp", context.cacheDir)
            if (!copySourceTo(context, track, backup) || backup.length() < 12) {
                return TagWriteResult.Failed("Could not read the original audio file")
            }

            val head = ByteArray(12)
            FileInputStream(backup).use { readFully(it, head) }
            val ext = track.formatExt.lowercase()
            val isId3 = String(head, 0, 3, Charsets.ISO_8859_1) == "ID3"
            val isFlac = String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC"
            val audioBytes: Long = when {
                ext == "flac" && isFlac -> rewriteFlac(backup, built, changes)
                ext == "flac" && isId3 -> return TagWriteResult.Unsupported("Saved in MusicAmp only (this FLAC has an ID3 tag in front, which isn't supported)")
                ext == "mp3" && (isId3 || isMpegSync(head)) -> rewriteMp3(backup, built, changes)
                else -> return TagWriteResult.Unsupported("Saved in MusicAmp only (unrecognised file layout)")
            }

            // Check the new file before the original is touched
            val problem = verify(built, changes, audioBytes)
            if (problem != null) return TagWriteResult.Failed("$problem; original file left untouched")

            replaceOriginal(context, track, built, backup, changes, audioBytes)
            return TagWriteResult.Written
        } catch (e: UnsupportedLayout) {
            return TagWriteResult.Unsupported("Saved in MusicAmp only (${e.message})")
        } catch (e: OutOfMemoryError) {
            return TagWriteResult.Failed("File too large to edit")
        } catch (e: Exception) {
            return TagWriteResult.Failed(e.message ?: "Write error")
        } finally {
            backup?.delete()
            built?.delete()
        }
    }

    private class UnsupportedLayout(message: String) : Exception(message)

    // ---------------------------------------------------------------- copy / replace

    private fun copySourceTo(context: Context, track: AudioTrack, dest: File): Boolean {
        val uri = track.uri
        if (uri != null && uri.scheme != "http" && uri.scheme != "https") {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
                return true
            }
        }
        val f = File(track.filePath)
        if (track.filePath.isNotBlank() && f.canRead()) {
            FileInputStream(f).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output) } }
            return true
        }
        return false
    }

    private fun replaceOriginal(context: Context, track: AudioTrack, built: File, backup: File, changes: TagChanges, audioBytes: Long) {
        // 1. Atomic path: temp file in the same folder, then rename over the original
        val target = File(track.filePath)
        val dir = target.parentFile
        if (track.filePath.isNotBlank() && target.exists() && dir != null && dir.canWrite() && target.canWrite()) {
            val sibling = File(dir, ".${target.name}.musicamp-tmp")
            try {
                FileOutputStream(sibling).use { out ->
                    FileInputStream(built).use { it.copyTo(out) }
                    out.fd.sync()
                }
                if (sibling.length() == built.length() && sibling.renameTo(target)) return
            } catch (_: Exception) {
            } finally {
                if (sibling.exists()) sibling.delete()
            }
        }
        // 2. In-place overwrite through the content URI, checked afterwards, restored from the backup on failure
        val uri = track.uri ?: throw IllegalStateException("No writable location for this file")
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                FileInputStream(built).use { it.copyTo(out) }
                out.flush()
            } ?: throw IllegalStateException("Cannot open the file for writing")
            val check = File.createTempFile("tag_check_", ".tmp", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(check).use { input.copyTo(it) } }
                if (check.length() != built.length()) throw IllegalStateException("Written file has the wrong size")
                verify(check, changes, audioBytes)?.let { throw IllegalStateException(it) }
            } finally {
                check.delete()
            }
        } catch (e: Exception) {
            // Put the original bytes back
            try {
                context.contentResolver.openOutputStream(uri, "wt")?.use { out -> FileInputStream(backup).use { it.copyTo(out) } }
            } catch (_: Exception) {
                throw IllegalStateException("Write failed and the original could not be restored: ${e.message}")
            }
            throw IllegalStateException("Write failed, original restored (${e.message})")
        }
    }

    /** Returns a problem description, or null when [file] has every changed field and the expected audio size. */
    private fun verify(file: File, changes: TagChanges, audioBytes: Long): String? {
        val fields = try { readBack(file) } catch (e: Exception) { return "New file could not be read back" }
        val audioNow = fields.second
        if (audioNow != audioBytes) return "Audio data size changed"
        for ((field, value) in changes.values) {
            val got = fields.first[field] ?: return "${field.name.lowercase()} missing after write"
            if (got != value) return "${field.name.lowercase()} not written correctly"
        }
        return null
    }

    // ---------------------------------------------------------------- FLAC (VORBIS_COMMENT)

    private val FLAC_KEYS = mapOf(
        TagField.TITLE to "TITLE", TagField.ARTIST to "ARTIST", TagField.ALBUM to "ALBUM",
        TagField.ALBUM_ARTIST to "ALBUMARTIST", TagField.GENRE to "GENRE", TagField.YEAR to "DATE",
        TagField.COMPOSER to "COMPOSER", TagField.TRACK_NUMBER to "TRACKNUMBER"
    )

    private data class FlacBlock(val type: Int, val payload: ByteArray)

    /** Returns the metadata blocks and the byte offset where audio frames start. */
    private fun readFlacBlocks(input: InputStream): Pair<List<FlacBlock>, Long> {
        val magic = ByteArray(4)
        readFully(input, magic)
        if (String(magic, Charsets.ISO_8859_1) != "fLaC") throw UnsupportedLayout("not a FLAC stream")
        val blocks = ArrayList<FlacBlock>()
        var offset = 4L
        var last = false
        while (!last) {
            val hdr = ByteArray(4)
            readFully(input, hdr)
            last = (hdr[0].toInt() and 0x80) != 0
            val type = hdr[0].toInt() and 0x7F
            if (type == 127) throw UnsupportedLayout("invalid FLAC metadata")
            val len = ((hdr[1].toInt() and 0xFF) shl 16) or ((hdr[2].toInt() and 0xFF) shl 8) or (hdr[3].toInt() and 0xFF)
            val payload = ByteArray(len)
            readFully(input, payload)
            blocks.add(FlacBlock(type, payload))
            offset += 4 + len
            if (blocks.size > 1000) throw UnsupportedLayout("too many FLAC metadata blocks")
        }
        if (blocks.firstOrNull()?.type != 0) throw UnsupportedLayout("FLAC STREAMINFO is not first")
        return blocks to offset
    }

    /** Writes the edited FLAC to [dst]; returns the number of audio bytes after the metadata. */
    private fun rewriteFlac(src: File, dst: File, changes: TagChanges): Long {
        FileInputStream(src).use { input ->
            val (blocks, audioStart) = readFlacBlocks(input)
            val audioBytes = src.length() - audioStart
            val changedKeys = changes.values.keys.mapNotNull { FLAC_KEYS[it] }.toSet()

            var vendor = "MusicAmp"
            val kept = ArrayList<String>()
            var oldSpace = 0 // bytes taken by VORBIS_COMMENT + PADDING blocks, headers included
            var hadComment = false
            for (b in blocks) {
                when (b.type) {
                    4 -> {
                        if (hadComment) throw UnsupportedLayout("FLAC has two VORBIS_COMMENT blocks")
                        hadComment = true
                        oldSpace += 4 + b.payload.size
                        val (v, entries) = parseVorbisComment(b.payload)
                        vendor = v
                        entries.filterTo(kept) { e -> e.substringBefore('=').uppercase() !in changedKeys }
                    }
                    1 -> oldSpace += 4 + b.payload.size
                }
            }
            for ((field, value) in changes.values) kept.add("${FLAC_KEYS.getValue(field)}=$value")
            val vc = encodeVorbisComment(vendor, kept)

            // Keep every other block in its original order; the comment goes right after STREAMINFO
            val out = ArrayList<FlacBlock>()
            out.add(blocks[0])
            out.add(FlacBlock(4, vc))
            blocks.drop(1).filterTo(out) { it.type != 4 && it.type != 1 }
            val needed = 4 + vc.size
            val pad = if (oldSpace >= needed + 4) oldSpace - needed - 4 else 4096
            out.add(FlacBlock(1, ByteArray(pad)))

            FileOutputStream(dst).use { o ->
                o.write("fLaC".toByteArray(Charsets.ISO_8859_1))
                for ((i, b) in out.withIndex()) {
                    val len = b.payload.size
                    if (len > 0xFFFFFF) throw UnsupportedLayout("FLAC metadata block too large")
                    o.write(byteArrayOf(
                        ((b.type and 0x7F) or (if (i == out.lastIndex) 0x80 else 0)).toByte(),
                        (len shr 16).toByte(), (len shr 8).toByte(), len.toByte()
                    ))
                    o.write(b.payload)
                }
                input.copyTo(o) // audio frames, unchanged
            }
            return audioBytes
        }
    }

    private fun parseVorbisComment(payload: ByteArray): Pair<String, List<String>> {
        val bb = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        if (payload.size < 8) throw UnsupportedLayout("damaged VORBIS_COMMENT")
        val vendorLen = bb.int
        if (vendorLen < 0 || vendorLen > bb.remaining() - 4) throw UnsupportedLayout("damaged VORBIS_COMMENT")
        val vendor = String(payload, bb.position(), vendorLen, Charsets.UTF_8)
        bb.position(bb.position() + vendorLen)
        val count = bb.int
        if (count < 0 || count > 100_000) throw UnsupportedLayout("damaged VORBIS_COMMENT")
        val entries = ArrayList<String>(count)
        repeat(count) {
            if (bb.remaining() < 4) throw UnsupportedLayout("damaged VORBIS_COMMENT")
            val len = bb.int
            if (len < 0 || len > bb.remaining()) throw UnsupportedLayout("damaged VORBIS_COMMENT")
            entries.add(String(payload, bb.position(), len, Charsets.UTF_8))
            bb.position(bb.position() + len)
        }
        return vendor to entries
    }

    private fun encodeVorbisComment(vendor: String, comments: List<String>): ByteArray {
        val out = ByteArrayOutputStream()
        val vb = vendor.toByteArray(Charsets.UTF_8)
        out.write(le32(vb.size)); out.write(vb)
        out.write(le32(comments.size))
        for (c in comments) { val cb = c.toByteArray(Charsets.UTF_8); out.write(le32(cb.size)); out.write(cb) }
        return out.toByteArray()
    }

    // ---------------------------------------------------------------- MP3 (ID3v2.3 / v2.4)

    private fun isMpegSync(h: ByteArray) = (h[0].toInt() and 0xFF) == 0xFF && (h[1].toInt() and 0xE0) == 0xE0

    private fun id3FrameFor(field: TagField, major: Int): String = when (field) {
        TagField.TITLE -> "TIT2"; TagField.ARTIST -> "TPE1"; TagField.ALBUM -> "TALB"
        TagField.ALBUM_ARTIST -> "TPE2"; TagField.GENRE -> "TCON"; TagField.COMPOSER -> "TCOM"
        TagField.TRACK_NUMBER -> "TRCK"; TagField.YEAR -> if (major == 3) "TYER" else "TDRC"
    }

    private data class Id3Frame(val id: String, val flags: ByteArray, val body: ByteArray)

    /** Parses the ID3v2 tag at the start of [raf]. Returns (major version, frames, total tag size incl. header/footer). */
    private fun readId3(raf: RandomAccessFile): Triple<Int, List<Id3Frame>, Long> {
        val h = ByteArray(10)
        raf.seek(0); raf.readFully(h)
        val major = h[3].toInt() and 0xFF
        val flags = h[5].toInt() and 0xFF
        if (major != 3 && major != 4) throw UnsupportedLayout("ID3v2.$major tags aren't supported")
        if ((flags and 0x80) != 0) throw UnsupportedLayout("ID3 tag uses unsynchronisation")
        for (i in 6..9) if ((h[i].toInt() and 0x80) != 0) throw UnsupportedLayout("damaged ID3 header")
        val size = syncsafe(h, 6)
        val total = 10L + size + (if (major == 4 && (flags and 0x10) != 0) 10 else 0)
        if (total > raf.length()) throw UnsupportedLayout("damaged ID3 tag")
        val body = ByteArray(size)
        raf.readFully(body)
        var p = 0
        if ((flags and 0x40) != 0) {
            if (body.size < 4) throw UnsupportedLayout("damaged ID3 extended header")
            p = if (major == 4) syncsafe(body, 0) else be32(body, 0) + 4
            if (p < 0 || p > body.size) throw UnsupportedLayout("damaged ID3 extended header")
        }
        val frames = ArrayList<Id3Frame>()
        while (p + 10 <= body.size) {
            if (body[p].toInt() == 0) {
                // Padding must be all zeros to the end
                for (i in p until body.size) if (body[i].toInt() != 0) throw UnsupportedLayout("unexpected data after ID3 padding")
                break
            }
            val id = String(body, p, 4, Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) throw UnsupportedLayout("unrecognised ID3 frame")
            if (major == 4) for (i in 4..7) if ((body[p + i].toInt() and 0x80) != 0) throw UnsupportedLayout("non-standard ID3v2.4 frame sizes")
            val fs = if (major == 4) syncsafe(body, p + 4) else be32(body, p + 4)
            if (fs <= 0 || p + 10 + fs > body.size) throw UnsupportedLayout("damaged ID3 frame")
            val f1 = body[p + 9].toInt() and 0xFF
            // Compressed / encrypted frames (v2.3: 0x80/0x40, v2.4: 0x08/0x04) or v2.4 frame unsynchronisation (0x02)
            val format = if (major == 3) f1 and 0xC0 else f1 and 0x0E
            if (format != 0) throw UnsupportedLayout("ID3 tag has compressed or encrypted frames")
            frames.add(Id3Frame(id, byteArrayOf(body[p + 8], body[p + 9]), body.copyOfRange(p + 10, p + 10 + fs)))
            p += 10 + fs
        }
        return Triple(major, frames, total)
    }

    private fun textFrameBody(value: String, major: Int): ByteArray {
        val out = ByteArrayOutputStream()
        if (major == 4) {
            out.write(3); out.write(value.toByteArray(Charsets.UTF_8)) // UTF-8
        } else {
            out.write(1); out.write(value.toByteArray(Charsets.UTF_16)) // UTF-16 with BOM (v2.3 has no UTF-8)
        }
        return out.toByteArray()
    }

    /** Writes the edited MP3 to [dst]; returns the number of audio bytes after the tag. */
    private fun rewriteMp3(src: File, dst: File, changes: TagChanges): Long {
        RandomAccessFile(src, "r").use { raf ->
            val head = ByteArray(3)
            raf.readFully(head)
            val hasTag = String(head, Charsets.ISO_8859_1) == "ID3"
            val (major, frames, oldTotal) = if (hasTag) readId3(raf) else Triple(4, emptyList(), 0L)
            val audioBytes = raf.length() - oldTotal

            val replaced = changes.values.keys.map { id3FrameFor(it, major) }.toMutableSet()
            if (TagField.YEAR in changes.values) replaced += setOf("TYER", "TDRC", "TDAT")
            val out = ArrayList<Id3Frame>()
            frames.filterTo(out) { it.id !in replaced }
            for ((field, value) in changes.values) {
                out.add(Id3Frame(id3FrameFor(field, major), byteArrayOf(0, 0), textFrameBody(value, major)))
            }

            val frameBytes = ByteArrayOutputStream()
            for (f in out) {
                frameBytes.write(f.id.toByteArray(Charsets.ISO_8859_1))
                frameBytes.write(if (major == 4) syncsafeBytes(f.body.size) else be32Bytes(f.body.size))
                frameBytes.write(f.flags)
                frameBytes.write(f.body)
            }
            val fb = frameBytes.toByteArray()
            // Reuse the old tag size when the new frames fit, so most edits keep the file the same length
            val oldBody = (oldTotal - 10).toInt().coerceAtLeast(0)
            val bodySize = if (oldBody >= fb.size) oldBody else fb.size + 2048
            if (bodySize > 0x0FFFFFFF) throw UnsupportedLayout("ID3 tag too large")

            FileOutputStream(dst).use { o ->
                o.write("ID3".toByteArray(Charsets.ISO_8859_1))
                o.write(major); o.write(0); o.write(0) // no unsync, no extended header, no footer
                o.write(syncsafeBytes(bodySize))
                o.write(fb)
                o.write(ByteArray(bodySize - fb.size))
                raf.seek(oldTotal)
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = raf.read(buf)
                    if (n < 0) break
                    o.write(buf, 0, n)
                }
            }
            return audioBytes
        }
    }

    // ---------------------------------------------------------------- read-back for verification

    /** Reads the managed fields and the audio byte count from a file this writer produced. */
    private fun readBack(file: File): Pair<Map<TagField, String>, Long> {
        FileInputStream(file).use { input ->
            val head = ByteArray(4)
            readFully(input, head)
            if (String(head, Charsets.ISO_8859_1) == "fLaC") {
                FileInputStream(file).use { again ->
                    val (blocks, audioStart) = readFlacBlocks(again)
                    val map = HashMap<TagField, String>()
                    blocks.firstOrNull { it.type == 4 }?.let { b ->
                        val entries = parseVorbisComment(b.payload).second
                        for ((field, key) in FLAC_KEYS) {
                            entries.lastOrNull { it.substringBefore('=').equals(key, true) }?.let { map[field] = it.substringAfter('=') }
                        }
                    }
                    return map to (file.length() - audioStart)
                }
            }
        }
        RandomAccessFile(file, "r").use { raf ->
            val (major, frames, total) = readId3(raf)
            val map = HashMap<TagField, String>()
            for (field in TagField.entries) {
                val f = frames.lastOrNull { it.id == id3FrameFor(field, major) } ?: continue
                if (f.body.isEmpty()) continue
                val cs = when (f.body[0].toInt()) { 1 -> Charsets.UTF_16; 2 -> Charsets.UTF_16BE; 3 -> Charsets.UTF_8; else -> Charsets.ISO_8859_1 }
                map[field] = String(f.body, 1, f.body.size - 1, cs).trimEnd('\u0000')
            }
            return map to (raf.length() - total)
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun readFully(input: InputStream, b: ByteArray) {
        var r = 0
        while (r < b.size) {
            val n = input.read(b, r, b.size - r)
            if (n <= 0) throw UnsupportedLayout("file ends inside its metadata")
            r += n
        }
    }

    private fun le32(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()
    private fun be32Bytes(v: Int): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(v).array()
    private fun syncsafe(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0x7F) shl 21) or ((b[o + 1].toInt() and 0x7F) shl 14) or ((b[o + 2].toInt() and 0x7F) shl 7) or (b[o + 3].toInt() and 0x7F)
    private fun syncsafeBytes(v: Int): ByteArray =
        byteArrayOf(((v shr 21) and 0x7F).toByte(), ((v shr 14) and 0x7F).toByte(), ((v shr 7) and 0x7F).toByte(), (v and 0x7F).toByte())
    private fun be32(b: ByteArray, o: Int): Int =
        ((b[o].toInt() and 0xFF) shl 24) or ((b[o + 1].toInt() and 0xFF) shl 16) or ((b[o + 2].toInt() and 0xFF) shl 8) or (b[o + 3].toInt() and 0xFF)
}
