package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.SponsorSegment
import java.io.File

fun playerItem(id: String, segments: List<SponsorSegment> = emptyList()) = PlayerItem(
    videoId = id,
    source = MediaSource.Local(File("$id.mp4")),
    title = id,
    channelName = "",
    thumbnailUrl = null,
    playlistId = null,
    subtitles = emptyList(),
    sponsorSegments = segments,
)

/**
 * A player driven by the test. By default a seek lands where asked and is
 * reported from within seekTo, as media3 does; [landSeek] and [reportSeeksLater]
 * act like the desktop player, which seeks to a keyframe and reports it later.
 */
class FakeVideoPlayer : VideoPlayer {
    private val listeners = mutableListOf<VideoPlayerListener>()
    private var queue = listOf<PlayerItem>()
    private var index = -1

    override val currentItem: PlayerItem? get() = queue.getOrNull(index)
    override var isPlaying = false
        private set
    override var playWhenReady = false
        private set
    override var positionMs = 0L
    override var durationMs = 600_000L

    var landSeek: (Long) -> Long = { it }
    var reportSeeksLater = false
    private var unreported: Pair<PlayerPosition, PlayerPosition>? = null
    val seeks = mutableListOf<Long>()

    override fun addListener(listener: VideoPlayerListener) {
        listeners += listener
    }

    override fun removeListener(listener: VideoPlayerListener) {
        listeners -= listener
    }

    override fun setQueue(items: List<PlayerItem>, startIndex: Int, startPositionMs: Long) {
        queue = items
        index = startIndex
        positionMs = startPositionMs
        listeners.toList().forEach { it.onItemTransition(currentItem, ItemTransitionReason.Other) }
    }

    override fun play() = setPlaying(true)
    override fun pause() = setPlaying(false)

    private fun setPlaying(playing: Boolean) {
        playWhenReady = playing
        if (isPlaying == playing) return
        isPlaying = playing
        listeners.toList().forEach { it.onIsPlayingChanged(playing) }
    }

    override fun clear() {
        moveTo(-1, 0, PositionJumpReason.Other, ItemTransitionReason.Other)
        setPlaying(false)
    }

    override fun seekTo(positionMs: Long) {
        seeks += positionMs
        val old = PlayerPosition(currentItem?.videoId, this.positionMs)
        this.positionMs = landSeek(positionMs)
        val new = PlayerPosition(currentItem?.videoId, this.positionMs)
        if (reportSeeksLater) unreported = old to new else jumped(old, new, PositionJumpReason.Seek)
    }

    /** Reports a seek held back by [reportSeeksLater]. */
    fun reportSeek() {
        val (old, new) = unreported ?: return
        unreported = null
        jumped(old, new, PositionJumpReason.Seek)
    }

    override fun seekBack() = seekTo(positionMs - 10_000)
    override fun seekForward() = seekTo(positionMs + 10_000)

    override fun playItem(index: Int) {
        moveTo(index, 0, PositionJumpReason.Seek, ItemTransitionReason.Seek)
        setPlaying(true)
    }

    /** The current item ends and the queue moves on by itself. */
    fun finishItem() {
        if (index + 1 >= queue.size) {
            listeners.toList().forEach { it.onEnded() }
            isPlaying = false
            listeners.toList().forEach { it.onIsPlayingChanged(false) }
        } else {
            positionMs = durationMs
            moveTo(index + 1, 0, PositionJumpReason.Other, ItemTransitionReason.Auto)
        }
    }

    private fun moveTo(newIndex: Int, position: Long, jump: PositionJumpReason, transition: ItemTransitionReason) {
        val old = PlayerPosition(currentItem?.videoId, positionMs)
        index = newIndex
        positionMs = position
        jumped(old, PlayerPosition(currentItem?.videoId, positionMs), jump)
        listeners.toList().forEach { it.onItemTransition(currentItem, transition) }
    }

    private fun jumped(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) =
        listeners.toList().forEach { it.onPositionJump(old, new, reason) }

    override val selectedSubtitleKey: String? = null
    override fun selectSubtitle(key: String?, language: String) = Unit
    override fun disableSubtitles() = Unit
}
