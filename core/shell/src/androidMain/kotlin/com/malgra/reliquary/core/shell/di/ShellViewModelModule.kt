package com.malgra.reliquary.core.shell.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.core.shell.MainViewModel
import com.malgra.reliquary.core.shell.VideoActionsViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts the shell's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface ShellViewModelModule {
    @Binds @IntoMap @ClassKey(MainViewModel::class)
    fun main(viewModel: MainViewModel): ViewModel

    @Binds @IntoMap @ClassKey(VideoActionsViewModel::class)
    fun videoActions(viewModel: VideoActionsViewModel): ViewModel
}
