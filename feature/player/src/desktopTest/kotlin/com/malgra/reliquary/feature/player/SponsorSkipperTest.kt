package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.SponsorSegment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SponsorSkipperTest {

    private val sponsor = SponsorSegment(10.seconds, 40.seconds, "sponsor")
    private val outro = SponsorSegment(100.seconds, 120.seconds, "outro")
    private val player = FakeVideoPlayer()
    private val skipped = mutableListOf<SponsorSegment>()

    private fun TestScope.skipper(keyframeSlackMs: Long = 0) = SponsorSkipper(
        player,
        enabled = { true },
        keyframeSlackMs = keyframeSlackMs,
        main = StandardTestDispatcher(testScheduler),
        onSkipped = { skipped += it },
    )

    private fun TestScope.playFrom(positionMs: Long) {
        player.setQueue(listOf(playerItem("a", listOf(sponsor, outro))), 0, positionMs)
        player.play()
        advanceTimeBy(1)
    }

    @Test fun playbackReachingASegmentJumpsToItsEnd() = runTest {
        val skipper = skipper()
        playFrom(9_000)
        player.positionMs = 10_100
        advanceTimeBy(300)
        assertEquals(listOf(sponsor), skipped)
        assertEquals(40_000L, player.positionMs)
        skipper.release()
    }

    @Test fun aSegmentTheUserSeeksIntoIsLeftToPlay() = runTest {
        val skipper = skipper()
        playFrom(0)
        player.seekTo(20_000)
        advanceTimeBy(1_000)
        assertEquals(emptyList(), skipped)
        skipper.release()
    }

    @Test fun aSkipThatLandsShortOfTheEndIsNotRepeated() = runTest {
        val skipper = skipper()
        player.landSeek = { it - 3_000 } // a keyframe inside the segment
        player.reportSeeksLater = true
        playFrom(10_000)
        advanceTimeBy(300)
        assertEquals(listOf(sponsor), skipped)
        assertEquals(37_000L, player.positionMs)

        player.reportSeek()
        advanceTimeBy(2_000)
        assertEquals(listOf(sponsor), skipped)
        assertEquals(listOf(40_000L), player.seeks)

        // Not taken for the user choosing to watch, and later segments still go.
        player.positionMs = 100_500
        advanceTimeBy(300)
        assertEquals(listOf(sponsor, outro), skipped)
        skipper.release()
    }

    @Test fun aUserSeekAfterASkipStillCounts() = runTest {
        val skipper = skipper()
        playFrom(10_000)
        advanceTimeBy(300)
        assertEquals(listOf(sponsor), skipped)

        player.seekTo(15_000) // Undo
        advanceTimeBy(2_000)
        assertEquals(listOf(sponsor), skipped)
        skipper.release()
    }

    @Test fun undoThatLandsOnAKeyframeBeforeTheSegmentStillWatchesIt() = runTest {
        val skipper = skipper(keyframeSlackMs = 10_000)
        playFrom(10_000)
        advanceTimeBy(300)
        assertEquals(listOf(sponsor), skipped)

        player.landSeek = { it - 2_000 } // Undo to 10 s lands at 8 s
        player.seekTo(sponsor.start.inWholeMilliseconds)
        advanceTimeBy(300)
        player.positionMs = 10_500 // playback reaches the segment again
        advanceTimeBy(2_000)
        assertEquals(listOf(sponsor), skipped)
        skipper.release()
    }
}
