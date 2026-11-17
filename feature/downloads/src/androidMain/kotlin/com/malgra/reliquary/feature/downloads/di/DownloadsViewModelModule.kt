package com.malgra.reliquary.feature.downloads.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.feature.downloads.DownloadsViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts this feature's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface DownloadsViewModelModule {
    @Binds @IntoMap @ClassKey(DownloadsViewModel::class)
    fun downloads(viewModel: DownloadsViewModel): ViewModel
}
