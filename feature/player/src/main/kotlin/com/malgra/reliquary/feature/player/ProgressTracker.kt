package com.malgra.reliquary.feature.player

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn as UnstableOptIn
import android.os.Handler
import android.os.Looper
import androidx.media3.common.Player
import com.malgra.reliquary.core.data.progress.ProgressRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/**
 * Saves the place in the playing video to Room: every 10 seconds while it
 * plays, on pause, seek and end, and when the player goes away. Saves are
 * local and cheap; a sync to the server is requested when playback stops.
 *
 * Player callbacks arrive on the main thread, where the position is read;
 * the write runs in [scope], which outlives the service.
 */
internal class ProgressTracker(
    private val player: Player,
    private val progress: ProgressRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : Player.Listener {

    private val handler = Handler(Looper.getMainLooper())

    /** The video whose session is running: set once it actually plays, so opening a video saves nothing. */
    private var sessionVideoId: String? = null
    private var sessionStartedAt = 0L

    private val tick = object : Runnable {
        override fun run() {
            saveCurrent()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    init {
        player.addListener(this)
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        handler.removeCallbacks(tick)
        if (isPlaying) {
            val id = player.currentMediaItem?.mediaId ?: return
            if (id != sessionVideoId) {
                sessionVideoId = id
                sessionStartedAt = clock()
            }
            handler.postDelayed(tick, SAVE_INTERVAL_MS)
        } else {
            saveCurrent()
            progress.requestSync()
        }
    }

    @UnstableOptIn(UnstableApi::class) // PositionInfo.mediaItem
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        val session = sessionVideoId ?: return
        val oldId = oldPosition.mediaItem?.mediaId
        val newId = newPosition.mediaItem?.mediaId
        when {
            // Another video replaced this one: keep where this one got to.
            oldId == session && newId != session -> {
                save(session, oldPosition.positionMs)
                progress.requestSync()
                sessionVideoId = null
            }
            reason == Player.DISCONTINUITY_REASON_SEEK && newId == session -> save(session, newPosition.positionMs)
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            saveCurrent()
            progress.requestSync()
        }
    }

    /** Saves a last time; call before the player is released. */
    fun release() {
        handler.removeCallbacks(tick)
        saveCurrent()
        progress.requestSync()
        player.removeListener(this)
    }

    private fun saveCurrent() {
        val session = sessionVideoId ?: return
        if (player.currentMediaItem?.mediaId != session) return
        save(session, player.currentPosition)
    }

    private fun save(videoId: String, positionMs: Long) {
        val start = sessionStartedAt
        scope.launch { progress.recordPosition(videoId, positionMs.coerceAtLeast(0).milliseconds, start) }
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 10_000L
    }
}
