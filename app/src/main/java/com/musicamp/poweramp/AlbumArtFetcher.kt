package com.musicamp.poweramp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * Downloads missing album covers from the Cover Art Archive, looking the album up on MusicBrainz first.
 * Only the album title and artist name are sent. Results (and misses) are cached on disk, and MusicBrainz's
 * limit of one request per second is respected.
 */
object AlbumArtFetcher {
    private val mutex = Mutex()
    private var lastMbRequestAt = 0L
    private const val MISS_TTL_MS = 7L * 24 * 3600 * 1000
    private const val USER_AGENT = "MusicAmp/1.0 (Android music player)"

    private fun key(artist: String, album: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest("${artist.lowercase()}|${album.lowercase()}".toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }

    private fun dir(context: Context) = File(context.filesDir, "art").also { it.mkdirs() }

    /** Returns a cached or freshly downloaded cover, or null. Never throws. */
    suspend fun fetch(context: Context, artist: String, album: String, highRes: Boolean): Bitmap? = withContext(Dispatchers.IO) {
        if (album.isBlank() || album.equals("Unknown album", true)) return@withContext null
        val k = key(artist, album)
        val file = File(dir(context), "$k.jpg")
        val miss = File(dir(context), "$k.none")
        try {
            if (file.exists()) return@withContext decode(file, highRes)
            if (miss.exists() && System.currentTimeMillis() - miss.lastModified() < MISS_TTL_MS) return@withContext null
            val mbid = mutex.withLock {
                val wait = 1100L - (System.currentTimeMillis() - lastMbRequestAt)
                if (wait > 0) delay(wait)
                lastMbRequestAt = System.currentTimeMillis()
                lookupReleaseGroup(artist, album)
            }
            if (mbid == null) { miss.writeText(""); return@withContext null }
            val size = if (highRes) "1200" else "500"
            val bytes = get("https://coverartarchive.org/release-group/$mbid/front-$size", 10 * 1024 * 1024)
            if (bytes == null || bytes.isEmpty()) {
                miss.writeText(""); return@withContext null
            }
            val tmp = File(dir(context), "$k.tmp")
            tmp.writeBytes(bytes)
            tmp.renameTo(file)
            decode(file, highRes)
        } catch (_: Exception) {
            null // offline or service error: try again on a later visit
        }
    }

    private fun decode(file: File, highRes: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) { file.delete(); return null }
        val max = if (highRes) 1200 else 600
        var sample = 1
        while (bounds.outWidth / sample > max) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun lucene(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun lookupReleaseGroup(artist: String, album: String): String? {
        val q = buildString {
            append("releasegroup:").append(lucene(album))
            if (artist.isNotBlank() && !artist.equals("Unknown artist", true)) append(" AND artist:").append(lucene(artist))
        }
        val url = "https://musicbrainz.org/ws/2/release-group/?fmt=json&limit=1&query=" + URLEncoder.encode(q, "UTF-8")
        val body = get(url, 512 * 1024) ?: return null
        val groups = JSONObject(String(body, Charsets.UTF_8)).optJSONArray("release-groups") ?: return null
        val first = groups.optJSONObject(0) ?: return null
        if (first.optInt("score", 0) < 90) return null
        return first.optString("id").takeIf { it.length == 36 }
    }

    private fun get(url: String, maxBytes: Int): ByteArray? {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 12000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", USER_AGENT)
            c.setRequestProperty("Accept", "application/json, image/*")
            if (c.responseCode !in 200..299) return null
            c.inputStream.use { s ->
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) return null
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }
}
