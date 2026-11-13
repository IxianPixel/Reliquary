package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.SponsorSegment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class SegmentSkipRulesTest {

    private val sponsor = SponsorSegment(10.seconds, 40.seconds, "sponsor")
    private val outro = SponsorSegment(100.seconds, 120.seconds, "outro")
    private val segments = listOf(sponsor, outro)

    @Test fun playbackReachingASegmentSkipsIt() {
        val rules = SegmentSkipRules()
        assertNull(rules.segmentToSkip(segments, 9_900))
        assertEquals(sponsor, rules.segmentToSkip(segments, 10_000))
        assertEquals(outro, rules.segmentToSkip(segments, 101_000))
    }

    @Test fun theLastMomentsOfASegmentAreNotWorthAJump() {
        assertNull(SegmentSkipRules().segmentToSkip(segments, 39_600))
    }

    @Test fun aSegmentTheUserSeeksIntoPlaysUntilTheyLeaveIt() {
        val rules = SegmentSkipRules()
        rules.onUserSeek(segments, 15_000) // e.g. Undo, or a drag on the seek bar

        assertNull(rules.segmentToSkip(segments, 15_000))
        assertNull(rules.segmentToSkip(segments, 30_000))
        assertNull(rules.segmentToSkip(segments, 41_000)) // left it

        // Back before it, then playback reaches it again: skipped as usual.
        rules.onUserSeek(segments, 5_000)
        assertEquals(sponsor, rules.segmentToSkip(segments, 10_200))
    }

    @Test fun choosingOneSegmentLeavesTheOthersSkippable() {
        val rules = SegmentSkipRules()
        rules.onUserSeek(segments, 20_000)
        assertNull(rules.segmentToSkip(segments, 20_000))
        assertEquals(outro, rules.segmentToSkip(segments, 100_500))
    }

    @Test fun aNewVideoForgetsChoices() {
        val rules = SegmentSkipRules()
        rules.onUserSeek(segments, 20_000)
        rules.reset()
        assertEquals(sponsor, rules.segmentToSkip(segments, 20_000))
    }
}
