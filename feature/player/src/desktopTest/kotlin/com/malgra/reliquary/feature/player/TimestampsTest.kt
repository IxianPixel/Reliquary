package com.malgra.reliquary.feature.player

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TimestampsTest {

    private val hour = 3_600_000L

    @Test fun findsMinutesAndHours() {
        val text = "Skip to 1:23, or 1:02:03 for the end"
        val stamps = findTimestamps(text, 2 * hour)
        assertEquals(listOf(83_000L, 3_723_000L), stamps.map { it.positionMs })
        assertEquals(listOf("1:23", "1:02:03"), stamps.map { text.substring(it.range) })
    }

    @Test fun leavesOutWhatIsNotAPlaceInTheVideo() {
        val text = "1:75 and 1:60:00 aren't times, 10:00am and v1.2:30 aren't either, 12:30:45:00 is too long"
        assertEquals(emptyList(), findTimestamps(text, 24 * hour))
    }

    @Test fun leavesOutTimesPastTheEnd() {
        assertEquals(listOf(30_000L), findTimestamps("0:30 then 11:00", 10 * 60_000L).map { it.positionMs })
    }

    @Test fun keepsAllWhileTheDurationIsUnknown() {
        assertEquals(1, findTimestamps("59:59", 0).size)
    }

    @Test fun readsChaptersFromTheDescription() {
        val description = """
            A video about things.

            0:00 Intro
            1:30 - The first thing
            (4:05) The second thing
            The end: 10:00
            Thanks for watching! Shout out to whoever said hi at 2:00
        """.trimIndent()
        assertEquals(
            listOf(
                Chapter(0, "Intro"),
                Chapter(90_000, "The first thing"),
                Chapter(245_000, "The second thing"),
                Chapter(600_000, "The end"),
            ),
            parseChapters(description, hour),
        )
    }

    @Test fun noChaptersWithoutOneAtZero() {
        assertTrue(parseChapters("1:00 One\n2:00 Two\n3:00 Three", hour).isEmpty())
    }

    @Test fun noChaptersWithFewerThanThree() {
        assertTrue(parseChapters("0:00 One\n2:00 Two", hour).isEmpty())
        assertTrue(parseChapters(null, hour).isEmpty())
    }

    @Test fun chapterAtAPosition() {
        val chapters = listOf(Chapter(0, "One"), Chapter(60_000, "Two"), Chapter(120_000, "Three"))
        assertEquals("One", chapters.at(59_999)?.title)
        assertEquals("Two", chapters.at(60_000)?.title)
        assertEquals("Three", chapters.at(hour)?.title)
        assertNull(emptyList<Chapter>().at(0))
    }
}
