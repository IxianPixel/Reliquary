package com.malgra.reliquary.feature.channels.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.feature.channels.ChannelSettingsViewModel
import com.malgra.reliquary.feature.channels.ChannelViewModel
import com.malgra.reliquary.feature.channels.ChannelsViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts this feature's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface ChannelsViewModelModule {
    @Binds @IntoMap @ClassKey(ChannelsViewModel::class)
    fun channels(viewModel: ChannelsViewModel): ViewModel

    @Binds @IntoMap @ClassKey(ChannelViewModel.Factory::class)
    fun channel(factory: ChannelViewModel.Factory): Any

    @Binds @IntoMap @ClassKey(ChannelSettingsViewModel.Factory::class)
    fun channelSettings(factory: ChannelSettingsViewModel.Factory): Any
}
