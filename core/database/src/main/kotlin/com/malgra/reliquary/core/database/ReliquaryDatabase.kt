package com.malgra.reliquary.core.database

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import com.malgra.reliquary.core.database.dao.ChannelDao
import com.malgra.reliquary.core.database.dao.DownloadDao
import com.malgra.reliquary.core.database.dao.KeepOfflineRuleDao
import com.malgra.reliquary.core.database.dao.ListingDao
import com.malgra.reliquary.core.database.dao.PendingProgressDao
import com.malgra.reliquary.core.database.dao.PlaylistDao
import com.malgra.reliquary.core.database.dao.VideoDao
import com.malgra.reliquary.core.database.dao.VideoStatsDao
import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.database.entity.KeepOfflineRuleEntity
import com.malgra.reliquary.core.database.entity.ListingItemEntity
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.database.entity.PlaylistEntity
import com.malgra.reliquary.core.database.entity.VideoEntity

@Database(
    entities = [
        VideoEntity::class,
        ChannelEntity::class,
        PlaylistEntity::class,
        ListingItemEntity::class,
        ListingStateEntity::class,
        PendingProgressEntity::class,
        DownloadEntity::class,
        KeepOfflineRuleEntity::class,
    ],
    version = 7,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), // adds pending_progress
        AutoMigration(from = 2, to = 3), // adds download
        AutoMigration(from = 3, to = 4), // adds keep_offline_rule and download.ruleId
        AutoMigration(from = 4, to = 5), // adds video.subtitlesJson
        AutoMigration(from = 5, to = 6), // adds download.thumbnailPath
        AutoMigration(from = 6, to = 7), // adds video.sponsorSegmentsJson
    ],
)
abstract class ReliquaryDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun channelDao(): ChannelDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun listingDao(): ListingDao
    abstract fun pendingProgressDao(): PendingProgressDao
    abstract fun downloadDao(): DownloadDao
    abstract fun keepOfflineRuleDao(): KeepOfflineRuleDao
    abstract fun videoStatsDao(): VideoStatsDao
}
