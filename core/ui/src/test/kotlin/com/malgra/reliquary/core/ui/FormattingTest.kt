package com.malgra.reliquary.core.ui

import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class FormattingTest {
    @Test fun durations() {
        assertEquals("0:05", formatDuration(5.seconds))
        assertEquals("14:02", formatDuration(14.minutes + 2.seconds))
        assertEquals("1:02:03", formatDuration(1.hours + 2.minutes + 3.seconds))
    }

    @Test fun minutesLeftRoundsUp() {
        assertEquals(1, minutesLeft(10.seconds))
        assertEquals(5, minutesLeft(4.minutes + 1.seconds))
        assertEquals(5, minutesLeft(5.minutes))
    }

    @Test fun relativeDays() {
        val today = LocalDate.of(2026, 11, 11)
        fun at(date: String) = Instant.parse("${date}T12:00:00Z")
        assertEquals(RelativeDay.Today, relativeDay(at("2026-11-11"), today, ZoneOffset.UTC))
        assertEquals(RelativeDay.Yesterday, relativeDay(at("2026-11-10"), today, ZoneOffset.UTC))
        assertEquals(RelativeDay.DaysAgo(6), relativeDay(at("2026-11-05"), today, ZoneOffset.UTC))
        assertEquals(RelativeDay.On(LocalDate.of(2026, 11, 4)), relativeDay(at("2026-11-04"), today, ZoneOffset.UTC))
    }
}
