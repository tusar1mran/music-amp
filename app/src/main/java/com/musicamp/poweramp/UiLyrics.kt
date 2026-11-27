package com.musicamp.poweramp

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One timed lyric line. [timeMs] already includes the file's `[offset:]` adjustment. */
data class LyricLine(val timeMs: Long, val text: String)

/** Result of [parseLyrics]: timed [lines] when the text is LRC, otherwise [synced] is false and [plainLines] holds the text. */
data class ParsedLyrics(
    val synced: Boolean,
    val lines: List<LyricLine>,
    val plainLines: List<String>
)

private val LRC_TIME = Regex("""^\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
private val LRC_META = Regex("""^\[([A-Za-z#]+):([^\]]*)]$""")
private val LRC_WORD_TIME = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")

/**
 * Parses LRC lyrics. Lines may carry several leading `[mm:ss.xx]` timestamps (each produces a line).
 * ID tags such as `[ar:]`, `[ti:]` are dropped; `[offset:+/-ms]` is applied (positive = lyrics appear sooner).
 * Enhanced per-word `<mm:ss.xx>` stamps are stripped. Text without any timestamp is returned as plain lines.
 */
fun parseLyrics(text: String): ParsedLyrics {
    var offsetMs = 0L
    val timed = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()

    for (raw in text.lines()) {
        var rest = raw.trim()
        val times = mutableListOf<Long>()
        while (rest.startsWith("[")) {
            val t = LRC_TIME.find(rest)
            if (t != null) {
                val min = t.groupValues[1].toLong()
                val sec = t.groupValues[2].toLong()
                val frac = t.groupValues[3]
                val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                times += min * 60_000L + sec * 1000L + ms
                rest = rest.substring(t.range.last + 1).trimStart()
                continue
            }
            val meta = LRC_META.find(rest)
            if (meta != null && times.isEmpty()) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    offsetMs = meta.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: offsetMs
                }
                rest = ""
                times.clear()
                break
            }
            break
        }
        if (times.isNotEmpty()) {
            val lineText = rest.replace(LRC_WORD_TIME, "").trim()
            times.forEach { timed += LyricLine(it, lineText) }
        } else if (raw.isNotBlank() && rest.isNotEmpty()) {
            plain += raw.trim()
        } else if (raw.isBlank()) {
            plain += ""
        }
    }

    if (timed.isEmpty()) {
        return ParsedLyrics(synced = false, lines = emptyList(), plainLines = plain.dropWhile { it.isEmpty() }.dropLastWhile { it.isEmpty() })
    }
    val adjusted = timed
        .map { it.copy(timeMs = (it.timeMs - offsetMs).coerceAtLeast(0L)) }
        .sortedBy { it.timeMs } // stable: lines sharing a timestamp keep file order
    return ParsedLyrics(synced = true, lines = adjusted, plainLines = emptyList())
}

/** Index of the line playing at [positionMs]: the last line whose time is <= position, or -1 before the first. */
fun currentLyricIndex(lines: List<LyricLine>, positionMs: Long): Int {
    if (positionMs < 0L) return -1
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].timeMs <= positionMs) {
            found = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return found
}

/**
 * Synced lyrics: highlights the current line, keeps it centred (pausing for a few seconds after the user
 * drags the list), and seeks to a line when it is tapped. [positionMs] returns -1 when the lyrics do not
 * belong to the playing track (no highlight).
 */
@Composable
fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: () -> Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val current by remember(lines) { derivedStateOf { currentLyricIndex(lines, positionMs()) } }
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastUserScrollAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastUserScrollAt = System.currentTimeMillis() }

    LaunchedEffect(current, lines) {
        if (current < 0) return@LaunchedEffect
        if (System.currentTimeMillis() - lastUserScrollAt < 3000L) return@LaunchedEffect
        var item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current }
        if (item == null) {
            listState.scrollToItem(current)
            item = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == current } ?: return@LaunchedEffect
        }
        val info = listState.layoutInfo
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val delta = item.offset + item.size / 2 - viewportCenter
        if (delta != 0) listState.animateScrollBy(delta.toFloat(), tween(450))
    }

    BoxWithConstraints(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Half-height padding so the first and last lines can sit in the centre
            contentPadding = PaddingValues(vertical = maxHeight / 2)
        ) {
            itemsIndexed(lines) { index, line ->
                val isCurrent = index == current
                val color by animateColorAsState(
                    targetValue = when {
                        isCurrent -> ThemeBerryPrimary
                        current >= 0 && index < current -> PowerampTextSecondary.copy(alpha = 0.55f)
                        else -> PowerampTextSecondary
                    },
                    animationSpec = tween(250),
                    label = "lyricLineColor"
                )
                val scale by animateFloatAsState(if (isCurrent) 1.08f else 1f, tween(250), label = "lyricLineScale")
                Text(
                    text = line.text.ifEmpty { "♪" },
                    color = color,
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = (if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold).appWeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSeek(line.timeMs) }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                )
            }
        }
    }
}
