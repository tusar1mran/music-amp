package com.musicamp.poweramp

import java.io.File

/** One track of a cue sheet, positioned inside a single audio file. */
data class CueTrack(val number: Int, val title: String?, val performer: String?, val startMs: Long)

data class CueSheet(val albumTitle: String?, val albumPerformer: String?, val tracks: List<CueTrack>)

object CueParser {
    private val quoted = Regex("^\\S+\\s+\"?(.*?)\"?\\s*$")

    /**
     * Parses a text cue sheet. Only tracks belonging to [audioFileName] are returned (or every track when
     * the sheet references a single file, which is the usual "image + cue" rip).
     */
    fun parse(text: String, audioFileName: String?): CueSheet? {
        var albumTitle: String? = null
        var albumPerformer: String? = null
        val files = ArrayList<Pair<String, MutableList<CueTrack>>>()
        var curNum = -1
        var curTitle: String? = null
        var curPerf: String? = null
        var curStart = -1L

        fun flush() {
            if (curNum > 0 && curStart >= 0 && files.isNotEmpty()) files.last().second.add(CueTrack(curNum, curTitle, curPerf, curStart))
            curNum = -1; curTitle = null; curPerf = null; curStart = -1L
        }

        for (raw in text.lineSequence()) {
            val line = raw.trim().trimStart('﻿')
            val key = line.substringBefore(' ').uppercase()
            when (key) {
                "FILE" -> {
                    flush()
                    val name = line.removePrefix(line.substringBefore(' ')).trim()
                        .let { if (it.startsWith("\"")) it.substring(1).substringBefore('"') else it.substringBeforeLast(' ') }
                    files.add(name to ArrayList())
                }
                "TRACK" -> {
                    flush()
                    curNum = line.split(Regex("\\s+")).getOrNull(1)?.toIntOrNull() ?: -1
                }
                "TITLE" -> {
                    val v = quoted.find(line)?.groupValues?.get(1)
                    if (curNum > 0) curTitle = v else albumTitle = v
                }
                "PERFORMER" -> {
                    val v = quoted.find(line)?.groupValues?.get(1)
                    if (curNum > 0) curPerf = v else albumPerformer = v
                }
                "INDEX" -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.getOrNull(1)?.toIntOrNull() == 1) parseTime(parts.getOrNull(2))?.let { curStart = it }
                }
            }
        }
        flush()
        if (files.isEmpty()) return null
        val chosen = when {
            files.size == 1 -> files[0].second
            audioFileName != null -> files.firstOrNull { File(it.first).name.equals(audioFileName, true) }?.second
                ?: files.firstOrNull { File(it.first).nameWithoutExtension.equals(File(audioFileName).nameWithoutExtension, true) }?.second
            else -> null
        } ?: return null
        if (chosen.size < 2) return null
        return CueSheet(albumTitle, albumPerformer, chosen.sortedBy { it.startMs })
    }

    /** mm:ss:ff with 75 frames per second. */
    private fun parseTime(s: String?): Long? {
        val p = s?.split(':') ?: return null
        if (p.size != 3) return null
        val m = p[0].toLongOrNull() ?: return null
        val sec = p[1].toLongOrNull() ?: return null
        val f = p[2].toLongOrNull() ?: return null
        return (m * 60 + sec) * 1000 + f * 1000 / 75
    }
}
