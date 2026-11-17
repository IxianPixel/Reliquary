package com.malgra.reliquary.feature.player

/** One subtitle cue: [text] shows from [startMs] until just before [endMs]. */
internal data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

private val Timing = Regex("""^((?:\d+:)?\d{1,2}:\d{2}\.\d{3})\s+-->\s+((?:\d+:)?\d{1,2}:\d{2}\.\d{3})""")
private val Tag = Regex("<[^>]*>")

/**
 * Reads a WebVTT file into cues, for players that don't show subtitles
 * themselves (desktop). Styling, positions and inline timestamps (YouTube's
 * word-by-word captions) are dropped; the text is kept.
 */
internal fun parseWebVtt(text: String): List<SubtitleCue> {
    val cues = mutableListOf<SubtitleCue>()
    val blocks = text.replace("\r\n", "\n").replace('\r', '\n').split(Regex("\n{2,}"))
    for (block in blocks) {
        val lines = block.lines().filter { it.isNotBlank() }
        val timingAt = lines.indexOfFirst { Timing.containsMatchIn(it) }
        if (timingAt < 0) continue // header, NOTE, STYLE or REGION
        val match = Timing.find(lines[timingAt]) ?: continue
        val start = parseTimestamp(match.groupValues[1]) ?: continue
        val end = parseTimestamp(match.groupValues[2]) ?: continue
        val body = lines.drop(timingAt + 1).joinToString("\n") { it.replace(Tag, "").unescapeEntities().trim() }.trim()
        if (body.isNotEmpty() && end > start) cues += SubtitleCue(start, end, body)
    }
    return cues.sortedBy { it.startMs }
}

/** The text showing at [positionMs]: overlapping cues stack, earliest first. */
internal fun List<SubtitleCue>.textAt(positionMs: Long): String? =
    filter { positionMs >= it.startMs && positionMs < it.endMs }
        .map { it.text }
        // YouTube's auto captions repeat the previous line in the next cue; show each line once.
        .flatMap { it.lines() }
        .distinct()
        .joinToString("\n")
        .ifEmpty { null }

private fun parseTimestamp(value: String): Long? {
    val parts = value.split(':')
    val (seconds, millis) = parts.last().split('.').let { (s, ms) -> s.toLongOrNull() to ms.toLongOrNull() }
    if (seconds == null || millis == null) return null
    val minutes = parts[parts.size - 2].toLongOrNull() ?: return null
    val hours = if (parts.size == 3) parts[0].toLongOrNull() ?: return null else 0
    return ((hours * 60 + minutes) * 60 + seconds) * 1000 + millis
}

private fun String.unescapeEntities() =
    replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ").replace("&lrm;", "").replace("&rlm;", "").replace("&amp;", "&")
