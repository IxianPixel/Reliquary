package com.malgra.reliquary.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

// Compose state over a VideoPlayer, updated from its callbacks.

/** Runs [read] now and after each of the player's callbacks. */
@Composable
private fun <T> playerState(player: VideoPlayer, read: () -> T): State<T> =
    produceState(read(), player) {
        val listener = object : VideoPlayerListener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                value = read()
            }

            override fun onPositionJump(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) {
                value = read()
            }

            override fun onItemTransition(item: PlayerItem?, reason: ItemTransitionReason) {
                value = read()
            }

            override fun onEnded() {
                value = read()
            }

            override fun onStatusChanged(status: PlayerStatus) {
                value = read()
            }

            override fun onVideoSizeChanged(size: VideoSize) {
                value = read()
            }

            override fun onSubtitlesChanged() {
                value = read()
            }
        }
        player.addListener(listener)
        awaitDispose { player.removeListener(listener) }
    }

/** The item loaded in [player]. */
@Composable
internal fun currentItemState(player: VideoPlayer): State<PlayerItem?> = playerState(player) { player.currentItem }

@Composable
internal fun playerStatusState(player: VideoPlayer): State<PlayerStatus> = playerState(player) { player.status }

@Composable
internal fun videoSizeState(player: VideoPlayer): State<VideoSize> = playerState(player) { player.videoSize }

/** The key of the subtitle track showing, or null when off. */
@Composable
internal fun selectedSubtitleState(player: VideoPlayer): State<String?> = playerState(player) { player.selectedSubtitleKey }

/** Whether the play button shows Play (paused, ended or not started) rather than Pause. */
@Composable
internal fun showPlayState(player: VideoPlayer): State<Boolean> = playerState(player) { player.showsPlay() }

internal fun VideoPlayer.showsPlay(): Boolean =
    !playWhenReady || status.state == PlaybackState.Idle || status.state == PlaybackState.Ended

/** What the play/pause button does: as media3's button, it reloads after an error and restarts after the end. */
internal fun VideoPlayer.togglePlay() {
    if (!showsPlay()) {
        pause()
        return
    }
    when (status.state) {
        PlaybackState.Idle -> prepare()
        PlaybackState.Ended -> seekTo(0)
        else -> Unit
    }
    play()
}

internal data class PlaybackProgress(val positionMs: Long, val durationMs: Long)

/** Position and duration, read every [tickMs]. */
@Composable
internal fun progressState(player: VideoPlayer, tickMs: Long): State<PlaybackProgress> =
    produceState(PlaybackProgress(player.positionMs, player.durationMs), player, tickMs) {
        while (true) {
            value = PlaybackProgress(player.positionMs, player.durationMs)
            delay(tickMs)
        }
    }
