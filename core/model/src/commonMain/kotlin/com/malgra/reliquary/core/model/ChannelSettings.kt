package com.malgra.reliquary.core.model

/**
 * A channel's own settings on the server, over TA's global ones. Null means
 * "use the server's setting". A page size of 0 skips that kind of video when
 * TA checks the channel's subscription.
 */
data class ChannelSettings(
    val videosPageSize: Int? = null,
    val shortsPageSize: Int? = null,
    val streamsPageSize: Int? = null,
    /** A yt-dlp format string. */
    val downloadFormat: String? = null,
    /** Delete watched videos this many days after watching. */
    val autoDeleteDays: Int? = null,
    val indexPlaylists: Boolean = false,
    val sponsorBlock: Boolean? = null,
)
