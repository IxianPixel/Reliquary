package com.malgra.reliquary.desktop

import androidx.lifecycle.ViewModel
import com.malgra.reliquary.core.shell.MainViewModel
import com.malgra.reliquary.core.shell.VideoActionsViewModel
import com.malgra.reliquary.core.ui.viewmodel.ViewModelGraph
import com.malgra.reliquary.feature.channels.ChannelSettingsViewModel
import com.malgra.reliquary.feature.channels.ChannelViewModel
import com.malgra.reliquary.feature.channels.ChannelsViewModel
import com.malgra.reliquary.feature.downloads.DownloadsViewModel
import com.malgra.reliquary.feature.home.HomeViewModel
import com.malgra.reliquary.feature.onboarding.OnboardingViewModel
import com.malgra.reliquary.feature.playlists.PlaylistViewModel
import com.malgra.reliquary.feature.playlists.PlaylistsViewModel
import com.malgra.reliquary.feature.queue.QueueViewModel
import com.malgra.reliquary.feature.search.SearchViewModel
import com.malgra.reliquary.feature.settings.SettingsViewModel
import kotlin.reflect.KClass

/**
 * The screens' ViewModels, built from [app]. Android gets these from Hilt
 * multibindings; a type missing here fails at runtime, so ViewModelGraphTest
 * asks for every one.
 */
class DesktopViewModelGraph(private val app: AppGraph) : ViewModelGraph {

    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(type: KClass<VM>): VM = with(app) {
        when (type) {
            MainViewModel::class -> MainViewModel(sessions, progress, account)
            VideoActionsViewModel::class -> VideoActionsViewModel(downloads, progress)
            ChannelsViewModel::class -> ChannelsViewModel(channels, account)
            DownloadsViewModel::class -> DownloadsViewModel(downloads, progress, settings, keepOffline, librarySync, playlists)
            HomeViewModel::class -> HomeViewModel(videos, connection, progress)
            OnboardingViewModel::class -> OnboardingViewModel(auth, sessions)
            PlaylistsViewModel::class -> PlaylistsViewModel(playlists, account)
            QueueViewModel::class -> QueueViewModel(queue, account)
            SearchViewModel::class -> SearchViewModel(search)
            SettingsViewModel::class -> SettingsViewModel(sessions, auth, downloads, settings)
            else -> error("No ViewModel binding for $type")
        } as VM
    }

    @Suppress("UNCHECKED_CAST")
    override fun <F : Any> factory(type: KClass<F>): F = with(app) {
        when (type) {
            ChannelViewModel.Factory::class -> ChannelViewModel.Factory(channels, videos, playlists, keepOffline, account)
            ChannelSettingsViewModel.Factory::class -> ChannelSettingsViewModel.Factory(channelSettings)
            PlaylistViewModel.Factory::class -> PlaylistViewModel.Factory(playlists, videos, keepOffline, account)
            else -> error("No factory binding for $type")
        } as F
    }
}
