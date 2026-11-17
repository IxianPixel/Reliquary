package com.malgra.reliquary.desktop

import com.malgra.reliquary.core.data.AppDirectories
import com.malgra.reliquary.core.shell.MainViewModel
import com.malgra.reliquary.core.shell.VideoActionsViewModel
import com.malgra.reliquary.feature.channels.ChannelSettingsViewModel
import com.malgra.reliquary.feature.channels.ChannelViewModel
import com.malgra.reliquary.feature.channels.ChannelsViewModel
import com.malgra.reliquary.feature.downloads.DownloadsViewModel
import com.malgra.reliquary.feature.home.HomeViewModel
import com.malgra.reliquary.feature.onboarding.OnboardingViewModel
import com.malgra.reliquary.feature.player.PlayerViewModel
import com.malgra.reliquary.feature.playlists.PlaylistViewModel
import com.malgra.reliquary.feature.playlists.PlaylistsViewModel
import com.malgra.reliquary.feature.queue.QueueViewModel
import com.malgra.reliquary.feature.search.SearchViewModel
import com.malgra.reliquary.feature.settings.SettingsViewModel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** A ViewModel missing from DesktopViewModelGraph would only fail when its screen opens; this fails here instead. */
class ViewModelGraphTest {

    @get:Rule val folder = TemporaryFolder()

    private val app by lazy { AppGraph(AppDirectories(folder.root), logHttp = false) }
    private val graph by lazy { DesktopViewModelGraph(app) }

    @After fun closeApp() = app.close()

    @Test
    fun buildsEveryViewModel() {
        listOf(
            MainViewModel::class, VideoActionsViewModel::class, ChannelsViewModel::class, DownloadsViewModel::class,
            HomeViewModel::class, OnboardingViewModel::class, PlaylistsViewModel::class, QueueViewModel::class,
            SearchViewModel::class, SettingsViewModel::class,
        ).forEach { graph.create(it) }
    }

    @Test
    fun buildsEveryFactory() {
        listOf(ChannelViewModel.Factory::class, ChannelSettingsViewModel.Factory::class, PlaylistViewModel.Factory::class,
            PlayerViewModel.Factory::class)
            .forEach { graph.factory(it) }
    }
}
