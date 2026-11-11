package com.malgra.reliquary.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.ListingItemEntity
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.database.entity.PlaylistEntity
import com.malgra.reliquary.core.database.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Upsert suspend fun upsert(videos: List<VideoEntity>)

    @Query("SELECT * FROM video WHERE id = :id")
    fun observe(id: String): Flow<VideoEntity?>

    @Query(
        """
        SELECT video.* FROM listing_item JOIN video ON video.id = listing_item.itemId
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun pagingSource(listKey: String): PagingSource<Int, VideoEntity>

    @Query(
        """
        SELECT video.* FROM listing_item JOIN video ON video.id = listing_item.itemId
        WHERE listing_item.listKey = :listKey ORDER BY listing_item.position
        """,
    )
    fun observeListing(listKey: String): Flow<List<VideoEntity>>

    /** Offline fallback for search: a plain substring match on cached rows. */
    @Query(
        """
        SELECT * FROM video WHERE title LIKE '%' || :query || '%' OR channelName LIKE '%' || :query || '%'
        ORDER BY addedEpochSeconds DESC LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int = 30): List<VideoEntity>
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
