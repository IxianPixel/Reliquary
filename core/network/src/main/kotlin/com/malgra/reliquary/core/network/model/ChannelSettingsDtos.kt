package com.malgra.reliquary.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Per-channel overwrites (#26), from ChannelOverwriteSerializer in
 * backend/channel/serializers.py. A missing key means the global setting applies.
 */

/** Just the overwrites from `GET /api/channel/<id>/`. */
@Serializable
data class ChannelSettingsDto(
    @SerialName("channel_overwrites") val overwrites: ChannelOverwritesDto? = null,
)

@Serializable
data class ChannelOverwritesDto(
    @SerialName(DOWNLOAD_FORMAT) val downloadFormat: String? = null,
    @SerialName(AUTODELETE_DAYS) val autodeleteDays: Int? = null,
    @SerialName(INDEX_PLAYLISTS) val indexPlaylists: Boolean? = null,
    @SerialName(INTEGRATE_SPONSORBLOCK) val integrateSponsorblock: Boolean? = null,
    @SerialName(VIDEOS_SIZE) val videosSize: Int? = null,
    @SerialName(LIVE_SIZE) val liveSize: Int? = null,
    @SerialName(SHORTS_SIZE) val shortsSize: Int? = null,
) {
    companion object {
        const val DOWNLOAD_FORMAT = "download_format"
        const val AUTODELETE_DAYS = "autodelete_days"
        const val INDEX_PLAYLISTS = "index_playlists"
        const val INTEGRATE_SPONSORBLOCK = "integrate_sponsorblock"
        const val VIDEOS_SIZE = "subscriptions_channel_size"
        const val LIVE_SIZE = "subscriptions_live_channel_size"
        const val SHORTS_SIZE = "subscriptions_shorts_channel_size"
    }
}
