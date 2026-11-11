package com.malgra.reliquary.core.model

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A video in the Tube Archivist library. IDs are YouTube IDs. URLs are absolute,
 * resolved against the signed-in server. Watch state is the server's as of the
 * last refresh.
 */
data class Video(
    val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val description: String?,
    val type: VideoType,
    val duration: Duration,
    /** When the video was published on YouTube; null if the server sent no date. */
    val published: Instant?,
    /** When the server downloaded it into the archive. */
    val addedToArchive: Instant?,
    val mediaUrl: String,
    val mediaSizeBytes: Long,
    val thumbnailUrl: String?,
    val watched: Boolean,
    val position: Duration,
    /** True while a position or watched change made here hasn't reached the server. */
    val syncPending: Boolean = false,
) {
    /** 0..1, or null when there is no position to show. */
    val progress: Float?
        get() = if (watched || position <= Duration.ZERO || duration <= Duration.ZERO) {
            null
        } else {
            (position / duration).toFloat().coerceIn(0f, 1f)
        }

    val remaining: Duration
        get() = (duration - position).coerceAtLeast(Duration.ZERO)
}

/** Tube Archivist's `vid_type`, which matches the YouTube channel tabs. */
enum class VideoType(val apiValue: String) {
    Videos("videos"),
    Streams("streams"),
    Shorts("shorts");

    companion object {
        fun fromApi(value: String?): VideoType = entries.firstOrNull { it.apiValue == value } ?: Videos
    }
}

data class Channel(
    val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val bannerUrl: String?,
    val subscribed: Boolean,
    val subscriberCount: Long,
    /** Which tabs the archive has content for; null until the channel is opened once. */
    val tabs: ChannelTabs?,
)

data class ChannelTabs(
    val videos: Boolean,
    val streams: Boolean,
    val shorts: Boolean,
    val playlists: Boolean,
)

data class Playlist(
    val id: String,
    val name: String,
    val channelId: String?,
    val channelName: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val subscribed: Boolean,
    /** Entries in the YouTube playlist. */
    val entryCount: Int,
    /** Entries the server has archived. */
    val archivedCount: Int,
)

/** Filter for a channel's video list. "On this device" arrives with downloads. */
enum class WatchFilter { All, Unwatched }

/** Why loading failed, in terms a screen can explain. */
enum class LoadError { Offline, ServerError, Unexpected }

/** Combined search results across the library. */
data class SearchResults(
    val videos: List<Video>,
    val channels: List<Channel>,
    val playlists: List<Playlist>,
    /** True when the server couldn't be reached and these came from the cache on this device. */
    val fromCache: Boolean,
) {
    val isEmpty: Boolean get() = videos.isEmpty() && channels.isEmpty() && playlists.isEmpty()

    companion object {
        val Empty = SearchResults(emptyList(), emptyList(), emptyList(), fromCache = false)
    }
}

/**
 * Playback position as the app knows it. The server stores no timestamp for
 * position, so the client tracks when it last changed locally and whether that
 * change still has to be pushed.
 */
data class WatchProgress(
    val videoId: String,
    val position: Duration,
    val updatedAtEpochMillis: Long,
    val pendingSync: Boolean,
)

enum class DownloadStatus { Queued, Downloading, Paused, Completed, Failed }
