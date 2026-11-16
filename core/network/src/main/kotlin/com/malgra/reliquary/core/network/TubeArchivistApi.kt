package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.auth.NoAuth
import com.malgra.reliquary.core.network.auth.RequestAuth
import com.malgra.reliquary.core.network.model.AccountDto
import com.malgra.reliquary.core.network.model.ChannelDto
import com.malgra.reliquary.core.network.model.ChannelNavDto
import com.malgra.reliquary.core.network.model.ChannelSettingsDto
import com.malgra.reliquary.core.network.model.ChannelSubscribeRequest
import com.malgra.reliquary.core.network.model.ChannelSubscribedUpdate
import com.malgra.reliquary.core.network.model.CommentDto
import com.malgra.reliquary.core.network.model.LoginRequest
import com.malgra.reliquary.core.network.model.NotificationDto
import com.malgra.reliquary.core.network.model.Page
import com.malgra.reliquary.core.network.model.PingDto
import com.malgra.reliquary.core.network.model.PlaylistDto
import com.malgra.reliquary.core.network.model.PlaylistSubscribeRequest
import com.malgra.reliquary.core.network.model.PlaylistUpdateRequest
import com.malgra.reliquary.core.network.model.ProgressRequest
import com.malgra.reliquary.core.network.model.QueueAddRequest
import com.malgra.reliquary.core.network.model.QueueItemDto
import com.malgra.reliquary.core.network.model.QueueStatusRequest
import com.malgra.reliquary.core.network.model.SearchDto
import com.malgra.reliquary.core.network.model.TaskStartedDto
import com.malgra.reliquary.core.network.model.VideoDto
import com.malgra.reliquary.core.network.model.WatchedRequest
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Tag

/**
 * Tube Archivist REST API (v0.6.0). Paths are relative so a server hosted under a
 * sub-path works. A [RequestAuth] tag overrides the stored session for one call,
 * which sign-in uses before anything is saved.
 *
 * List endpoints answer 404 for a page past the end.
 */
interface TubeArchivistApi {

    @GET("api/health/")
    suspend fun health(@Tag noAuth: NoAuth = NoAuth): Response<String>

    @GET("api/ping/")
    suspend fun ping(@Tag auth: RequestAuth? = null): PingDto

    @GET("api/user/account/")
    suspend fun account(@Tag auth: RequestAuth? = null): AccountDto

    @POST("api/user/login/")
    suspend fun login(@Body body: LoginRequest, @Tag noAuth: NoAuth = NoAuth): Response<Unit>

    @POST("api/user/logout/")
    suspend fun logout(@Tag auth: RequestAuth? = null): Response<Unit>

    @GET("api/video/")
    suspend fun videos(
        @Query("page") page: Int? = null,
        @Query("channel") channel: String? = null,
        @Query("playlist") playlist: String? = null,
        @Query("watch") watch: String? = null,
        @Query("sort") sort: String? = null,
        @Query("order") order: String? = null,
        @Query("type") type: String? = null,
    ): Page<VideoDto>

    @GET("api/video/{id}/")
    suspend fun video(@Path("id") id: String): VideoDto

    /** The video's saved comments with their replies; 404 when the server saved none. Not paginated. */
    @GET("api/video/{id}/comment/")
    suspend fun comments(@Path("id") id: String): Response<List<CommentDto>>

    /** Up to 6 videos like this one, by title and tags; not paginated. */
    @GET("api/video/{id}/similar/")
    suspend fun similar(@Path("id") id: String): List<VideoDto>

    @POST("api/video/{id}/progress/")
    suspend fun setProgress(@Path("id") id: String, @Body body: ProgressRequest): Response<Unit>

    @DELETE("api/video/{id}/progress/")
    suspend fun clearProgress(@Path("id") id: String): Response<Unit>

    @POST("api/watched/")
    suspend fun setWatched(@Body body: WatchedRequest): Response<Unit>

