package app.reliquary.core.model

import kotlin.time.Duration


/** A video in the Tube Archivist library. IDs are YouTube IDs. */
data class Video(
    val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val duration: Duration,
    val published: String,
    val mediaPath: String,
    val mediaSizeBytes: Long,
    val thumbnailPath: String,
    val watched: Boolean,
)

data class Channel(
    val id: String,
    val name: String,
    val thumbnailPath: String,
    val bannerPath: String?,
    val subscribed: Boolean,
)

data class Playlist(
    val id: String,
    val name: String,
    val channelName: String?,
    val thumbnailPath: String,
)

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
