package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.model.SponsorSegment
import com.malgra.reliquary.core.model.Video
import java.io.File

// Platform-free player types, ready for commonMain (#2). media3 stays in Media3VideoPlayer.

/** Where a video or subtitle file comes from. */
sealed interface MediaSource {
    /** On the signed-in server; the platform's player adds the credentials. */
    data class Remote(val url: String) : MediaSource

    /** A downloaded copy, which plays without the server. */
    data class Local(val file: File) : MediaSource
}

/** A subtitle track as handed to the player. [key] is the Subtitle key, e.g. "en.user". */
data class SubtitleSource(val key: String, val source: MediaSource, val language: String, val label: String)

/** One entry in the player's queue. */
data class PlayerItem(
    val videoId: String,
    val source: MediaSource,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    /** The playlist this item was queued from, if any. */
    val playlistId: String?,
    val subtitles: List<SubtitleSource>,
    val sponsorSegments: List<SponsorSegment>,
)

/**
 * [localFile] and [localSubtitles] (by track key) replace the server's copies
 * when the video is downloaded.
 */
fun Video.toPlayerItem(
    localFile: File? = null,
    playlistId: String? = null,
    localSubtitles: Map<String, File> = emptyMap(),
): PlayerItem = PlayerItem(
    videoId = id,
    source = localFile?.let(MediaSource::Local) ?: MediaSource.Remote(mediaUrl),
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl,
    playlistId = playlistId,
    subtitles = subtitles.map { subtitle ->
        SubtitleSource(
            key = subtitle.key,
            source = localSubtitles[subtitle.key]?.let(MediaSource::Local) ?: MediaSource.Remote(subtitle.url),
            language = subtitle.language,
            label = subtitle.name,
        )
    },
    sponsorSegments = sponsorSegments,
)

/** Where playback is: a video (null when nothing is loaded) and a position in it. */
data class PlayerPosition(val videoId: String?, val positionMs: Long)

enum class PositionJumpReason {
    /** A seek, by the user or by us. */
    Seek,

    /** Moving to another item, or anything else. */
    Other,
}

enum class ItemTransitionReason {
    /** The previous item ended. */
    Auto,

    /** A seek to another item, e.g. a tap on "Next in". */
    Seek,

    /** A new queue, a repeat, or anything else. */
    Other,
}

/**
 * The player's callbacks, on the main thread. Positions are where playback
 * actually is: a seek may not land where it was asked to (desktop players seek
 * to the nearest keyframe), so nothing should assume it did.
 */
interface VideoPlayerListener {
    fun onIsPlayingChanged(isPlaying: Boolean) {}

    /** Playback jumped from [old] to [new], by a seek or a move to another item. */
    fun onPositionJump(old: PlayerPosition, new: PlayerPosition, reason: PositionJumpReason) {}

    /** [item] became the current one. */
    fun onItemTransition(item: PlayerItem?, reason: ItemTransitionReason) {}

    /** The current item played to its end. */
    fun onEnded() {}
}

/**
 * The parts of a video player the shared player code uses. Android wraps
 * media3 (Media3VideoPlayer); call it from the main thread only.
 */
interface VideoPlayer {
    val currentItem: PlayerItem?
    val isPlaying: Boolean

    /** Whether it plays once it can, i.e. not paused. */
    val playWhenReady: Boolean
    val positionMs: Long

    /** Zero while unknown. */
    val durationMs: Long

    fun addListener(listener: VideoPlayerListener)
    fun removeListener(listener: VideoPlayerListener)

    fun setQueue(items: List<PlayerItem>, startIndex: Int, startPositionMs: Long)
    fun play()
    fun pause()

    /** Stops and empties the queue. */
    fun clear()

    /** May land near [positionMs] rather than on it; read [positionMs] back for where it went. */
    fun seekTo(positionMs: Long)
    fun seekBack()
    fun seekForward()

    /** Plays the queue's [index]th item from its start. */
    fun playItem(index: Int)

    /** The key of the subtitle track showing now, or null when subtitles are off. */
    val selectedSubtitleKey: String?

    /**
     * Shows [key]'s track if this video has it; otherwise prefers [language],
     * which also carries over to the next video in the queue.
     */
    fun selectSubtitle(key: String?, language: String)
    fun disableSubtitles()
}