    @GET("api/channel/")
    suspend fun channels(
        @Query("page") page: Int? = null,
        @Query("filter") filter: String? = null,
    ): Page<ChannelDto>

    @GET("api/channel/{id}/")
    suspend fun channel(@Path("id") id: String): ChannelDto

    @GET("api/playlist/")
    suspend fun playlists(
        @Query("page") page: Int? = null,
        @Query("channel") channel: String? = null,
        @Query("subscribed") subscribed: Boolean? = null,
        @Query("type") type: String? = null,
    ): Page<PlaylistDto>

    @GET("api/playlist/{id}/")
    suspend fun playlist(@Path("id") id: String): PlaylistDto

    @GET("api/channel/{id}/nav/")
    suspend fun channelNav(@Path("id") id: String): ChannelNavDto

    /** Up to 30 hits per kind; not paginated. */
    @GET("api/search/")
    suspend fun search(@Query("query") query: String): SearchDto

    // #25 Playlist subscriptions (admin only).

    /** Starts a task that indexes and subscribes; the answer is only the task's ID. */
    @POST("api/playlist/")
    suspend fun subscribePlaylists(@Body body: PlaylistSubscribeRequest): Response<Unit>

    /** For a playlist the server already has; answers with the updated playlist. */
    @POST("api/playlist/{id}/")
    suspend fun updatePlaylist(@Path("id") id: String, @Body body: PlaylistUpdateRequest): PlaylistDto

    // #24 Add channels: subscriptions. The writes are wrapped in ChannelCalls.kt.

    @POST("api/channel/")
    suspend fun subscribeChannels(@Body body: ChannelSubscribeRequest): Response<Unit>

    @POST("api/channel/{id}/")
    suspend fun updateChannelSubscribed(@Path("id") id: String, @Body body: ChannelSubscribedUpdate): Response<Unit>

    // #26 Channel settings: per-channel overwrites. Admin only to change. Wrapped in ChannelSettingsCalls.kt.

    @GET("api/channel/{id}/")
    suspend fun channelSettings(@Path("id") id: String): ChannelSettingsDto

    /** Body is `{"channel_overwrites": {...}}`, built by hand so nulls (clear) survive. */
    @POST("api/channel/{id}/")
    suspend fun updateChannelOverwrites(@Path("id") id: String, @Body body: RequestBody): Response<Unit>

    /** A channel the server has, found by URL, handle or ID; 404 until TA has added it. */
    @GET("api/channel/search/")
    suspend fun findChannel(@Query("q") query: String): ChannelDto

    // #23 Queue: TA's download queue and the tasks that fill and empty it. Admin only.

    @GET("api/download/")
    suspend fun queue(
        @Query("filter") filter: String,
        @Query("page") page: Int? = null,
    ): Page<QueueItemDto>

    /** Takes video, channel or playlist URLs and IDs; TA resolves them in a background task. */
    @POST("api/download/")
    suspend fun addToQueue(@Body body: QueueAddRequest, @Query("autostart") autostart: Boolean? = null): TaskStartedDto

    @POST("api/download/{id}/")
    suspend fun setQueueStatus(@Path("id") id: String, @Body body: QueueStatusRequest): Response<Unit>

    @DELETE("api/download/{id}/")
    suspend fun removeFromQueue(@Path("id") id: String): Response<Unit>

    /** Removes every item with this status ("pending" or "ignore"). */
    @DELETE("api/download/")
    suspend fun clearQueue(@Query("filter") filter: String): Response<Unit>

    /** "update_subscribed" rescans subscriptions; "download_pending" downloads the queue. */
    @POST("api/task/by-name/{name}/")
    suspend fun startTask(@Path("name") name: String): TaskStartedDto

    /** What the server's background tasks are doing now; "download" covers rescans and downloads. */
    @GET("api/notification/")
    suspend fun notifications(@Query("filter") filter: String): List<NotificationDto>
}
