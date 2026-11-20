package com.malgra.reliquary.di

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.core.ui.viewmodel.ViewModelGraph
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.reflect.KClass

/** The screens' ViewModels and factories, from each feature's `@IntoMap` bindings. */
@Singleton
class HiltViewModelGraph @Inject constructor(
    private val viewModels: Map<Class<*>, @JvmSuppressWildcards Provider<ViewModel>>,
    private val factories: Map<Class<*>, @JvmSuppressWildcards Provider<Any>>,
) : ViewModelGraph {
    override fun <VM : ViewModel> create(type: KClass<VM>): VM =
        type.java.cast(checkNotNull(viewModels[type.java]) { "No ViewModel binding for $type" }.get())

    override fun <F : Any> factory(type: KClass<F>): F =
        type.java.cast(checkNotNull(factories[type.java]) { "No factory binding for $type" }.get())
}

@Module
@InstallIn(SingletonComponent::class)
interface ViewModelGraphModule {
    @Binds
    fun viewModelGraph(graph: HiltViewModelGraph): ViewModelGraph
}
