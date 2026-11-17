package com.malgra.reliquary.feature.playlists.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.feature.playlists.PlaylistViewModel
import com.malgra.reliquary.feature.playlists.PlaylistsViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts this feature's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface PlaylistsViewModelModule {
    @Binds @IntoMap @ClassKey(PlaylistsViewModel::class)
    fun playlists(viewModel: PlaylistsViewModel): ViewModel

    @Binds @IntoMap @ClassKey(PlaylistViewModel.Factory::class)
    fun playlist(factory: PlaylistViewModel.Factory): Any
}
