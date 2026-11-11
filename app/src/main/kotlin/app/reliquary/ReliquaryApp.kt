package app.reliquary

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.reliquary.feature.channels.ChannelsScreen
import app.reliquary.feature.downloads.DownloadsScreen
import app.reliquary.feature.home.HomeScreen
import app.reliquary.feature.onboarding.OnboardingScreen
import app.reliquary.feature.player.PlayerScreen
import app.reliquary.feature.playlists.PlaylistsScreen
import app.reliquary.feature.search.SearchScreen
import app.reliquary.feature.settings.SettingsScreen
import app.reliquary.navigation.Channels
import app.reliquary.navigation.Downloads
import app.reliquary.navigation.Home
import app.reliquary.navigation.Onboarding
import app.reliquary.navigation.Player
import app.reliquary.navigation.Playlists
import app.reliquary.navigation.Search
import app.reliquary.navigation.Settings
import app.reliquary.navigation.TopLevelDestination

@Composable
fun ReliquaryApp() {
    // Phase 2 replaces this with the stored sign-in state.
    val backStack = rememberNavBackStack(Onboarding)
    val current = backStack.lastOrNull()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        if (current == Onboarding) {
            OnboardingScreen(onConnected = { backStack.replaceWith(Home) })
        } else {
            MainShell(backStack)
        }
    }
}

@Composable
private fun MainShell(backStack: NavBackStack<NavKey>) {
    // The tab whose stack is showing: the first top-level key from the top of the stack.
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
            entryProvider = entryProvider {
                entry<Home> { HomeScreen() }
                entry<Channels> { ChannelsScreen() }
                entry<Playlists> { PlaylistsScreen() }
                entry<Downloads> { DownloadsScreen() }
                entry<Search> { SearchScreen() }
                entry<Settings> { SettingsScreen() }
                entry<Player> { PlayerScreen() }
            },
        )
    }
}

private fun NavBackStack<NavKey>.replaceWith(key: NavKey) {
    clear()
    add(key)
}

/** Home is the root; any other tab sits on top of it, so Back from a tab returns Home. */
private fun NavBackStack<NavKey>.selectTab(tab: NavKey) {
    if (lastOrNull() == tab) return
    clear()
    add(Home)
    if (tab != Home) add(tab)
}
