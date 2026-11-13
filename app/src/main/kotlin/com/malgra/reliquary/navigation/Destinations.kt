package com.malgra.reliquary.navigation

import androidx.annotation.DrawableRes
import androidx.navigation3.runtime.NavKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import kotlinx.serialization.Serializable

@Serializable data object Home : NavKey
@Serializable data object Channels : NavKey
@Serializable data object Playlists : NavKey
@Serializable data object Downloads : NavKey

@Serializable data object Search : NavKey
@Serializable data object Settings : NavKey
@Serializable data class ChannelDetail(val channelId: String) : NavKey
@Serializable data class PlaylistDetail(val playlistId: String) : NavKey

/** The tabs in the navigation bar (phones) or rail (tablets and foldables). */
enum class TopLevelDestination(val key: NavKey, val label: String, @DrawableRes val icon: Int) {
    HOME(Home, "Home", ReliquaryIcons.Home),
    CHANNELS(Channels, "Channels", ReliquaryIcons.Channels),
    PLAYLISTS(Playlists, "Playlists", ReliquaryIcons.Playlists),
    DOWNLOADS(Downloads, "Downloads", ReliquaryIcons.Downloads),
}
