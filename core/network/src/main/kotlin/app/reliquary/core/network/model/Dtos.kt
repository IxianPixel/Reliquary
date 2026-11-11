package app.reliquary.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * Wire types for Tube Archivist v0.6.0, from the DRF serializers in
 * backend/{common,video,channel,playlist,user}/serializers.py.
 * Fields the server marks optional, or that older data may lack, have defaults.
 */

@Serializable
data class PingDto(
    val response: String,
    val user: Int,
    val version: String,
    @SerialName("ta_update") val taUpdate: PingUpdateDto? = null,
)

@Serializable
data class PingUpdateDto(
    val status: Boolean,
    val version: String,
    @SerialName("is_breaking") val isBreaking: Boolean,
)

@Serializable
data class AccountDto(
    val id: Int,
    val name: String,
    @SerialName("is_superuser") val isSuperuser: Boolean = false,
    @SerialName("is_staff") val isStaff: Boolean = false,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    // No default: kotlinx.serialization leaves defaults out, and the server's
    // default "off" would end the session after 2 days instead of 365.
    @SerialName("remember_me") val rememberMe: String,
)

@Serializable
data class ErrorDto(val error: String? = null, val detail: String? = null)

@Serializable
data class PaginateDto(
    @SerialName("page_size") val pageSize: Int = 0,
    @SerialName("page_from") val pageFrom: Int = 0,
    @SerialName("prev_pages") val prevPages: List<Int>? = null,
    @SerialName("current_page") val currentPage: Int = 0,
    @SerialName("max_hits") val maxHits: Boolean = false,
    val params: String = "",
    @SerialName("last_page") val lastPage: Int = 0,
    @SerialName("next_pages") val nextPages: List<Int>? = null,
    @SerialName("total_hits") val totalHits: Int = 0,
)

@Serializable
data class PlayerDto(
    val watched: Boolean = false,
    @SerialName("watched_date") val watchedDate: Long? = null,
    val duration: Int = 0,
    @SerialName("duration_str") val durationStr: String = "",
    val progress: Double? = null,
    val position: Double? = null,
)

@Serializable
data class ChannelDto(
    @SerialName("channel_id") val channelId: String,
    @SerialName("channel_name") val channelName: String,
    @SerialName("channel_active") val channelActive: Boolean = true,
    @SerialName("channel_banner_url") val channelBannerUrl: String? = null,
    @SerialName("channel_thumb_url") val channelThumbUrl: String? = null,
    @SerialName("channel_tvart_url") val channelTvartUrl: String? = null,
    @SerialName("channel_description") val channelDescription: String? = null,
    @SerialName("channel_last_refresh") val channelLastRefresh: String? = null,
    @SerialName("channel_subs") val channelSubs: Long = 0,
    @SerialName("channel_subscribed") val channelSubscribed: Boolean = false,
    @SerialName("channel_tags") val channelTags: List<String> = emptyList(),
    @SerialName("channel_tabs") val channelTabs: List<String> = emptyList(),
)

@Serializable
data class StatsDto(
    @SerialName("view_count") val viewCount: Long = 0,
    @SerialName("like_count") val likeCount: Long = 0,
    @SerialName("dislike_count") val dislikeCount: Long = 0,
    @SerialName("average_rating") val averageRating: Double? = null,
)

@Serializable
data class StreamDto(
    val type: String,
    val codec: String,
    val bitrate: Long = 0,
    val index: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class SubtitleDto(
    val lang: String,
    val name: String,
    val ext: String,
    val source: String,
    @SerialName("media_url") val mediaUrl: String,
    val url: String? = null,
)

@Serializable
data class SponsorSegmentDto(
    val segment: List<Double>,
    val category: String,
    val actionType: String,
    @SerialName("UUID") val uuid: String,
    val votes: Int = 0,
    val locked: Int = 0,
    val videoDuration: Double = 0.0,
)

@Serializable
data class SponsorBlockDto(
    @SerialName("is_enabled") val isEnabled: Boolean = false,
    @SerialName("last_refresh") val lastRefresh: Long? = null,
    @SerialName("has_unlocked") val hasUnlocked: Boolean? = null,
    val segments: List<SponsorSegmentDto> = emptyList(),
)

@Serializable
data class VideoDto(
    @SerialName("youtube_id") val youtubeId: String,
    val title: String,
    val active: Boolean = true,
    val channel: ChannelDto? = null,
    val description: String? = null,
    val published: String = "",
    @SerialName("date_downloaded") val dateDownloaded: Long = 0,
    @SerialName("vid_last_refresh") val vidLastRefresh: String? = null,
    @SerialName("vid_thumb_url") val vidThumbUrl: String = "",
    @SerialName("vid_type") val vidType: String = "videos",
    @SerialName("media_url") val mediaUrl: String,
    @SerialName("media_size") val mediaSize: Long = 0,
    val player: PlayerDto = PlayerDto(),
    val playlist: List<String> = emptyList(),
    val category: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val stats: StatsDto = StatsDto(),
    val streams: List<StreamDto> = emptyList(),
    val subtitles: List<SubtitleDto> = emptyList(),
    val sponsorblock: SponsorBlockDto? = null,
    @SerialName("comment_count") val commentCount: Int? = null,
)

@Serializable
data class PlaylistEntryDto(
    @SerialName("youtube_id") val youtubeId: String,
    val title: String,
    val uploader: String? = null,
    val idx: Int,
    val downloaded: Boolean,
)

@Serializable
data class PlaylistDto(
    @SerialName("playlist_id") val playlistId: String,
    @SerialName("playlist_name") val playlistName: String,
    @SerialName("playlist_channel") val playlistChannel: String = "",
    @SerialName("playlist_channel_id") val playlistChannelId: String = "",
    @SerialName("playlist_description") val playlistDescription: String? = null,
    @SerialName("playlist_entries") val playlistEntries: List<PlaylistEntryDto> = emptyList(),
    @SerialName("playlist_thumbnail") val playlistThumbnail: String = "",
    @SerialName("playlist_type") val playlistType: String = "regular",
    @SerialName("playlist_subscribed") val playlistSubscribed: Boolean = false,
    @SerialName("playlist_active") val playlistActive: Boolean = true,
    @SerialName("playlist_sort_order") val playlistSortOrder: String = "top",
    @SerialName("playlist_last_refresh") val playlistLastRefresh: String? = null,
)

@Serializable
data class ProgressRequest(val position: Double)

@Serializable
data class WatchedRequest(val id: String, @SerialName("is_watched") val isWatched: Boolean)
