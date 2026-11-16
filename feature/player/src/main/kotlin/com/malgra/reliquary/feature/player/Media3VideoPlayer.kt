package com.malgra.reliquary.feature.player

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * [VideoPlayer] over a media3 [Player]: the service's ExoPlayer, or a
 * MediaController connected to it. Its seeks land where they are asked to.
 */
internal class Media3VideoPlayer(val player: Player) : VideoPlayer {

    private val listeners = mutableMapOf<VideoPlayerListener, Player.Listener>()

    // Converting reads the item's extras, so keep the last one.
    private var lastMediaItem: MediaItem? = null
    private var lastItem: PlayerItem? = null

    override val currentItem: PlayerItem?
        get() = player.currentMediaItem.toCachedPlayerItem()

    override val isPlaying: Boolean get() = player.isPlaying
    override val playWhenReady: Boolean get() = player.playWhenReady
    override val positionMs: Long get() = player.currentPosition
    override val durationMs: Long get() = player.duration.coerceAtLeast(0)

    override fun addListener(listener: VideoPlayerListener) {
        if (listener in listeners) return
        val adapter = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = listener.onIsPlayingChanged(isPlaying)

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val jump = if (reason == Player.DISCONTINUITY_REASON_SEEK) PositionJumpReason.Seek else PositionJumpReason.Other
                listener.onPositionJump(oldPosition.toPlayerPosition(), newPosition.toPlayerPosition(), jump)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val transition = when (reason) {
                    Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> ItemTransitionReason.Auto
                    Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> ItemTransitionReason.Seek
                    else -> ItemTransitionReason.Other
                }
                listener.onItemTransition(mediaItem.toCachedPlayerItem(), transition)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) listener.onEnded()
            }
        }
        listeners[listener] = adapter
        player.addListener(adapter)
    }

    override fun removeListener(listener: VideoPlayerListener) {
        listeners.remove(listener)?.let(player::removeListener)
    }

    override fun setQueue(items: List<PlayerItem>, startIndex: Int, startPositionMs: Long) {
        player.setMediaItems(items.map { it.toMediaItem() }, startIndex, startPositionMs)
        player.prepare()
    }

    override fun play() = player.play()
    override fun pause() = player.pause()

    override fun clear() {
        player.stop()
        player.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    override fun seekBack() = player.seekBack()
    override fun seekForward() = player.seekForward()

    override fun playItem(index: Int) {
        player.seekToDefaultPosition(index)
        player.play()
    }

    override val selectedSubtitleKey: String? get() = player.selectedSubtitleKey()

    override fun selectSubtitle(key: String?, language: String) = player.selectSubtitle(key, language)
    override fun disableSubtitles() = player.disableSubtitles()

    private fun MediaItem?.toCachedPlayerItem(): PlayerItem? {
        if (this == null) return null
        if (this !== lastMediaItem) {
            lastMediaItem = this
            lastItem = toPlayerItem()
        }
        return lastItem
    }

    @OptIn(UnstableApi::class) // PositionInfo.mediaItem
    private fun Player.PositionInfo.toPlayerPosition() = PlayerPosition(mediaItem?.mediaId, positionMs)
}
