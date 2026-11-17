package com.malgra.reliquary.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * The library as last fetched from the signed-in server. It is a cache: the
 * server is the source of these rows, and they are cleared when the user signs
 * out or switches server. URLs are stored absolute.
 */

@Entity(tableName = "video", indices = [Index("channelId")])
data class VideoEntity(
    @PrimaryKey val id: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val description: String?,
    /** `videos`, `streams` or `shorts`. */
    val type: String,
    val durationSeconds: Long,
    val publishedEpochSeconds: Long?,
    val addedEpochSeconds: Long?,
    val mediaUrl: String,
    val mediaSizeBytes: Long,
    val thumbnailUrl: String?,
    val watched: Boolean,
    val watchedEpochSeconds: Long?,
    val positionSeconds: Double,
    val fetchedAtMillis: Long,
    /** The server's subtitle tracks as JSON (see the data layer's mapper); null if it has none. */
    val subtitlesJson: String? = null,
    /** The video's skippable SponsorBlock segments as JSON (see the data layer's mapper); null if none. */
    val sponsorSegmentsJson: String? = null,
)

@Entity(tableName = "channel")
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val thumbnailUrl: String?,
    val bannerUrl: String?,
    val subscribed: Boolean,
    val subscriberCount: Long,
    /** Null until `/nav/` has been fetched for this channel. */
    val hasVideos: Boolean?,
    val hasStreams: Boolean?,
    val hasShorts: Boolean?,
    val hasPlaylists: Boolean?,
    val fetchedAtMillis: Long,
)

@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val channelId: String?,
    val channelName: String?,
    val description: String?,
    val thumbnailUrl: String?,
    val subscribed: Boolean,
    val entryCount: Int,
    val archivedCount: Int,
    val fetchedAtMillis: Long,
)

/**
 * One row of a server-ordered list, such as "recently added" or a channel's
 * unwatched videos. [listKey] names the list and the query behind it;
 * [position] keeps the server's order across pages.
 */
@Entity(
    tableName = "listing_item",
    primaryKeys = ["listKey", "position"],
    indices = [Index("listKey", "itemId")],
)
data class ListingItemEntity(
    val listKey: String,
    val position: Int,
    val itemId: String,
)

/** Paging state of a list: the next page to fetch (null at the end) and when page 1 was last fetched. */
@Entity(tableName = "listing_state")
data class ListingStateEntity(
    @PrimaryKey val listKey: String,
    val nextPage: Int?,
    val totalCount: Int?,
    val refreshedAtMillis: Long,
)

/**
 * Watch progress made on this device that the server hasn't confirmed yet.
 * A row exists only while there is something to push; sync deletes it once the
 * server has the change (and the video row has the server's answer).
 */
@Entity(tableName = "pending_progress")
data class PendingProgressEntity(
    @PrimaryKey val videoId: String,
    val positionSeconds: Double,
    /** Set when the user marked the video watched (true) or unwatched (false) here. */
    val watched: Boolean?,
    val updatedAtMillis: Long,
    /** When the playback session (or mark) that produced this change started. */
    val sessionStartedAtMillis: Long,
)

/** A video with any unsynced local progress over it, and its download if it has one. */
data class VideoWithPending(
    @Embedded val video: VideoEntity,
    @Embedded(prefix = "pending_") val pending: PendingProgressEntity?,
    @Embedded(prefix = "download_") val download: DownloadEntity? = null,
)

/** A video saved (or being saved) to this device. [state] is a DownloadStatus name. */
@Entity(tableName = "download")
data class DownloadEntity(
    @PrimaryKey val videoId: String,
    val state: String,
    val bytesDownloaded: Long,
    /** From the server's Content-Range, else the video's media size; 0 if unknown. */
    val totalBytes: Long,
    val createdAtMillis: Long,
    /** The keep-offline rule that downloaded it, or null if the user did; only rule downloads are removed automatically. */
    val ruleId: String? = null,
    /** The thumbnail saved with the download, so it shows offline; null until saved. */
    val thumbnailPath: String? = null,
)

data class DownloadWithVideo(
    @Embedded val download: DownloadEntity,
    @Embedded(prefix = "video_") val video: VideoEntity?,
)

/**
 * Keeps a channel's or playlist's videos downloaded. [id] is "channel:<id>"
 * or "playlist:<id>", so each source has at most one rule.
 */
@Entity(tableName = "keep_offline_rule")
data class KeepOfflineRuleEntity(
    @PrimaryKey val id: String,
    /** "channel" or "playlist". */
    val kind: String,
    val sourceId: String,
    /** Saved with the rule so the list needs no lookups. */
    val name: String,
    val thumbnailUrl: String?,
    /** How many of the newest videos to keep; null keeps a whole playlist. */
    val keepCount: Int?,
    val unwatchedOnly: Boolean,
    val wifiOnly: Boolean,
    val removeWhenWatched: Boolean,
    val createdAtMillis: Long,
)
