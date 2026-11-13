package com.malgra.reliquary.core.download.di

import com.malgra.reliquary.core.data.download.DownloadScheduler
import com.malgra.reliquary.core.download.WorkManagerDownloadScheduler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
interface DownloadModule {
    @Binds fun downloadScheduler(scheduler: WorkManagerDownloadScheduler): DownloadScheduler
}
