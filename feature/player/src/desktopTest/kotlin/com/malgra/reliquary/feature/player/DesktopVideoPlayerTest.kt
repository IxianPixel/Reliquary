package com.malgra.reliquary.feature.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopVideoPlayerTest {

    /** Records what the player asks of it; the test plays libVLC's part. */
    private class FakeEngine : PlaybackEngine {
        override var listener: PlaybackEngine.Listener? = null
        override var positionMs = 0L
        override var durationMs = 0L
        val calls = mutableListOf<String>()

        override fun open(uri: String, startMs: Long, paused: Boolean) {
            calls += "open $uri $startMs paused=$paused"
            positionMs = startMs
        }

        override fun play() {
            calls += "play"
        }

        override fun pause() {
            calls += "pause"
        }

        override fun stop() {
            calls += "stop"
        }

        override fun seekTo(positionMs: Long) {
            calls += "seek $positionMs"
            this.positionMs = positionMs
        }

        override fun release() = Unit
    }

    private class Recorder : VideoPlayerListener {
        val jumps = mutableListOf<Pair<PlayerPosition, PositionJumpReason>>()
        val transitions = mutableListOf<Pair<String?, ItemTransitionReason>>()
        val playing = mutableListOf<Boolean>()

        override fun onPositionJump(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) {
            jumps += new to reason
        }

        override fun onItemTransition(item: PlayerItem?, reason: ItemTransitionReason) {
            transitions += item?.videoId to reason
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            playing += isPlaying
        }
    }

    private val engine = FakeEngine()
    private val events = Recorder()

    private fun item(id: String) = playerItem(id).copy(source = MediaSource.Local(File("/media/$id.mp4")))

    private fun TestScope.player(factory: EngineFactory = EngineFactory { Result.success(engine) }) =
        DesktopVideoPlayer(OkHttpClient(), this, factory, clock = { testScheduler.currentTime }).also { it.addListener(events) }

    /** What PlayerSheet does: queue, then play. */
    private fun TestScope.start(player: DesktopVideoPlayer, vararg ids: String, startMs: Long = 0) {
        player.setQueue(ids.map(::item), 0, startMs)
        player.play()
        runCurrent()
    }

    @Test fun opensAtTheResumePointAndPlaysOnceLibVlcDoes() = runTest {
        val player = player()
        start(player, "a", startMs = 45_000)

        // The play() right after the queue reaches the open, which isn't then paused.
        assertEquals(listOf("open file:///media/a.mp4 45000 paused=false"), engine.calls)
        assertEquals(PlaybackState.Buffering, player.status.state)
        assertEquals(45_000, player.positionMs)

        engine.durationMs = 600_000
        engine.listener!!.onPlaying()
        runCurrent()
        assertEquals(PlayerStatus(PlaybackState.Ready, true, null), player.status)
        assertEquals(listOf(true), events.playing)
        player.release()
    }

    @Test fun aSeekLandsExactlyAndIsReportedAtOnce() = runTest {
        val player = player()
        start(player, "a")
        engine.durationMs = 600_000
        engine.listener!!.onPlaying()
        runCurrent()

        player.seekTo(45_500)
        assertEquals("seek 45500", engine.calls.last())
        assertEquals(PlayerPosition("a", 45_500) to PositionJumpReason.Seek, events.jumps.last())
        assertNull(events.jumps.last().first.requestedMs)
        player.release()
    }

    @Test fun aSeekWhileOpeningIsMadeOnceThePictureShows() = runTest {
        val player = player()
        start(player, "a")
        player.seekTo(30_000) // e.g. ProgressTracker resuming the next video in the queue
        assertEquals(30_000, player.positionMs)
        assertTrue(engine.calls.none { it.startsWith("seek") })

        engine.listener!!.onPlaying()
        runCurrent()
        assertEquals("seek 30000", engine.calls.last())
        player.release()
    }

    @Test fun theQueueMovesOnWhenAVideoEnds() = runTest {
        val player = player()
        start(player, "a", "b")
        engine.listener!!.onPlaying()
        runCurrent()

        engine.listener!!.onEnded()
        runCurrent()
        assertEquals("b" to ItemTransitionReason.Auto, events.transitions.last())
        assertEquals("open file:///media/b.mp4 0 paused=false", engine.calls.last())
        player.release()
    }

    @Test fun libVlcErrorsShowAsErrors() = runTest {
        val player = player()
        start(player, "a")
        engine.listener!!.onError("libVLC couldn't play the media")
        runCurrent()
        assertEquals(PlaybackState.Idle, player.status.state)
        assertTrue(player.status.error is PlayerError.Playback)
        player.release()
    }

    @Test fun missingVlcLibrariesSaySo() = runTest {
        val player = player(EngineFactory { Result.failure(IllegalStateException("The VLC libraries are missing")) })
        start(player, "a")
        assertEquals(PlayerError.Unavailable, player.status.error)
        player.release()
    }

    @Test fun aStartThatNeverComesBecomesAnError() = runTest {
        val player = player()
        start(player, "a")
        advanceTimeBy(19_000)
        assertNull(player.status.error)
        advanceTimeBy(2_000)
        assertTrue(player.status.error is PlayerError.Playback)
        player.release()
    }

    @Test fun pausingBeforeTheStartIsNotAnError() = runTest {
        val player = player()
        start(player, "a")
        player.pause()
        advanceTimeBy(30_000)
        assertNull(player.status.error)
        player.release()
    }

    @Test fun serverVideosGoThroughTheProxyUnderTheirFileName() = runTest {
        val player = player()
        player.setQueue(listOf(playerItem("a").copy(source = MediaSource.Remote("http://ta.local:8000/media/UC1/a.mp4"))), 0, 0)
        runCurrent()
        val open = engine.calls.single()
        assertTrue(open.startsWith("open http://127.0.0.1:"), open)
        assertTrue(open.contains("/a.mp4 0 paused=true"), open)
        player.release()
    }

    @Test fun bufferingWhilePlayingIsNotAStop() = runTest {
        val player = player()
        start(player, "a")
        engine.listener!!.onPlaying()
        runCurrent()
        engine.listener!!.onBuffering(40f)
        runCurrent()
        assertEquals(PlaybackState.Buffering, player.status.state)
        assertEquals(listOf(true), events.playing)
        player.release()
    }

    /** Plays [player] to a moving position: libVLC's playing and time events. */
    private fun TestScope.playing(player: DesktopVideoPlayer) {
        engine.durationMs = 600_000
        engine.listener!!.onPlaying()
        engine.listener!!.onTime(0)
        engine.positionMs = 1_000
        engine.listener!!.onTime(1_000)
        runCurrent()
    }

    @Test fun aProblemReportedWhilePlayingIsNotShownIfPlaybackCarriesOn() = runTest {
        val player = player()
        start(player, "a")
        playing(player)

        // e.g. libVLC's error event for a dropped range, or the proxy's report of one
        engine.listener!!.onError("libVLC couldn't play the media")
        runCurrent()
        repeat(10) {
            advanceTimeBy(1_000)
            engine.positionMs += 1_000
            engine.listener!!.onTime(engine.positionMs)
        }
        runCurrent()
        assertNull(player.status.error)
        assertEquals(PlayerStatus(PlaybackState.Ready, true, null), player.status)
        player.release()
    }

    @Test fun aProblemReportedWhilePlayingIsShownIfPlaybackStops() = runTest {
        val player = player()
        start(player, "a")
        playing(player)

        engine.listener!!.onError("libVLC couldn't play the media")
        advanceTimeBy(9_000) // the position stands still
        assertTrue(player.status.error is PlayerError.Playback)
        player.release()
    }

    @Test fun playingAfterTheWatchdogFiredClearsTheError() = runTest {
        val player = player()
        start(player, "a")
        advanceTimeBy(21_000)
        assertTrue(player.status.error is PlayerError.Playback)

        playing(player) // slow, but it got there
        assertNull(player.status.error)
        assertEquals(PlaybackState.Ready, player.status.state)
        player.release()
    }
}
