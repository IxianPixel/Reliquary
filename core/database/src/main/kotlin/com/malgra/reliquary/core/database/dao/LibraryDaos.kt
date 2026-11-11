package com.malgra.reliquary.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.database.entity.DownloadWithVideo
import com.malgra.reliquary.core.database.entity.ListingItemEntity
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.database.entity.PlaylistEntity
import com.malgra.reliquary.core.database.entity.VideoEntity
import com.malgra.reliquary.core.database.entity.VideoWithPending
import kotlinx.coroutines.flow.Flow

/** Columns for [VideoWithPending]: the video, its pending progress and its download, if any. */
internal const val VIDEO_WITH_PENDING = """
    video.*,
    pending_progress.videoId AS pending_videoId,
    pending_progress.positionSeconds AS pending_positionSeconds,
    pending_progress.watched AS pending_watched,
    pending_progress.updatedAtMillis AS pending_updatedAtMillis,
    pending_progress.sessionStartedAtMillis AS pending_sessionStartedAtMillis,
    download.videoId AS download_videoId,
    download.state AS download_state,
    download.bytesDownloaded AS download_bytesDownloaded,
    download.totalBytes AS download_totalBytes,
    download.createdAtMillis AS download_createdAtMillis
"""

/** Joins for [VIDEO_WITH_PENDING]; follows `FROM video` or a join onto video. */
internal const val VIDEO_JOINS = """
    LEFT JOIN pending_progress ON pending_progress.videoId = video.id
    LEFT JOIN download ON download.videoId = video.id
"""

@Dao
interface VideoDao {
    @Upsert suspend fun upsert(videos: List<VideoEntity>)

    @Query("SELECT $VIDEO_WITH_PENDING FROM video $VIDEO_JOINS WHERE video.id = :id")
    fun observe(id: String): Flow<VideoWithPending?>

    @Query("SELECT $VIDEO_WITH_PENDING FROM video $VIDEO_JOINS WHERE video.id = :id")
    suspend fun get(id: String): VideoWithPending?

    @Query(
        """
        SELECT $VIDEO_WITH_PENDING FROM listing_item
        JOIN video ON video.id = listing_item.itemId
        $VIDEO_JOINS
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun pagingSource(listKey: String): PagingSource<Int, VideoWithPending>

    @Query(
        """
        SELECT $VIDEO_WITH_PENDING FROM listing_item
        JOIN video ON video.id = listing_item.itemId
        $VIDEO_JOINS
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun observeListing(listKey: String): Flow<List<VideoWithPending>>

    /** Offline fallback for search: a plain substring match on cached rows. */
    @Query(
        """
        SELECT $VIDEO_WITH_PENDING FROM video $VIDEO_JOINS
        WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%'
        ORDER BY addedEpochSeconds DESC LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 30): List<VideoWithPending>
}

@Dao
interface PendingProgressDao {
    @Query("SELECT * FROM pending_progress WHERE videoId = :videoId")
    suspend fun get(videoId: String): PendingProgressEntity?

    @Query("SELECT * FROM pending_progress ORDER BY updatedAtMillis")
    suspend fun all(): List<PendingProgressEntity>

    @Query("SELECT COUNT(*) FROM pending_progress")
    fun observeCount(): Flow<Int>

    @Upsert suspend fun upsert(progress: PendingProgressEntity)

    /** Removes the row only if nothing changed it since [updatedAtMillis]; returns rows deleted. */
    @Query("DELETE FROM pending_progress WHERE videoId = :videoId AND updatedAtMillis = :updatedAtMillis")
    suspend fun deleteIfUnchanged(videoId: String, updatedAtMillis: Long): Int

    @Query("DELETE FROM pending_progress WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}

@Dao
interface ChannelDao {
    @Upsert suspend fun upsert(channels: List<ChannelEntity>)

    @Query("SELECT * FROM channel WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<ChannelEntity>

    @Query("SELECT * FROM channel WHERE id = :id")
    fun observe(id: String): Flow<ChannelEntity?>

    @Query(
        """
        SELECT channel.* FROM listing_item JOIN channel ON channel.id = listing_item.itemId
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun pagingSource(listKey: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channel WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    suspend fun search(query: String, limit: Int = 30): List<ChannelEntity>
}

@Dao
interface PlaylistDao {
    @Upsert suspend fun upsert(playlists: List<PlaylistEntity>)

    @Query("SELECT * FROM playlist WHERE id = :id")
    fun observe(id: String): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT playlist.* FROM listing_item JOIN playlist ON playlist.id = listing_item.itemId
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun pagingSource(listKey: String): PagingSource<Int, PlaylistEntity>

    @Query("SELECT * FROM playlist WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    suspend fun search(query: String, limit: Int = 30): List<PlaylistEntity>
}

@Dao
interface ListingDao {
    @Query("SELECT * FROM listing_state WHERE listKey = :listKey")
    suspend fun state(listKey: String): ListingStateEntity?

    @Query("SELECT * FROM listing_state WHERE listKey = :listKey")
    fun observeState(listKey: String): Flow<ListingStateEntity?>

    @Upsert suspend fun upsertState(state: ListingStateEntity)

    @Query("SELECT COALESCE(MAX(position) + 1, 0) FROM listing_item WHERE listKey = :listKey")
    suspend fun nextPosition(listKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ListingItemEntity>)

    @Query("DELETE FROM listing_item WHERE listKey = :listKey")
    suspend fun clearItems(listKey: String)

    /** Adds [itemIds] after the list's current end, skipping any already in it. */
    suspend fun append(listKey: String, itemIds: List<String>) {
        val present = itemIdsIn(listKey, itemIds).toSet()
        val start = nextPosition(listKey)
        insertItems(
            itemIds.distinct().filterNot { it in present }
                .mapIndexed { i, id -> ListingItemEntity(listKey, start + i, id) },
        )
    }

    @Query("SELECT itemId FROM listing_item WHERE listKey = :listKey AND itemId IN (:itemIds)")
    suspend fun itemIdsIn(listKey: String, itemIds: List<String>): List<String>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download WHERE videoId = :videoId")
    fun observe(videoId: String): Flow<DownloadEntity?>

    @Query("SELECT * FROM download WHERE videoId = :videoId")
    suspend fun get(videoId: String): DownloadEntity?

    /** Newest first, with each video's cached details. */
    @Query(
        """
        SELECT download.*,
            video.id AS video_id, video.title AS video_title, video.channelId AS video_channelId,
            video.channelName AS video_channelName, video.description AS video_description,
            video.type AS video_type, video.durationSeconds AS video_durationSeconds,
            video.publishedEpochSeconds AS video_publishedEpochSeconds,
            video.addedEpochSeconds AS video_addedEpochSeconds, video.mediaUrl AS video_mediaUrl,
            video.mediaSizeBytes AS video_mediaSizeBytes, video.thumbnailUrl AS video_thumbnailUrl,
            video.watched AS video_watched, video.watchedEpochSeconds AS video_watchedEpochSeconds,
            video.positionSeconds AS video_positionSeconds, video.fetchedAtMillis AS video_fetchedAtMillis
        FROM download LEFT JOIN video ON video.id = download.videoId
        ORDER BY download.createdAtMillis DESC
        """,
    )
    fun observeAll(): Flow<List<DownloadWithVideo>>

    @Upsert suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE download SET state = :state, bytesDownloaded = :bytes, totalBytes = :total WHERE videoId = :videoId")
    suspend fun update(videoId: String, state: String, bytes: Long, total: Long)

    @Query("DELETE FROM download WHERE videoId = :videoId")
    suspend fun delete(videoId: String)
}
