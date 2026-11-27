package com.musicamp.poweramp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.MediaDescription
import android.media.MediaMetadata
import android.media.VolumeProvider
import android.media.browse.MediaBrowser
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import androidx.compose.runtime.snapshotFlow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground media service: keeps audio alive in the background, shows the media notification
 * (previous / play-pause / next), exposes lock-screen and Bluetooth/headset controls through a
 * [MediaSession], mirrors the controller's state, and serves the library to Android Auto and other
 * media browsers (browse tree: songs, albums, artists, playlists, queue, radio streams).
 */
class PlaybackService : MediaBrowserService() {

    private data class Snap(
        val playing: Boolean,
        val trackId: Long,
        val title: String,
        val artist: String,
        val album: String,
        val durationMs: Long,
        val epoch: Int
    )

    private val scope = CoroutineScope(Dispatchers.Main)
    private var collectJob: Job? = null
    private var artJob: Job? = null
    private var session: MediaSession? = null
    private var inForeground = false
    /** True once the service was started with startService/startForegroundService (not merely bound). */
    private var started = false
    /** True once a playback notification has been posted; a browse-only bind never posts one. */
    private var notificationShown = false
    private var art: Bitmap? = null
    private var artTrackId = -2L
    private var dvcProvider: VolumeProvider? = null
    private lateinit var controller: PowerampController

