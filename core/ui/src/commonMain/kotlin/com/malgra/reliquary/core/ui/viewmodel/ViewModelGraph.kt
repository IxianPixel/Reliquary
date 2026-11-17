package com.malgra.reliquary.core.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.reflect.KClass

/**
 * Builds the screens' ViewModels: a Hilt multibinding on Android, the hand-wired
 * graph on desktop. ViewModels that take arguments (a channel ID, say) have a
 * nested `Factory` with an `@Inject` constructor, which [factory] returns.
 */
interface ViewModelGraph {
    /** A new instance of a ViewModel with an `@Inject` constructor. */
    fun <VM : ViewModel> create(type: KClass<VM>): VM

    /** The factory of a ViewModel that takes arguments, e.g. `ChannelViewModel.Factory`. */
    fun <F : Any> factory(type: KClass<F>): F
}

val LocalViewModelGraph = staticCompositionLocalOf<ViewModelGraph> { error("No ViewModelGraph provided") }

/** The screen's [VM], kept in the nearest ViewModelStore (the Nav 3 entry's) under [key]. */
@Composable
inline fun <reified VM : ViewModel> injectedViewModel(key: String? = null): VM {
    val graph = LocalViewModelGraph.current
    return viewModel(key = key) { graph.create(VM::class) }
}

/** Like [injectedViewModel], for a ViewModel built by its factory [F]: `injectedViewModel<ChannelViewModel, ChannelViewModel.Factory>(channelId) { it.create(channelId) }`. */
@Composable
inline fun <reified VM : ViewModel, reified F : Any> injectedViewModel(
    key: String,
    crossinline create: (F) -> VM,
): VM {
    val graph = LocalViewModelGraph.current
    return viewModel(key = key) { create(graph.factory(F::class)) }
}
