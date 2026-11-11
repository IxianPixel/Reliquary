package com.malgra.reliquary.core.database.entity

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
