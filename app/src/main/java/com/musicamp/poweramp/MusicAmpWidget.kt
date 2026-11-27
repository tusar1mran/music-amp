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
 * [MusicAmpWidget4x2] and [MusicAmpWidget2x2] extend this provider and reuse all state/art logic.
 */
open class MusicAmpWidget : AppWidgetProvider() {

    protected open val layoutRes: Int get() = R.layout.widget_musicamp

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val controller = (context.applicationContext as? PowerampApp)?.controller
        val stream = controller?.currentStream
        val track = if (stream == null) controller?.currentTrack else null
        val title = stream?.title ?: track?.title.orEmpty()
        val artist = stream?.genre ?: track?.artist.orEmpty()
        val playing = controller?.isPlaying == true
        val progress = if (controller != null && controller.durationMs > 0L) {
            ((controller.currentPositionMs.toFloat() / controller.durationMs) * 1000f).toInt().coerceIn(0, 1000)
        } else 0
        val cached = if (track != null) controller?.getCachedArt(track.id) else null

        lastKey = null
        appWidgetManager.updateAppWidget(
            appWidgetIds,
            buildViews(context, layoutRes, title, artist, playing, cached, progress)
        )

        // Artwork not cached yet: load it off the main thread within the broadcast's time budget
        if (cached == null && track != null && controller != null) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val art = withTimeoutOrNull(8_000L) { controller.loadArt(track) }
                    if (art != null && controller.currentTrack?.id == track.id && controller.currentStream == null) {
                        push(context, title, artist, controller.isPlaying, art, progress)
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

        /** Redraws every placed widget across 4x1, 4x2 and 2x2 providers. */
        fun push(
            context: Context,
            title: String,
            artist: String,
            playing: Boolean,
            art: Bitmap?,
            progress: Int = 0
        ) {
            try {
                val manager = AppWidgetManager.getInstance(context) ?: return
                val ids4x1 = manager.getAppWidgetIds(ComponentName(context, MusicAmpWidget::class.java))
                val ids4x2 = manager.getAppWidgetIds(ComponentName(context, MusicAmpWidget4x2::class.java))
                val ids2x2 = manager.getAppWidgetIds(ComponentName(context, MusicAmpWidget2x2::class.java))
                if (ids4x1.isEmpty() && ids4x2.isEmpty() && ids2x2.isEmpty()) return

                val progBucket = progress / 10
                val key = listOf(title, artist, playing, art?.let { System.identityHashCode(it) }, progBucket)
                if (key == lastKey) return
                lastKey = key

                if (ids4x1.isNotEmpty()) {
                    manager.updateAppWidget(ids4x1, buildViews(context, R.layout.widget_musicamp, title, artist, playing, art, progress))
                }
                if (ids4x2.isNotEmpty()) {
                    manager.updateAppWidget(ids4x2, buildViews(context, R.layout.widget_musicamp_4x2, title, artist, playing, art, progress))
                }
                if (ids2x2.isNotEmpty()) {
                    manager.updateAppWidget(ids2x2, buildViews(context, R.layout.widget_musicamp_2x2, title, artist, playing, art, progress))
                }
            } catch (_: Exception) {
                // Launcher / widget host unavailable: nothing to draw into
            }
        }

        private fun buildViews(
            context: Context,
            layoutRes: Int,
            title: String,
            artist: String,
            playing: Boolean,
            art: Bitmap?,
            progress: Int
        ): RemoteViews {
            val views = RemoteViews(context.packageName, layoutRes)

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
            views.setOnClickPendingIntent(R.id.widget_play, servicePi(context, PlaybackService.ACTION_TOGGLE, 102))

            if (layoutRes != R.layout.widget_musicamp_2x2) {
                views.setTextViewText(R.id.widget_title, title.ifBlank { "MusicAmp" })
                views.setTextViewText(R.id.widget_artist, if (title.isBlank()) "Tap to open" else artist)
                views.setOnClickPendingIntent(R.id.widget_info, open)
                views.setOnClickPendingIntent(R.id.widget_prev, servicePi(context, PlaybackService.ACTION_PREV, 101))
                views.setOnClickPendingIntent(R.id.widget_next, servicePi(context, PlaybackService.ACTION_NEXT, 103))
            }

            if (layoutRes == R.layout.widget_musicamp_4x2) {
                views.setProgressBar(R.id.widget_progress, 1000, progress.coerceIn(0, 1000), false)
                views.setOnClickPendingIntent(R.id.widget_shuffle, servicePi(context, PlaybackService.ACTION_SHUFFLE, 104))
                views.setOnClickPendingIntent(R.id.widget_repeat, servicePi(context, PlaybackService.ACTION_REPEAT, 105))
            }

            return views
        }

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

/** 4x2 widget: art + title + artist + progress bar + shuffle/prev/play/next/repeat. */
class MusicAmpWidget4x2 : MusicAmpWidget() {
    override val layoutRes: Int get() = R.layout.widget_musicamp_4x2
}

/** 2x2 widget: album art + play/pause button. */
class MusicAmpWidget2x2 : MusicAmpWidget() {
    override val layoutRes: Int get() = R.layout.widget_musicamp_2x2
}
