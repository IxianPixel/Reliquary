package com.malgra.reliquary.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.malgra.reliquary.core.database.dao.ChannelDao
import com.malgra.reliquary.core.database.dao.ListingDao
import com.malgra.reliquary.core.database.dao.PlaylistDao
import com.malgra.reliquary.core.database.dao.VideoDao
import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.ListingItemEntity
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.database.entity.PlaylistEntity
import com.malgra.reliquary.core.database.entity.VideoEntity

@Database(
    entities = [
        VideoEntity::class,
        ChannelEntity::class,
        PlaylistEntity::class,
        ListingItemEntity::class,
        ListingStateEntity::class,
    ],
    version = 1,
)
abstract class ReliquaryDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listingDao(): ListingDao
}
