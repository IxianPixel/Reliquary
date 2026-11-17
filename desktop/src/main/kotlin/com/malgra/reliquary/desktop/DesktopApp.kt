package com.malgra.reliquary.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.core.shell.ChannelDetail
import com.malgra.reliquary.core.shell.Channels
import com.malgra.reliquary.core.shell.Downloads
import com.malgra.reliquary.core.shell.Home
import com.malgra.reliquary.core.shell.MainViewModel
import com.malgra.reliquary.core.shell.PlaylistDetail
import com.malgra.reliquary.core.shell.Playlists
import com.malgra.reliquary.core.shell.Queue
import com.malgra.reliquary.core.shell.Search
import com.malgra.reliquary.core.shell.Settings
import com.malgra.reliquary.core.shell.TopLevelDestination
import com.malgra.reliquary.core.shell.VideoActionsViewModel
import com.malgra.reliquary.core.ui.LocalVideoActions
import com.malgra.reliquary.core.ui.viewmodel.injectedViewModel
import com.malgra.reliquary.feature.channels.ChannelScreen
import com.malgra.reliquary.feature.channels.ChannelsScreen
import com.malgra.reliquary.feature.downloads.DownloadsScreen
import com.malgra.reliquary.feature.home.HomeScreen
import com.malgra.reliquary.feature.onboarding.OnboardingRoute
import com.malgra.reliquary.feature.player.LocalPlayerWindowState
import com.malgra.reliquary.feature.player.PlayerSheet
import com.malgra.reliquary.feature.player.VideoPlayer
import com.malgra.reliquary.feature.player.isPlayerSheetShowing
import com.malgra.reliquary.feature.player.rememberPlayerSheetState
import com.malgra.reliquary.feature.playlists.PlaylistScreen
import com.malgra.reliquary.feature.playlists.PlaylistsScreen
import com.malgra.reliquary.feature.queue.QueueScreen
import com.malgra.reliquary.feature.search.SearchScreen
import com.malgra.reliquary.feature.settings.SettingsScreen
import org.jetbrains.compose.resources.painterResource

/** Back from the keyboard or the mouse's back button: the window catches it, the shell decides what it means. */
class BackShortcuts {
    @Volatile var onBack: () -> Unit = {}
}

/** The desktop counterpart of the phone's ReliquaryApp: sign-in, or the library with its tabs and the player over it. */
@Composable
fun DesktopApp(
    player: VideoPlayer,
    windowState: WindowState,
    backShortcuts: BackShortcuts,
    viewModel: MainViewModel = injectedViewModel(),
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    // Nothing should keep streaming from a server the user has signed out of.
    val signedOut = session is SessionState.SignedOut
    LaunchedEffect(signedOut) { if (signedOut) player.clear() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (session) {
            SessionState.Loading -> Unit // the window background shows until the stored session is read
            is SessionState.SignedOut -> OnboardingRoute()
            is SessionState.SignedIn -> MainShell(viewModel, player, windowState, backShortcuts)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MainShell(viewModel: MainViewModel, player: VideoPlayer, windowState: WindowState, backShortcuts: BackShortcuts) {
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    // Nothing outlives the process on desktop, so the back stack needn't be saveable.
    val backStack = remember { mutableStateListOf<NavKey>(Home) }
    // The tab whose stack is showing: the nearest top-level key from the top of the stack.
    val selectedTab = backStack.lastOrNull { key -> TopLevelDestination.entries.any { it.key == key } }
    val playerSheet = rememberPlayerSheetState()
    val sheetShowing = isPlayerSheetShowing(playerSheet, player)
    val videoActions: VideoActionsViewModel = injectedViewModel()
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    // Like Escape: the expanded player first (collapsing it also leaves full screen), then the screens.
    val shortcutBack: () -> Unit = { if (playerSheet.isExpanded) playerSheet.collapse() else back() }
    DisposableEffect(backShortcuts) {
        backShortcuts.onBack = shortcutBack
        onDispose { backShortcuts.onBack = {} }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) {
                if (it.button == PointerButton.Back) shortcutBack()
            },
    ) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.filter { it != TopLevelDestination.QUEUE || isAdmin }.forEach { destination ->
                    item(
                        selected = destination.key == selectedTab,
                        onClick = {
                            playerSheet.collapse()
                            backStack.selectTab(destination.key)
                        },
                        icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                        label = { Text(destination.label) },
                        badge = if (destination == TopLevelDestination.DOWNLOADS && pendingSync > 0) {
                            { Badge { Text(pendingSync.toString()) } }
                        } else {
                            null
                        },
                    )
                }
            },
            // A side rail at every window size: a bottom bar looks out of place on a desktop.
            layoutType = NavigationSuiteType.NavigationRail,
        ) {
            CompositionLocalProvider(LocalVideoActions provides videoActions) {
                NavDisplay(
                    backStack = backStack,
                    // Leave room for the mini-player so it doesn't cover the end of a list.
                    modifier = Modifier.padding(bottom = if (sheetShowing) MiniPlayerSpace else 0.dp),
                    onBack = back,
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = entryProvider {
                        val openVideo: (String) -> Unit = { playerSheet.open(it) }
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
                            // Videos opened here play with the rest of the playlist queued after them.
                            PlaylistScreen(
                                playlistId = key.playlistId,
                                onBack = back,
                                onOpenVideo = { id -> playerSheet.open(id, key.playlistId) },
                            )
                        }
                        entry<Downloads> { DownloadsScreen(onOpenVideo = openVideo) }
                        entry<Queue> { QueueScreen() }
                        entry<Search> {
                            SearchScreen(
                                onBack = back,
                                onOpenVideo = openVideo,
                                onOpenChannel = openChannel,
                                onOpenPlaylist = openPlaylist,
                            )
                        }
                        entry<Settings> { SettingsScreen() }
                    },
                )
            }
        }

        CompositionLocalProvider(
            LocalVideoActions provides videoActions,
            LocalPlayerWindowState provides windowState,
        ) {
            PlayerSheet(
                state = playerSheet,
                player = player,
                bottomReserve = 0.dp, // the navigation is a side rail
                onOpenChannel = { backStack.add(ChannelDetail(it)) },
            )
        }
    }
}

/** The collapsed player's height plus its margin. */
private val MiniPlayerSpace = 72.dp

/** Home is the root; any other tab sits on top of it, so Back from a tab returns Home. */
private fun MutableList<NavKey>.selectTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(Home)
    if (tab != Home) add(tab)
}
