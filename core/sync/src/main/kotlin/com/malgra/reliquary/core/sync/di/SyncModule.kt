package com.malgra.reliquary.core.sync.di

import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import com.malgra.reliquary.core.sync.WorkManagerProgressSyncScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds fun progressSyncScheduler(scheduler: WorkManagerProgressSyncScheduler): ProgressSyncScheduler
}
