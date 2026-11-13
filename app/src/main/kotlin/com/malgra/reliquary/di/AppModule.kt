package com.malgra.reliquary.di

import com.malgra.reliquary.R
import com.malgra.reliquary.core.data.di.NotificationIcon
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun notificationIcon(): NotificationIcon = NotificationIcon(R.drawable.ic_notification)
}
