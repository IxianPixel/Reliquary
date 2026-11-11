package com.malgra.reliquary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.feature.channels.ChannelScreen
import com.malgra.reliquary.feature.channels.ChannelsScreen
import com.malgra.reliquary.feature.downloads.DownloadsScreen
import com.malgra.reliquary.feature.home.HomeScreen
import com.malgra.reliquary.feature.onboarding.OnboardingRoute
import com.malgra.reliquary.feature.player.PlayerScreen
import com.malgra.reliquary.feature.playlists.PlaylistScreen
import com.malgra.reliquary.feature.playlists.PlaylistsScreen
import com.malgra.reliquary.feature.search.SearchScreen
import com.malgra.reliquary.feature.settings.SettingsScreen
import com.malgra.reliquary.navigation.ChannelDetail
import com.malgra.reliquary.navigation.Channels
import com.malgra.reliquary.navigation.Downloads
import com.malgra.reliquary.navigation.Home
import com.malgra.reliquary.navigation.Player
import com.malgra.reliquary.navigation.PlaylistDetail
import com.malgra.reliquary.navigation.Playlists
import com.malgra.reliquary.navigation.Search
import com.malgra.reliquary.navigation.Settings
import com.malgra.reliquary.navigation.TopLevelDestination

@Composable
fun ReliquaryApp(viewModel: MainViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (session) {
            SessionState.Loading -> Unit // the window background shows until the stored session is read
            is SessionState.SignedOut -> OnboardingRoute()
            is SessionState.SignedIn -> MainShell()
        }
    }
}

@Composable
private fun MainShell() {
    val backStack = rememberNavBackStack(Home)
    // The tab whose stack is showing: the nearest top-level key from the top of the stack.
    val selectedTab = backStack.lastOrNull { key -> TopLevelDestination.entries.any { it.key == key } }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination.key == selectedTab,
                    onClick = { backStack.selectTab(destination.key) },
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(destination.label) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
                val openVideo: (String) -> Unit = { backStack.add(Player(it)) }
                val openChannel: (String) -> Unit = { backStack.add(ChannelDetail(it)) }
                val openPlaylist: (String) -> Unit = { backStack.add(PlaylistDetail(it)) }

                entry<Home> {
                    HomeScreen(
                        onOpenSearch = { backStack.add(Search) },
                        onOpenSettings = { backStack.add(Settings) },
                        onOpenVideo = openVideo,
                    )
                }
                entry<Channels> { ChannelsScreen(onOpenChannel = openChannel) }
                entry<ChannelDetail> { key ->
                    ChannelScreen(
                        channelId = key.channelId,
                        onBack = back,
                        onOpenVideo = openVideo,
                        onOpenPlaylist = openPlaylist,
                    )
                }
                entry<Playlists> { PlaylistsScreen(onOpenPlaylist = openPlaylist) }
                entry<PlaylistDetail> { key ->
                    PlaylistScreen(playlistId = key.playlistId, onBack = back, onOpenVideo = openVideo)
                }
                entry<Downloads> { DownloadsScreen() }
                entry<Search> {
                    SearchScreen(
                        onBack = back,
                        onOpenVideo = openVideo,
                        onOpenChannel = openChannel,
                        onOpenPlaylist = openPlaylist,
                    )
                }
                entry<Settings> { SettingsScreen() }
                entry<Player> { PlayerScreen() }
            },
        )
    }
}

/** Home is the root; any other tab sits on top of it, so Back from a tab returns Home. */
private fun NavBackStack<NavKey>.selectTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(Home)
    if (tab != Home) add(tab)
}
