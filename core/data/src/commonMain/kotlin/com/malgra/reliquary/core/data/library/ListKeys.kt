package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.model.WatchFilter

/** Names of the cached lists; each names its query too, so filters don't share rows. */
internal object ListKeys {
    const val CONTINUE_WATCHING = "videos/continue"
    const val RECENTLY_ADDED = "videos/recent"
    const val CHANNELS = "channels"
    const val PLAYLISTS = "playlists"

    fun channelVideos(channelId: String, type: VideoType, filter: WatchFilter) =
        "channel/$channelId/${type.apiValue}/${filter.name.lowercase()}"

    fun channelPlaylists(channelId: String) = "channel/$channelId/playlists"

    fun playlistVideos(playlistId: String) = "playlist/$playlistId/videos"
}
