package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.data.progress.ProgressRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Saves the place in the playing video to Room: every 10 seconds while it
 * plays, on pause, seek and end, and when the player goes away. Saves are
 * local and cheap; a sync to the server is requested when playback stops,
 * and after a seek while paused.
 *
 * Player callbacks arrive on the main thread, where the position is read;
 * the write runs in [scope], which outlives the player.
 */
internal class ProgressTracker(
    private val player: VideoPlayer,
    private val progress: PlaybackProgress,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val main: CoroutineDispatcher = Dispatchers.Main,
) : VideoPlayerListener {

    private val ticks = CoroutineScope(SupervisorJob() + main)
    private var tick: Job? = null

    /** The video whose session is running: set once it actually plays, so opening a video saves nothing. */
    private var sessionVideoId: String? = null
    private var sessionStartedAt = 0L

    init {
        player.addListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        tick?.cancel()
        if (isPlaying) {
            val id = player.currentItem?.videoId ?: return
            if (id != sessionVideoId) startSession(id)
            tick = ticks.launch {
                while (true) {
                    delay(SAVE_INTERVAL_MS)
                    saveCurrent()
                }
            }
        } else {
            saveCurrent(thenSync = true)
        }
    }

    override fun onPositionJump(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) {
        val session = sessionVideoId ?: return
        when {
            // Another video replaced this one (or the queue moved on): keep where this one got to.
            old.videoId == session && new.videoId != session -> {
                save(session, old.positionMs, thenSync = true)
                sessionVideoId = null
                // Still playing, so no onIsPlayingChanged will start the next video's session.
                if (player.playWhenReady && new.videoId != null) startSession(new.videoId)
            }
            // The position is where the seek landed, which may not be where it was asked to go.
            reason == PositionJumpReason.Seek && new.videoId == session -> {
                // While playing, the next pause syncs; while paused, nothing else will.
                save(session, new.positionMs, thenSync = !player.isPlaying)
            }
        }
    }

    /**
     * When the queue moves to another video by itself or by a tap on "Next in",
     * that video picks up from its saved place (the first one is started there
     * by the player screen).
     */
    override fun onItemTransition(item: PlayerItem?, reason: ItemTransitionReason) {
        val id = item?.videoId ?: return
        if (reason != ItemTransitionReason.Auto && reason != ItemTransitionReason.Seek) return
        scope.launch(main) {
            val resume = progress.resumePosition(id)
            val stillStarting = player.currentItem?.videoId == id && player.positionMs < RESUME_WINDOW_MS
            if (resume > Duration.ZERO && stillStarting) player.seekTo(resume.inWholeMilliseconds)
        }
    }

    override fun onEnded() {
        saveCurrent(thenSync = true)
    }

    /** Saves a last time; call before the player is released. */
    fun release() {
        ticks.cancel()
        saveCurrent(thenSync = true)
        player.removeListener(this)
    }

    private fun startSession(videoId: String) {
        sessionVideoId = videoId
        sessionStartedAt = clock()
    }

    /** With [thenSync], a sync is requested even when there's nothing to save, for earlier changes. */
    private fun saveCurrent(thenSync: Boolean = false) {
        val session = sessionVideoId
        if (session == null || player.currentItem?.videoId != session) {
            if (thenSync) progress.requestSync()
            return
        }
        save(session, player.positionMs, thenSync)
    }

    /** The sync request waits for the write, so the sync can't run before the change is saved. */
    private fun save(videoId: String, positionMs: Long, thenSync: Boolean = false) {
        val start = sessionStartedAt
        scope.launch {
            progress.recordPosition(videoId, positionMs.coerceAtLeast(0).milliseconds, start)
            if (thenSync) progress.requestSync()
        }
    }

    /** What the tracker needs of [ProgressRepository]. */
    interface PlaybackProgress {
        suspend fun recordPosition(videoId: String, position: Duration, sessionStartedAtMillis: Long)
        suspend fun resumePosition(videoId: String): Duration
        fun requestSync()
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 10_000L

        /** Only jump to the saved place if the user hasn't already moved on in the video. */
        const val RESUME_WINDOW_MS = 3_000L
    }
}

internal fun ProgressRepository.asPlaybackProgress() = object : ProgressTracker.PlaybackProgress {
    override suspend fun recordPosition(videoId: String, position: Duration, sessionStartedAtMillis: Long) =
        this@asPlaybackProgress.recordPosition(videoId, position, sessionStartedAtMillis)

    override suspend fun resumePosition(videoId: String) = this@asPlaybackProgress.resumePosition(videoId)

    override fun requestSync() = this@asPlaybackProgress.requestSync()
}
