package com.malgra.reliquary.feature.player

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Lets the app's ViewModelGraph build the player's ViewModel. */
@Module
@InstallIn(SingletonComponent::class)
internal interface PlayerViewModelModule {
    @Binds
    @IntoMap
    @ClassKey(PlayerViewModel.Factory::class)
    fun player(factory: PlayerViewModel.Factory): Any
}
