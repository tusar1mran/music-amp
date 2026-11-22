package com.musicamp.poweramp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
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
 * [MediaSession], and mirrors the controller's state.
 */
class PlaybackService : Service() {

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
    private var art: Bitmap? = null
    private var artTrackId = -2L
    private lateinit var controller: PowerampController

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
                    stopSelf()
                }
            })
            s.isActive = true
        }

        // startForeground() must be called promptly after startForegroundService()
        promoteToForeground(buildNotification(currentSnap()))

        collectJob = scope.launch {
            snapshotFlow { currentSnap() }.collect { render(it) }
        }
    }

    private fun currentSnap(): Snap {
        val t = controller.currentTrack
        return Snap(
            playing = controller.isPlaying,
            trackId = t?.id ?: -1L,
            title = t?.title.orEmpty(),
            artist = t?.artist.orEmpty(),
            album = t?.album.orEmpty(),
            durationMs = controller.durationMs,
            epoch = controller.playbackEpoch
        )
    }

    private fun render(snap: Snap) {
        // Artwork loads lazily; the notification is rebuilt once it arrives
        if (snap.trackId != artTrackId) {
            artTrackId = snap.trackId
            art = null
            artJob?.cancel()
            val track = controller.currentTrack
            if (track != null && controller.albumArtOnLockscreen) {
                artJob = scope.launch {
                    art = controller.loadArt(track)
                    if (artTrackId == track.id) render(currentSnap())
                }
            }
        }

        val s = session ?: return
        s.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snap.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snap.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snap.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snap.durationMs)
                .also { b -> art?.let { b.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it) } }
                .build()
        )
        val actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or
            PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_SEEK_TO or
            PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND or PlaybackState.ACTION_STOP
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

        val notification = buildNotification(snap)
        if (snap.playing) {
            promoteToForeground(notification)
        } else {
            if (inForeground) {
                stopForeground(STOP_FOREGROUND_DETACH) // keep the notification, but let the user swipe it away
                inForeground = false
            }
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
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
        } catch (_: Exception) {
            // Background-start restrictions: keep running as a plain service
        }
    }

    private fun servicePi(action: String, code: Int): PendingIntent =
        PendingIntent.getService(
            this, code, Intent(this, PlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun buildNotification(snap: Snap): Notification {
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
        art?.let { b.setLargeIcon(it) }
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
        when (intent?.action) {
            ACTION_TOGGLE -> controller.togglePlayPause()
            ACTION_NEXT -> controller.nextTrack()
            ACTION_PREV -> controller.previousTrack()
            ACTION_DISMISSED -> if (!controller.isPlaying) stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!controller.isPlaying) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        collectJob?.cancel()
        artJob?.cancel()
        scope.cancel()
        session?.run {
            isActive = false
            release()
        }
        session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 4242
        private const val ACTION_TOGGLE = "com.musicamp.poweramp.TOGGLE"
        private const val ACTION_NEXT = "com.musicamp.poweramp.NEXT"
        private const val ACTION_PREV = "com.musicamp.poweramp.PREV"
        private const val ACTION_DISMISSED = "com.musicamp.poweramp.DISMISSED"

        fun ensureStarted(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
            } catch (_: Exception) {
                // Not allowed to start from the background right now; playback itself still works
            }
        }
    }
}
