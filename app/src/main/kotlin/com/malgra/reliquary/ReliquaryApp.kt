package com.malgra.reliquary

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
import com.malgra.reliquary.core.ui.LocalVideoActions
import com.malgra.reliquary.core.ui.rememberLocalNetworkAccess
import com.malgra.reliquary.feature.channels.ChannelScreen
import com.malgra.reliquary.feature.channels.ChannelsScreen
import com.malgra.reliquary.feature.downloads.DownloadsScreen
import com.malgra.reliquary.feature.home.HomeScreen
import com.malgra.reliquary.feature.onboarding.OnboardingRoute
import com.malgra.reliquary.feature.player.PlayerSheet
import com.malgra.reliquary.feature.player.isPlayerSheetShowing
import com.malgra.reliquary.feature.player.rememberMediaController
import com.malgra.reliquary.feature.player.rememberPlayerSheetState
import com.malgra.reliquary.feature.playlists.PlaylistScreen
import com.malgra.reliquary.feature.playlists.PlaylistsScreen
import com.malgra.reliquary.feature.search.SearchScreen
import com.malgra.reliquary.feature.settings.SettingsScreen
import com.malgra.reliquary.navigation.ChannelDetail
import com.malgra.reliquary.navigation.Channels
import com.malgra.reliquary.navigation.Downloads
import com.malgra.reliquary.navigation.Home
import com.malgra.reliquary.navigation.PlaylistDetail
import com.malgra.reliquary.navigation.Playlists
import com.malgra.reliquary.navigation.Search
import com.malgra.reliquary.navigation.Settings
import com.malgra.reliquary.navigation.TopLevelDestination

@Composable
fun ReliquaryApp(viewModel: MainViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        when (val current = session) {
            SessionState.Loading -> Unit // the window background shows until the stored session is read
            is SessionState.SignedOut -> OnboardingRoute()
            is SessionState.SignedIn -> {
                MainShell(viewModel)
                LocalNetworkPrompt(serverUrl = current.session.baseUrl)
            }
        }
    }
}

@Composable
private fun MainShell(viewModel: MainViewModel) {
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(Home)
    // The tab whose stack is showing: the nearest top-level key from the top of the stack.
    val selectedTab = backStack.lastOrNull { key -> TopLevelDestination.entries.any { it.key == key } }
    // One connection to the playback service, and the player sheet over everything.
    val controller = rememberMediaController()
    val playerSheet = rememberPlayerSheetState()
    val sheetShowing = isPlayerSheetShowing(playerSheet, controller)
    val videoActions: VideoActionsViewModel = hiltViewModel()
    val density = LocalDensity.current
    // Space the navigation takes at the bottom (a bar, or just the gesture area with a rail).
    var bottomReserve by remember { mutableStateOf(0.dp) }
    var windowHeightPx by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { windowHeightPx = it.height },
    ) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
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
            layoutType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2()),
        ) {
            CompositionLocalProvider(LocalVideoActions provides videoActions) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { coordinates ->
                            val bottom = coordinates.positionInWindow().y + coordinates.size.height
                            bottomReserve = with(density) { (windowHeightPx - bottom).coerceAtLeast(0f).toDp() }
                        }
                        // Leave room for the mini-player so it doesn't cover the end of a list.
                        .padding(bottom = if (sheetShowing) MiniPlayerSpace else 0.dp),
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
        }

        CompositionLocalProvider(LocalVideoActions provides videoActions) {
            PlayerSheet(
                state = playerSheet,
                controller = controller,
                bottomReserve = bottomReserve,
                onOpenChannel = { backStack.add(ChannelDetail(it)) },
            )
        }
    }
}

/** The collapsed player's height plus its margin. */
private val MiniPlayerSpace = 72.dp

/** Home is the root; any other tab sits on top of it, so Back from a tab returns Home. */
private fun NavBackStack<NavKey>.selectTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(Home)
    if (tab != Home) add(tab)
}

/**
 * Asks once per launch for local network access when the signed-in server is
 * on the local network and the app may not reach it (Android 17).
 */
@Composable
private fun LocalNetworkPrompt(serverUrl: String) {
    val access = rememberLocalNetworkAccess(serverUrl)
    var dismissed by rememberSaveable { mutableStateOf(false) }
    if (!access.needed || dismissed) return
    AlertDialog(
        onDismissRequest = { dismissed = true },
        title = { Text(stringResource(R.string.local_network_title)) },
        text = { Text(stringResource(R.string.local_network_body)) },
        confirmButton = {
            TextButton(onClick = access::request) {
                Text(stringResource(if (access.settingsOnly) R.string.local_network_settings else R.string.local_network_allow))
            }
        },
        dismissButton = { TextButton(onClick = { dismissed = true }) { Text(stringResource(R.string.local_network_not_now)) } },
    )
}

