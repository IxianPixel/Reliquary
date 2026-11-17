package com.malgra.reliquary.core.ui

import androidx.compose.runtime.Composable
import com.malgra.reliquary.core.ui.resources.Res
import com.malgra.reliquary.core.ui.resources.ui_added_days_ago
import com.malgra.reliquary.core.ui.resources.ui_added_on
import com.malgra.reliquary.core.ui.resources.ui_added_today
import com.malgra.reliquary.core.ui.resources.ui_added_yesterday
import com.malgra.reliquary.core.ui.resources.ui_meta_separator
import com.malgra.reliquary.core.ui.resources.ui_minutes_left
import com.malgra.reliquary.core.ui.resources.ui_video_count
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Instant
import kotlin.time.toJavaInstant

/** "4:05", "14:02" or "1:02:03". */
fun formatDuration(duration: Duration): String {
    val total = duration.inWholeSeconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total % 3600 / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** Whole minutes left, rounded up so the last seconds still read "1 min left". */
fun minutesLeft(remaining: Duration): Int = ceil(remaining.inWholeSeconds / 60.0).toInt().coerceAtLeast(1)

sealed interface RelativeDay {
    data object Today : RelativeDay
    data object Yesterday : RelativeDay
    data class DaysAgo(val days: Int) : RelativeDay
    data class On(val date: LocalDate) : RelativeDay
}

/** Today, yesterday, up to six days ago, then the date. */
fun relativeDay(instant: Instant, today: LocalDate, zone: ZoneId): RelativeDay {
    val date = instant.toJavaInstant().atZone(zone).toLocalDate()
    return when (val days = ChronoUnit.DAYS.between(date, today)) {
        0L -> RelativeDay.Today
        1L -> RelativeDay.Yesterday
        in 2L..6L -> RelativeDay.DaysAgo(days.toInt())
        else -> RelativeDay.On(date)
    }
}

@Composable
fun minutesLeftText(remaining: Duration): String {
    val minutes = minutesLeft(remaining)
    return pluralStringResource(Res.plurals.ui_minutes_left, minutes, minutes)
}

@Composable
fun addedText(instant: Instant): String {
    val zone = ZoneId.systemDefault()
    return when (val day = relativeDay(instant, LocalDate.now(zone), zone)) {
        RelativeDay.Today -> stringResource(Res.string.ui_added_today)
        RelativeDay.Yesterday -> stringResource(Res.string.ui_added_yesterday)
        is RelativeDay.DaysAgo -> pluralStringResource(Res.plurals.ui_added_days_ago, day.days, day.days)
        is RelativeDay.On -> stringResource(Res.string.ui_added_on, formatDate(day.date))
    }
}

/** A medium date in the user's locale, e.g. "3 Mar 2026". */
fun formatDate(date: LocalDate): String = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

fun formatDate(instant: Instant): String = formatDate(instant.toJavaInstant().atZone(ZoneId.systemDefault()).toLocalDate())

/** "1.2M" and friends. */
@Composable
expect fun compactCount(count: Long): String

/** A short file size in the user's locale, e.g. "1.2 GB". */
@Composable
expect fun fileSizeText(bytes: Long): String

@Composable
fun videoCountText(count: Int): String = pluralStringResource(Res.plurals.ui_video_count, count, count)

/** Joins the non-empty parts with " · ". */
@Composable
fun metaLine(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(stringResource(Res.string.ui_meta_separator))
