package com.malgra.reliquary.feature.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink

/** A time written in text, e.g. "1:23" or "1:02:03": where it sits in the text and where it points in the video. */
internal data class TextTimestamp(val range: IntRange, val positionMs: Long)

/** A chapter from the description: [title] starts at [startMs]. */
internal data class Chapter(val startMs: Long, val title: String)

// Not part of a longer number, time or word ("12:30:45:00", "v1.2:30", "10:00am"); seconds are always two digits.
private val Timestamp = Regex("""(?<![\w:.])(?:(\d{1,2}):)?(\d{1,2}):(\d{2})(?![\w:])""")

/** YouTube shows chapters only for three or more. */
private const val MIN_CHAPTERS = 3

/**
 * The timestamps in [text]. Ones past the end of a video [durationMs] long are
 * left out: they're more likely clock times or scores than places in the video.
 */
internal fun findTimestamps(text: String, durationMs: Long): List<TextTimestamp> =
    Timestamp.findAll(text).mapNotNull { match ->
        val (hours, minutes, seconds) = match.destructured
        if (seconds.toInt() >= 60 || (hours.isNotEmpty() && minutes.toInt() >= 60)) return@mapNotNull null
        val positionMs = (((hours.toLongOrNull() ?: 0) * 60 + minutes.toLong()) * 60 + seconds.toLong()) * 1000
        if (durationMs > 0 && positionMs > durationMs) null else TextTimestamp(match.range, positionMs)
    }.toList()

/**
 * The chapters in a [description], by YouTube's rules: one per line that starts
 * or ends with a timestamp, the first at 0:00, in order, and at least three.
 * Anything else gives none.
 */
internal fun parseChapters(description: String?, durationMs: Long): List<Chapter> {
    if (description.isNullOrBlank()) return emptyList()
    val candidates = description.lineSequence()
        .mapNotNull { line ->
            val stamp = findTimestamps(line, durationMs).firstOrNull() ?: return@mapNotNull null
            Chapter(stamp.positionMs, line.titleWithout(stamp.range))
        }
        .dropWhile { it.startMs != 0L }
    val chapters = mutableListOf<Chapter>()
    for (candidate in candidates) {
        if (chapters.isEmpty() || candidate.startMs > chapters.last().startMs) chapters += candidate
    }
    return if (chapters.size >= MIN_CHAPTERS) chapters else emptyList()
}

/** The chapter playing at [positionMs], or null before the first. */
internal fun List<Chapter>.at(positionMs: Long): Chapter? = lastOrNull { it.startMs <= positionMs }

/** The line with its timestamp (and brackets round it) taken out, and separators like " - " trimmed. */
private fun String.titleWithout(range: IntRange): String {
    var from = range.first
    var to = range.last + 1
    if (from > 0 && to < length && this[from - 1] in "([" && this[to] in ")]") {
        from--
        to++
    }
    return removeRange(from, to).trim { it.isWhitespace() || it in "-–—:|•·" }
}

/** [text] with its timestamps as links that call [onSeek] with their position. */
@Composable
internal fun rememberTimestampedText(text: String, durationMs: Long, onSeek: (positionMs: Long) -> Unit): AnnotatedString {
    val styles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium))
    return remember(text, durationMs, onSeek, styles) {
        val stamps = findTimestamps(text, durationMs)
        buildAnnotatedString {
            var at = 0
            for (stamp in stamps) {
                append(text, at, stamp.range.first)
                withLink(LinkAnnotation.Clickable("timestamp", styles) { onSeek(stamp.positionMs) }) {
                    append(text.substring(stamp.range))
                }
                at = stamp.range.last + 1
            }
            append(text, at, text.length)
        }
    }
}
