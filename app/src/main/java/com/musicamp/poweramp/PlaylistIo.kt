package com.musicamp.poweramp

import java.io.File

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** A parsed entry from an M3U/M3U8/PLS playlist file. */
data class PlaylistEntry(
    val path: String,
    val extInfTitle: String? = null
)

/**
 * Pure playlist import/export helpers for M3U, M3U8 and PLS files.
 */
object PlaylistIo {

    /**
     * Serialises [tracks] to an M3U/M3U8 string.
     * CUE virtual tracks emit their image file path once per track with an `#EXTINF` title.
     */
    fun writeM3u(tracks: List<AudioTrack>, extended: Boolean = true): String {
        val sb = StringBuilder()
        if (extended) sb.appendLine("#EXTM3U")
        for (track in tracks) {
            if (extended) {
                val seconds = (track.durationMs / 1000L).coerceAtLeast(0L)
                val artist = track.artist.ifBlank { "Unknown Artist" }
                val title = track.title.ifBlank { track.fileName }
                sb.appendLine("#EXTINF:$seconds,$artist - $title")
            }
            val location = track.filePath.ifBlank { track.uri?.toString() ?: track.fileName }
            sb.appendLine(location)
        }
        return sb.toString()
    }

    /**
     * Decodes raw playlist bytes: UTF-8 for `.m3u8` (and tried first for all files),
     * falling back to Windows-1252 when strict UTF-8 decoding fails on `.m3u` / `.pls`.
     */
    fun decodePlaylistBytes(bytes: ByteArray, fileName: String? = null): String {
        if (bytes.isEmpty()) return ""
        // Strip UTF-8 BOM if present
        val offset = if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() &&
            bytes[2] == 0xBF.toByte()
        ) 3 else 0
        val slice = if (offset > 0) bytes.copyOfRange(offset, bytes.size) else bytes

        val forceUtf8 = fileName?.lowercase()?.endsWith(".m3u8") == true
        return try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(if (forceUtf8) CodingErrorAction.REPLACE else CodingErrorAction.REPORT)
                .onUnmappableCharacter(if (forceUtf8) CodingErrorAction.REPLACE else CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(slice)).toString()
        } catch (_: Exception) {
            String(slice, Charset.forName("windows-1252"))
        }
    }

    /** Parses an M3U/M3U8 playlist and returns the resolved path/URI strings. */
    fun parseM3u(text: String): List<String> = parseM3uEntries(text).map { it.path }

    /** Parses an M3U/M3U8 playlist, keeping `#EXTINF` titles alongside each path. */
    fun parseM3uEntries(text: String): List<PlaylistEntry> {
        val result = mutableListOf<PlaylistEntry>()
        var pendingTitle: String? = null
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                    val comma = line.indexOf(',')
                    if (comma >= 0 && comma + 1 < line.length) {
                        pendingTitle = line.substring(comma + 1).trim().ifEmpty { null }
                    }
                }
                continue
            }
            val cleaned = normaliseEntryPath(line)
            if (cleaned.isNotEmpty()) {
                result.add(PlaylistEntry(path = cleaned, extInfTitle = pendingTitle))
            }
            pendingTitle = null
        }
        return result
    }

    /** Parses a PLS playlist and returns the `FileN=` paths ordered by index N. */
    fun parsePls(text: String): List<String> = parsePlsEntries(text).map { it.path }

    /** Parses a PLS playlist, pairing `FileN=` entries with optional `TitleN=` metadata. */
    fun parsePlsEntries(text: String): List<PlaylistEntry> {
        val files = sortedMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";") || line.startsWith("[")) continue
            val eq = line.indexOf('=')
            if (eq <= 0) continue
            val key = line.substring(0, eq).trim()
            val value = line.substring(eq + 1).trim()
            if (value.isEmpty()) continue
            when {
                key.startsWith("File", ignoreCase = true) -> {
                    val idx = key.substring(4).toIntOrNull() ?: continue
                    val cleaned = normaliseEntryPath(value)
                    if (cleaned.isNotEmpty()) files[idx] = cleaned
                }
                key.startsWith("Title", ignoreCase = true) -> {
                    val idx = key.substring(5).toIntOrNull() ?: continue
                    titles[idx] = value
                }
            }
        }
        return files.entries.map { (idx, path) ->
            PlaylistEntry(path = path, extInfTitle = titles[idx])
        }
    }

    /** Returns true if the text or file name looks like a PLS file. */
    fun isPls(text: String, fileName: String? = null): Boolean {
        if (fileName?.lowercase()?.endsWith(".pls") == true) return true
        val firstNonBlank = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return false
        return firstNonBlank.equals("[playlist]", ignoreCase = true)
    }

    /** Strips `file://` URL schemes, URL-decodes percent escapes, and normalises slashes. */
    fun normaliseEntryPath(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("file://", ignoreCase = true)) {
            s = s.substring(7)
            if (s.startsWith("localhost/", ignoreCase = true)) {
                s = s.substring(9)
            }
            s = try {
                android.net.Uri.decode(s)
            } catch (_: Exception) {
                s
            }
        }
        return s.replace('\\', '/')
    }

    /** Resolves a possibly relative [entryPath] against [baseFolder], collapsing `.` and `..`. */
    fun resolveRelativePath(baseFolder: String, entryPath: String): String {
        val normEntry = normaliseEntryPath(entryPath)
        if (normEntry.startsWith("/")) return normEntry
        return try {
            File(baseFolder, normEntry).canonicalPath.replace('\\', '/')
        } catch (_: Exception) {
            "${baseFolder.trimEnd('/')}/$normEntry"
        }
    }

    /** Extracts just the file name component of a normalised path. */
    fun fileNameOf(path: String): String {
        val norm = normaliseEntryPath(path)
        val slash = norm.lastIndexOf('/')
        return if (slash >= 0) norm.substring(slash + 1) else norm
    }
}
