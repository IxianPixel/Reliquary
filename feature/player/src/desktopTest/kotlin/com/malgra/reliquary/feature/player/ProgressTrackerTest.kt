package com.malgra.reliquary.feature.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressTrackerTest {

    private class FakeProgress : ProgressTracker.PlaybackProgress {
        val saved = mutableListOf<Pair<String, Duration>>()
        val resume = mutableMapOf<String, Duration>()
        var syncs = 0

        override suspend fun recordPosition(videoId: String, position: Duration, sessionStartedAtMillis: Long) {
            saved += videoId to position
        }

        override suspend fun resumePosition(videoId: String) = resume[videoId] ?: Duration.ZERO

        override fun requestSync() {
            syncs++
        }
    }

    private val player = FakeVideoPlayer()
    private val progress = FakeProgress()

    private fun TestScope.tracker() =
        ProgressTracker(player, progress, this, clock = { 0L }, main = StandardTestDispatcher(testScheduler))

    @Test fun openingAVideoSavesNothingUntilItPlays() = runTest {
        val tracker = tracker()
        player.setQueue(listOf(playerItem("a")), 0, 30_000)
        player.seekTo(40_000)
        runCurrent()
        assertEquals(emptyList(), progress.saved)
        tracker.release()
    }

    @Test fun savesEveryTenSecondsWhilePlayingAndSyncsOnPause() = runTest {
        val tracker = tracker()
        player.setQueue(listOf(playerItem("a")), 0, 0)
        player.play()
        player.positionMs = 10_000
        advanceTimeBy(10_001)
        assertEquals(listOf("a" to 10.seconds), progress.saved)
        assertEquals(0, progress.syncs)

        player.positionMs = 12_000
        player.pause()
        runCurrent()
        assertEquals("a" to 12.seconds, progress.saved.last())
        assertEquals(1, progress.syncs)
        tracker.release()
    }

    @Test fun aSeekSavesWhereItLandedNotWhereItWasAskedToGo() = runTest {
        val tracker = tracker()
        player.landSeek = { it - 2_500 } // the keyframe before
        player.setQueue(listOf(playerItem("a")), 0, 0)
        player.play()
        player.pause()
        runCurrent()
        val syncs = progress.syncs

        player.seekTo(45_500)
        runCurrent()
        assertEquals("a" to 43_000.milliseconds, progress.saved.last())
        // Paused: nothing else would sync it.
        assertEquals(syncs + 1, progress.syncs)
        tracker.release()
    }

    @Test fun aSeekReportedLaterIsSavedWhenReported() = runTest {
        val tracker = tracker()
        player.reportSeeksLater = true
        player.landSeek = { 41_667 }
        player.setQueue(listOf(playerItem("a")), 0, 0)
        player.play()
        player.seekTo(45_500)
        runCurrent()
        assertEquals(emptyList(), progress.saved)

        player.reportSeek()
        runCurrent()
        assertEquals("a" to 41_667.milliseconds, progress.saved.last())
        assertEquals(0, progress.syncs) // still playing: the next pause syncs
        tracker.release()
    }

    @Test fun movingOnKeepsWhereThePreviousVideoGotToAndStartsTheNext() = runTest {
        val tracker = tracker()
        progress.resume["b"] = 90.seconds
        player.setQueue(listOf(playerItem("a"), playerItem("b")), 0, 0)
        player.play()
        player.positionMs = 50_000
        player.finishItem()
        runCurrent()
        assertEquals("a" to 600.seconds, progress.saved.first())
        // The next video picks up where it was left.
        assertEquals(90_000L, player.positionMs)

        player.positionMs = 95_000
        player.pause()
        runCurrent()
        assertEquals("b" to 95.seconds, progress.saved.last())
        tracker.release()
    }

    @Test fun theEndAndReleaseSaveAndSync() = runTest {
        val tracker = tracker()
        player.setQueue(listOf(playerItem("a")), 0, 0)
        player.play()
        player.positionMs = 600_000
        player.finishItem()
        runCurrent()
        assertEquals("a" to 600.seconds, progress.saved.last())
        val syncs = progress.syncs

        tracker.release()
        runCurrent()
        assertEquals(syncs + 1, progress.syncs)
    }
}
