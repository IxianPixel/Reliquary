package com.malgra.reliquary.core.database.di

import android.content.Context
import androidx.room.Room
import com.malgra.reliquary.core.database.ReliquaryDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): ReliquaryDatabase =
        Room.databaseBuilder(context, ReliquaryDatabase::class.java, "reliquary.db").build()

    @Provides fun videoDao(db: ReliquaryDatabase) = db.videoDao()
    @Provides fun channelDao(db: ReliquaryDatabase) = db.channelDao()
    @Provides fun playlistDao(db: ReliquaryDatabase) = db.playlistDao()
    @Provides fun listingDao(db: ReliquaryDatabase) = db.listingDao()
    @Provides fun pendingProgressDao(db: ReliquaryDatabase) = db.pendingProgressDao()
    @Provides fun downloadDao(db: ReliquaryDatabase) = db.downloadDao()
}
