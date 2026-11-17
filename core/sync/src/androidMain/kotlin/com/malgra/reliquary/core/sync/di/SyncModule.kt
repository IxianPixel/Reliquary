package com.malgra.reliquary.core.sync.di

import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import com.malgra.reliquary.core.data.rules.LibrarySyncScheduler
import com.malgra.reliquary.core.sync.WorkManagerLibrarySyncScheduler
import com.malgra.reliquary.core.sync.WorkManagerProgressSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds fun progressSyncScheduler(scheduler: WorkManagerProgressSyncScheduler): ProgressSyncScheduler

    @Binds fun librarySyncScheduler(scheduler: WorkManagerLibrarySyncScheduler): LibrarySyncScheduler
}
