package com.malgra.reliquary.feature.home.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.feature.home.HomeViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts this feature's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface HomeViewModelModule {
    @Binds @IntoMap @ClassKey(HomeViewModel::class)
    fun home(viewModel: HomeViewModel): ViewModel
}
