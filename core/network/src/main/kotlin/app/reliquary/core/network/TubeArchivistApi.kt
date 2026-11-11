package app.reliquary.core.network

import app.reliquary.core.network.auth.NoAuth
import app.reliquary.core.network.auth.RequestAuth
import app.reliquary.core.network.model.AccountDto
import app.reliquary.core.network.model.ChannelDto
import app.reliquary.core.network.model.LoginRequest
import app.reliquary.core.network.model.Page
import app.reliquary.core.network.model.PingDto
import app.reliquary.core.network.model.PlayerDto
import app.reliquary.core.network.model.PlaylistDto
import app.reliquary.core.network.model.ProgressRequest
import app.reliquary.core.network.model.VideoDto
import app.reliquary.core.network.model.WatchedRequest
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

    @POST("api/video/{id}/progress/")
    suspend fun setProgress(@Path("id") id: String, @Body body: ProgressRequest): PlayerDto

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
}