    private val localAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    override fun onCreate() {
        super.onCreate()
        controller = (application as PowerampApp).controller
        createChannel()

        session = MediaSession(this, "MusicAmp").also { s ->
            s.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = controller.play()
                override fun onPause() = controller.pause()
                override fun onSkipToNext() = controller.nextTrack()
                override fun onSkipToPrevious() = controller.previousTrack()
                override fun onSeekTo(pos: Long) = controller.seekToMs(pos)
                override fun onFastForward() = controller.seekRelative(10_000L)
                override fun onRewind() = controller.seekRelative(-10_000L)
                override fun onStop() {
                    controller.pause()
                    stopSelf() // the framework keeps the service alive while browsers are still bound
                }

                override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean {
                    // "Headset buttons" off: swallow wired/Bluetooth media-button events
                    if (!controller.headsetButtonsEnabled) return true
                    return super.onMediaButtonEvent(mediaButtonIntent)
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    playFromMediaId(mediaId ?: return)
                }

                override fun onPlayFromSearch(query: String?, extras: Bundle?) {
                    playFromSearch(query, extras)
                }
            })
            s.setPlaybackToLocal(localAttributes)
            s.isActive = true
        }
        sessionToken = session?.sessionToken

        // Only become foreground here when something is actually playing; a media browser
        // (e.g. Android Auto) that merely binds to browse must not get a playing notification.
        if (controller.isPlaying) promoteToForeground(buildNotification(currentSnap()))

        collectJob = scope.launch {
            snapshotFlow { currentSnap() }.collect { render(it) }
        }
        // Direct Volume Control: route volume keys to the app's software volume while it is active
        scope.launch {
            snapshotFlow { controller.dvcActive }.collect { applyVolumeHandling(it) }
        }
        scope.launch {
            snapshotFlow { controller.volumePercent }.collect { v ->
                dvcProvider?.currentVolume = v.coerceIn(0, 100)
            }
        }
    }

    private fun applyVolumeHandling(dvcActive: Boolean) {
        val s = session ?: return
        if (dvcActive) {
            if (dvcProvider != null) return
            val provider = object : VolumeProvider(
                VOLUME_CONTROL_ABSOLUTE, 100, controller.volumePercent.coerceIn(0, 100)
            ) {
                override fun onSetVolumeTo(volume: Int) {
                    controller.setDvcVolumePercent(volume)
                    currentVolume = controller.volumePercent.coerceIn(0, 100)
                }

                override fun onAdjustVolume(direction: Int) {
                    controller.adjustDvcVolume(direction.coerceIn(-1, 1))
                    currentVolume = controller.volumePercent.coerceIn(0, 100)
                }
            }
            dvcProvider = provider
            s.setPlaybackToRemote(provider)
        } else {
            if (dvcProvider == null) return
            dvcProvider = null
            s.setPlaybackToLocal(localAttributes)
        }
    }

    private fun currentSnap(): Snap {
        val t = controller.currentTrack
        val stream = controller.currentStream
        return Snap(
            playing = controller.isPlaying,
            trackId = if (stream != null) -1L else t?.id ?: -1L,
            title = stream?.title ?: t?.title.orEmpty(),
            artist = if (stream != null) stream.genre else t?.artist.orEmpty(),
            album = if (stream != null) "Radio stream" else t?.album.orEmpty(),
            durationMs = controller.durationMs,
            epoch = controller.playbackEpoch
        )
    }

    private fun render(snap: Snap) {
        // Artwork loads lazily; the notification and widget are rebuilt once it arrives.
        // It is always loaded for the home-screen widget; the lock-screen setting only gates the session/notification.
        if (snap.trackId != artTrackId) {
            artTrackId = snap.trackId
            art = null
            artJob?.cancel()
            val track = controller.currentTrack
            if (track != null && snap.trackId == track.id) {
                artJob = scope.launch {
                    art = controller.loadArt(track)
                    if (artTrackId == track.id) render(currentSnap())
                }
            }
        }
        val lockscreenArt = if (controller.albumArtOnLockscreen) art else null

        MusicAmpWidget.push(this, snap.title, snap.artist, snap.playing, art)

        val s = session ?: return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snap.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snap.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snap.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snap.durationMs)
                .also { b -> lockscreenArt?.let { b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP or
            PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH
        s.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (snap.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    controller.currentPositionMs,
                    if (snap.playing) 1f else 0f
                )
                .build()
        )

        if (snap.playing) {
            promoteToForeground(buildNotification(snap, lockscreenArt))
        } else {
            if (inForeground) {
                stopForeground(STOP_FOREGROUND_DETACH) // keep the notification, but let the user swipe it away
                inForeground = false
            }
            // A browse-only bind (nothing ever played or started) stays silent
            if (notificationShown || started) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(snap, lockscreenArt))
                notificationShown = true
            }
        }
    }

    private fun promoteToForeground(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
            inForeground = true
            notificationShown = true
        } catch (_: Exception) {
            // Background-start restrictions: keep running as a plain service
        }
    }

    private fun servicePi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code,
            Intent(this, PlaybackService::class.java).setAction(action).putExtra(EXTRA_TOKEN, actionToken(this)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(
        snap: Snap,
        largeIcon: Bitmap? = if (controller.albumArtOnLockscreen) art else null
    ): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun action(icon: Int, label: String, pi: PendingIntent) =
            Notification.Action.Builder(Icon.createWithResource(this, icon), label, pi).build()

        val b = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_music)
            .setContentTitle(snap.title.ifBlank { "MusicAmp" })
            .setContentText(snap.artist)
            .setSubText(snap.album)
            .setContentIntent(open)
            .setDeleteIntent(servicePi(ACTION_DISMISSED, 4))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setOngoing(snap.playing)
            .addAction(action(android.R.drawable.ic_media_previous, "Previous", servicePi(ACTION_PREV, 1)))
            .addAction(
                if (snap.playing) action(android.R.drawable.ic_media_pause, "Pause", servicePi(ACTION_TOGGLE, 2))
                else action(android.R.drawable.ic_media_play, "Play", servicePi(ACTION_TOGGLE, 2))
            )
            .addAction(action(android.R.drawable.ic_media_next, "Next", servicePi(ACTION_NEXT, 3)))
            .setStyle(
                Notification.MediaStyle()
                    .setMediaSession(session?.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
        largeIcon?.let { b.setLargeIcon(it) }
        return b.build()
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Now playing controls"
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started = true
        // startForegroundService() requires startForeground() promptly, even if the action ends up pausing
        if (intent?.getBooleanExtra(EXTRA_FOREGROUND_START, false) == true || controller.isPlaying) {
            promoteToForeground(buildNotification(currentSnap()))
        }
        // The service is exported for media browsers, so only honour transport actions carrying our
        // private token (present only in this app's own notification / widget PendingIntents)
        val trusted = intent?.getStringExtra(EXTRA_TOKEN) == actionToken(this)
        if (trusted) when (intent?.action) {
            ACTION_TOGGLE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.nextTrack()
            ACTION_PREV -> controller.previousTrack()
            ACTION_DISMISSED -> if (!controller.isPlaying) stopSelf()
        }
        // Re-render after the action so a no-op (e.g. empty library) still leaves the foreground state
        scope.launch { if (session != null) render(currentSnap()) }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!controller.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    // ==========================================================================================
    // Media browsing (Android Auto, Bluetooth / car head units, other MediaBrowser clients)
    // ==========================================================================================

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        if (clientPackageName == ANDROID_AUTO_PACKAGE && !controller.androidAutoEnabled) return null
        // Only trusted clients may list the library; any other app can connect for transport controls only
        val trusted = clientUid == android.os.Process.myUid() || clientUid == android.os.Process.SYSTEM_UID ||
            clientPackageName in TRUSTED_BROWSERS
        if (!trusted) return BrowserRoot(ID_EMPTY_ROOT, null)
        val extras = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
        }
        return BrowserRoot(ID_ROOT, extras)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowser.MediaItem>>) {
        if (parentId == ID_EMPTY_ROOT) {
            result.sendResult(mutableListOf())
            return
        }
        result.sendResult(childrenOf(parentId)?.toMutableList())
    }

    private fun browsable(id: String, title: String, subtitle: String? = null): MediaBrowser.MediaItem =
        MediaBrowser.MediaItem(
            MediaDescription.Builder().setMediaId(id).setTitle(title).setSubtitle(subtitle).build(),
            MediaBrowser.MediaItem.FLAG_BROWSABLE
        )

    private fun trackItem(t: AudioTrack, parentId: String): MediaBrowser.MediaItem =
        MediaBrowser.MediaItem(
            MediaDescription.Builder()
                .setMediaId("$PREFIX_TRACK${t.id}$PARENT_SEP$parentId")
                .setTitle(t.title)
                .setSubtitle(listOf(t.artist, t.album).filter { it.isNotBlank() }.joinToString(" • "))
                .build(),
            MediaBrowser.MediaItem.FLAG_PLAYABLE
        )

    private fun streamItem(s: StreamItem): MediaBrowser.MediaItem =
        MediaBrowser.MediaItem(
            MediaDescription.Builder().setMediaId("$PREFIX_STREAM${s.id}").setTitle(s.title).setSubtitle(s.genre).build(),
            MediaBrowser.MediaItem.FLAG_PLAYABLE
        )

    private fun childrenOf(parentId: String): List<MediaBrowser.MediaItem>? {
        val tracks = controller.allTracks
        return when {
            parentId == ID_ROOT -> listOf(
                browsable(ID_ALL, "All songs", "${tracks.size} songs"),
                browsable(ID_ALBUMS, "Albums"),
                browsable(ID_ARTISTS, "Artists"),
                browsable(ID_PLAYLISTS, "Playlists"),
                browsable(ID_QUEUE, "Queue", "${controller.queueTracks.size} songs"),
                browsable(ID_STREAMS, "Radio streams")
            )
            parentId == ID_ALBUMS -> tracks.groupBy { it.album }.entries
                .sortedBy { it.key.lowercase() }
                .take(MAX_ITEMS)
                .map { (album, list) -> browsable(PREFIX_ALBUM + Uri.encode(album), album, list.first().albumArtist) }
            parentId == ID_ARTISTS -> tracks.groupBy { it.artist }.entries
                .sortedBy { it.key.lowercase() }
                .take(MAX_ITEMS)
                .map { (artist, list) -> browsable(PREFIX_ARTIST + Uri.encode(artist), artist, "${list.size} songs") }
            parentId == ID_PLAYLISTS -> controller.playlists.take(MAX_ITEMS)
                .map { browsable("$PREFIX_PLAYLIST${it.id}", it.name, "${it.trackIds.size} songs") }
            parentId == ID_STREAMS -> controller.streams.take(MAX_ITEMS).map { streamItem(it) }
            else -> tracksOf(parentId)?.take(MAX_ITEMS)?.map { trackItem(it, parentId) }
        }
    }

    /** The playable track list behind a browse node, also used as the play context. */
    private fun tracksOf(parentId: String): List<AudioTrack>? {
        val tracks = controller.allTracks
        return when {
            parentId == ID_ALL -> tracks.toList()
            parentId == ID_QUEUE -> controller.queueTracks.toList()
            parentId.startsWith(PREFIX_ALBUM) -> {
                val album = Uri.decode(parentId.removePrefix(PREFIX_ALBUM))
                tracks.filter { it.album == album }.sortedWith(compareBy({ it.discNumber }, { it.trackNumber }))
            }
            parentId.startsWith(PREFIX_ARTIST) -> {
                val artist = Uri.decode(parentId.removePrefix(PREFIX_ARTIST))
                tracks.filter { it.artist == artist }
            }
            parentId.startsWith(PREFIX_PLAYLIST) -> {
                val id = parentId.removePrefix(PREFIX_PLAYLIST).toLongOrNull() ?: return null
                val pl = controller.playlists.firstOrNull { it.id == id } ?: return null
                val byId = tracks.associateBy { it.id }
                pl.trackIds.mapNotNull { byId[it] }
            }
            else -> null
        }
    }

    private fun playFromMediaId(mediaId: String) {
        when {
            mediaId.startsWith(PREFIX_STREAM) -> {
                val id = mediaId.removePrefix(PREFIX_STREAM).toLongOrNull() ?: return
                controller.streams.firstOrNull { it.id == id }?.let { controller.playStream(it) }
            }
            mediaId.startsWith(PREFIX_TRACK) -> {
                val body = mediaId.removePrefix(PREFIX_TRACK)
                val id = body.substringBefore(PARENT_SEP).toLongOrNull() ?: return
                val parent = body.substringAfter(PARENT_SEP, "")
                val track = controller.allTracks.firstOrNull { it.id == id } ?: return
                val context = tracksOf(parent)?.takeIf { list -> list.any { it.id == id } }
                controller.playTrack(track, context)
            }
            else -> tracksOf(mediaId)?.firstOrNull()?.let { controller.playTrack(it, tracksOf(mediaId)) }
        }
    }

    private fun playFromSearch(query: String?, extras: Bundle?) {
        val q = query?.trim().orEmpty()
        if (q.isEmpty() && extras?.getString(MediaStore.EXTRA_MEDIA_TITLE).isNullOrBlank() &&
            extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST).isNullOrBlank() &&
            extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM).isNullOrBlank()
        ) {
            // "Play music" with no specifics: resume / start whatever is loaded
            controller.play()
            return
        }
        val matches = searchTracks(q, extras)
        if (matches.isNotEmpty()) {
            controller.playTrack(matches.first(), matches)
            return
        }
        controller.streams.firstOrNull { it.title.contains(q, ignoreCase = true) }?.let {
            controller.playStream(it)
            return
        }
        controller.play()
    }

    /** Tracks matching a voice/text query, best matches (title, then artist, then album) first. */
    private fun searchTracks(query: String, extras: Bundle?): List<AudioTrack> {
        val tracks = controller.allTracks.toList()
        val title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.trim().orEmpty()
        val artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST)?.trim().orEmpty()
        val album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM)?.trim().orEmpty()
        // Structured voice extras narrow the search when present
        if (title.isNotEmpty() || artist.isNotEmpty() || album.isNotEmpty()) {
            val structured = tracks.filter { t ->
                (title.isEmpty() || t.title.contains(title, true)) &&
                    (artist.isEmpty() || t.artist.contains(artist, true) || t.albumArtist.contains(artist, true)) &&
                    (album.isEmpty() || t.album.contains(album, true))
            }
            if (structured.isNotEmpty()) {
                return if (title.isEmpty() && album.isNotEmpty()) {
                    structured.sortedWith(compareBy({ it.album }, { it.discNumber }, { it.trackNumber }))
                } else structured
            }
        }
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val byTitle = tracks.filter { it.title.contains(q, true) }
        val byArtist = tracks.filter { it.artist.contains(q, true) || it.albumArtist.contains(q, true) }
        val byAlbum = tracks.filter { it.album.contains(q, true) }
            .sortedWith(compareBy({ it.album }, { it.discNumber }, { it.trackNumber }))
        return (byTitle + byArtist + byAlbum).distinctBy { it.id }
    }

    override fun onDestroy() {
        collectJob?.cancel()
        artJob?.cancel()
        scope.cancel()
        session?.run {
            isActive = false
            release()
        }
        session = null
        dvcProvider = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Leave the widget showing the last track, paused
        val last = currentSnap()
        MusicAmpWidget.push(this, last.title, last.artist, false, art)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 4242
        internal const val ACTION_TOGGLE = "com.musicamp.poweramp.TOGGLE"
        internal const val ACTION_NEXT = "com.musicamp.poweramp.NEXT"
        internal const val ACTION_PREV = "com.musicamp.poweramp.PREV"
        private const val ACTION_DISMISSED = "com.musicamp.poweramp.DISMISSED"
        /** Set on intents delivered via startForegroundService(), which must call startForeground() promptly. */
        internal const val EXTRA_FOREGROUND_START = "com.musicamp.poweramp.extra.FOREGROUND_START"

        private const val ANDROID_AUTO_PACKAGE = "com.google.android.projection.gearhead"
        private const val ID_EMPTY_ROOT = "__empty__"
        /** System media surfaces allowed to browse the library (Auto, Assistant, Bluetooth, System UI, Wear). */
        private val TRUSTED_BROWSERS = setOf(
            ANDROID_AUTO_PACKAGE, "com.android.systemui", "com.android.bluetooth",
            "com.google.android.googlequicksearchbox", "com.google.android.wearable.app", "com.google.android.gms"
        )
        private const val MAX_ITEMS = 500

        private const val ID_ROOT = "root"
        private const val ID_ALL = "all"
        private const val ID_ALBUMS = "albums"
        private const val ID_ARTISTS = "artists"
        private const val ID_PLAYLISTS = "playlists"
        private const val ID_QUEUE = "queue"
        private const val ID_STREAMS = "streams"
        private const val PREFIX_ALBUM = "album:"
        private const val PREFIX_ARTIST = "artist:"
        private const val PREFIX_PLAYLIST = "playlist:"
        private const val PREFIX_TRACK = "track:"
        private const val PREFIX_STREAM = "stream:"
        /** Separates a track id from the browse node it was listed under (album/artist names are Uri-encoded, so never contain it). */
        private const val PARENT_SEP = "|"

        // Android Auto content-style extras (plain string keys; no androidx.media dependency)
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM = 1

        private const val EXTRA_TOKEN = "com.musicamp.poweramp.extra.TOKEN"
        @Volatile private var cachedToken: String? = null

        /**
         * Random per-install secret attached to our own PendingIntents. Persisted so widget buttons
         * created before a process restart keep working.
         */
        internal fun actionToken(context: Context): String {
            cachedToken?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences("playback_service", Context.MODE_PRIVATE)
            val token = prefs.getString("action_token", null) ?: java.util.UUID.randomUUID().toString().also {
                prefs.edit().putString("action_token", it).apply()
            }
            cachedToken = token
            return token
        }

        /** Intent for a transport action (toggle / next / prev) from this app's own widget or notification. */
        internal fun actionIntent(context: Context, action: String): Intent =
            Intent(context, PlaybackService::class.java)
                .setAction(action)
                .putExtra(EXTRA_TOKEN, actionToken(context))

        fun ensureStarted(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java).putExtra(EXTRA_FOREGROUND_START, true)
                )
            } catch (_: Exception) {
                // Not allowed to start from the background right now; playback itself still works
            }
        }
    }
}
