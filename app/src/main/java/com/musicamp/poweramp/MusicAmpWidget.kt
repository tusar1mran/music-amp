package com.musicamp.poweramp

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 4x1 resizable home-screen widget: album art, title / artist and previous / play-pause / next.
 * Buttons start [PlaybackService] actions; tapping the art or text opens [MainActivity].
 * [PlaybackService.render] pushes every state change through [push]; [onUpdate] draws the
 * current state from the controller when the launcher asks (e.g. widget just added).
 */
class MusicAmpWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val controller = (context.applicationContext as? PowerampApp)?.controller
        val stream = controller?.currentStream
        val track = if (stream == null) controller?.currentTrack else null
        val title = stream?.title ?: track?.title.orEmpty()
        val artist = stream?.genre ?: track?.artist.orEmpty()
        val playing = controller?.isPlaying == true
        val cached = if (track != null) controller?.getCachedArt(track.id) else null

        lastKey = null
        appWidgetManager.updateAppWidget(appWidgetIds, buildViews(context, title, artist, playing, cached))

        // Artwork not cached yet: load it off the main thread within the broadcast's time budget
        if (cached == null && track != null && controller != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val art = withTimeoutOrNull(8_000L) { controller.loadArt(track) }
                    if (art != null && controller.currentTrack?.id == track.id && controller.currentStream == null) {
                        push(context, title, artist, controller.isPlaying, art)
                    }
                } catch (_: Exception) {
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        private const val ART_MAX_PX = 256

        private var lastKey: List<Any?>? = null
        private var lastArtSource: Bitmap? = null
        private var lastArtScaled: Bitmap? = null

        /** Redraws every placed widget; cheap no-op when none are placed or nothing changed. */
        fun push(context: Context, title: String, artist: String, playing: Boolean, art: Bitmap?) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(context, MusicAmpWidget::class.java))
                if (ids.isEmpty()) return
                val key = listOf(title, artist, playing, art?.let { System.identityHashCode(it) })
                if (key == lastKey) return
                lastKey = key
                manager.updateAppWidget(ids, buildViews(context, title, artist, playing, art))
            } catch (_: Exception) {
                // Launcher / widget host unavailable: nothing to draw into
            }
        }

        private fun buildViews(context: Context, title: String, artist: String, playing: Boolean, art: Bitmap?): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_musicamp)
            views.setTextViewText(R.id.widget_title, title.ifBlank { "MusicAmp" })
            views.setTextViewText(R.id.widget_artist, if (title.isBlank()) "Tap to open" else artist)

            val scaled = scaledArt(art)
            if (scaled != null) views.setImageViewBitmap(R.id.widget_art, scaled)
            else views.setImageViewResource(R.id.widget_art, R.drawable.widget_art_placeholder)

            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play)
            views.setContentDescription(R.id.widget_play, if (playing) "Pause" else "Play")

            val open = PendingIntent.getActivity(
                context, 100,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_art, open)
            views.setOnClickPendingIntent(R.id.widget_info, open)
            views.setOnClickPendingIntent(R.id.widget_prev, servicePi(context, PlaybackService.ACTION_PREV, 101))
            views.setOnClickPendingIntent(R.id.widget_play, servicePi(context, PlaybackService.ACTION_TOGGLE, 102))
            views.setOnClickPendingIntent(R.id.widget_next, servicePi(context, PlaybackService.ACTION_NEXT, 103))
            return views
        }

        // minSdk 26: getForegroundService is always available; the service promotes itself on start
        private fun servicePi(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getForegroundService(
                context, code,
                PlaybackService.actionIntent(context, action)
                    .putExtra(PlaybackService.EXTRA_FOREGROUND_START, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        /** Scales to at most [ART_MAX_PX] (RemoteViews bitmap size limit) and rounds the corners. */
        private fun scaledArt(src: Bitmap?): Bitmap? {
            if (src == null) return null
            if (src === lastArtSource) lastArtScaled?.let { return it }
            return try {
                // Hardware bitmaps cannot be drawn into a software canvas
                val soft = if (src.config == Bitmap.Config.HARDWARE) src.copy(Bitmap.Config.ARGB_8888, false) else src
                val scale = minOf(1f, ART_MAX_PX.toFloat() / maxOf(soft.width, soft.height).coerceAtLeast(1))
                val w = (soft.width * scale).toInt().coerceAtLeast(1)
                val h = (soft.height * scale).toInt().coerceAtLeast(1)
                val base = if (scale < 1f) Bitmap.createScaledBitmap(soft, w, h, true) else soft
                val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = BitmapShader(base, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                }
                val radius = minOf(w, h) * 0.12f
                Canvas(out).drawRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, paint)
                if (base !== src && base !== soft) base.recycle()
                lastArtSource = src
                lastArtScaled = out
                out
            } catch (_: Exception) {
                null
            }
        }
    }
}
