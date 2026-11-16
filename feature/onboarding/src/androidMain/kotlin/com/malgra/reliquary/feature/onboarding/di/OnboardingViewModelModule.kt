package com.malgra.reliquary.feature.onboarding.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.feature.onboarding.OnboardingViewModel
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap

/** Puts this feature's ViewModels into the app's ViewModelGraph (core:ui). */
@Module
@InstallIn(SingletonComponent::class)
interface OnboardingViewModelModule {
    @Binds @IntoMap @ClassKey(OnboardingViewModel::class)
    fun onboarding(viewModel: OnboardingViewModel): ViewModel
}
