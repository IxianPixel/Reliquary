package com.malgra.reliquary.feature.player

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebVttTest {

    @Test fun readsCuesAndSkipsTheHeaderAndNotes() {
        val cues = parseWebVtt(
            """
            WEBVTT
            Kind: captions
            Language: en

            NOTE made by hand

            1
            00:00:01.000 --> 00:00:04.500 align:start position:0%
            Hello <c.colorE5E5E5>there</c> &amp; welcome

            01:02.250 --> 01:03.000
            Two
            lines
            """.trimIndent(),
        )
        assertEquals(
            listOf(SubtitleCue(1_000, 4_500, "Hello there & welcome"), SubtitleCue(62_250, 63_000, "Two\nlines")),
            cues,
        )
    }

    @Test fun inlineTimestampsAreDropped() {
        val cues = parseWebVtt("WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nword<00:00:01.500><c> by</c><00:00:01.800><c> word</c>\n")
        assertEquals("word by word", cues.single().text)
    }

    @Test fun textAtShowsWhatOverlapsAndRepeatsNoLine() {
        val cues = listOf(
            SubtitleCue(0, 2_000, "first"),
            SubtitleCue(1_000, 3_000, "first\nsecond"),
        )
        assertEquals("first", cues.textAt(500))
        assertEquals("first\nsecond", cues.textAt(1_500))
        assertNull(cues.textAt(3_000))
    }
}
