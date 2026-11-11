package com.malgra.reliquary.core.data.library

import androidx.paging.PagingData
import androidx.room.withTransaction
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.model.WatchFilter
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.model.Page
import com.malgra.reliquary.core.network.model.VideoDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    private val videos = db.videoDao()
    private val listings = db.listingDao()

    /** Newest in the archive first. */
    fun recentlyAdded(): Flow<PagingData<Video>> =
        pager(ListKeys.RECENTLY_ADDED) { page -> videos(page = page, sort = "downloaded", order = "desc") }

    /** Newest published first. */
    fun channelVideos(channelId: String, type: VideoType, filter: WatchFilter): Flow<PagingData<Video>> =
        pager(ListKeys.channelVideos(channelId, type, filter)) { page ->
            videos(
                page = page,
                channel = channelId,
                type = type.apiValue,
                watch = if (filter == WatchFilter.Unwatched) "unwatched" else null,
                sort = "published",
                order = "desc",
            )
        }

    /** In playlist order: the server sorts by the playlist's own index. */
    fun playlistVideos(playlistId: String): Flow<PagingData<Video>> =
        pager(ListKeys.playlistVideos(playlistId)) { page -> videos(page = page, playlist = playlistId) }

    /** Videos with a saved position, as last fetched. */
    fun continueWatching(): Flow<List<Video>> =
        videos.observeListing(ListKeys.CONTINUE_WATCHING).map { rows -> rows.map { it.toModel() } }

    /** Re-fetches [continueWatching]; returns the error, or null on success. Only the first page is kept. */
    suspend fun refreshContinueWatching(): LoadError? = load {
        val server = library.server()
        val page = server.api.videos(watch = "continue")
        val now = clock()
        db.withTransaction {
            videos.upsert(page.data.map { it.toEntity(server.baseUrl, now) })
            listings.clearItems(ListKeys.CONTINUE_WATCHING)
            listings.append(ListKeys.CONTINUE_WATCHING, page.data.map { it.youtubeId })
            listings.upsertState(ListingStateEntity(ListKeys.CONTINUE_WATCHING, null, page.data.size, now))
        }
    }.errorOrNull

    /** The server's total for a channel list, once its first page has loaded. */
    fun channelVideoCount(channelId: String, type: VideoType, filter: WatchFilter): Flow<Int?> =
        listings.observeState(ListKeys.channelVideos(channelId, type, filter)).map { it?.totalCount }

    fun video(id: String): Flow<Video?> = videos.observe(id).map { it?.toModel() }

    /** Fetches one video's details and server watch state; returns the error, or null on success. */
    suspend fun refreshVideo(id: String): LoadError? = load {
        val server = library.server()
        videos.upsert(listOf(server.api.video(id).toEntity(server.baseUrl, clock())))
    }.errorOrNull

    private fun pager(
        listKey: String,
        fetch: suspend TubeArchivistApi.(page: Int) -> Page<VideoDto>,
    ): Flow<PagingData<Video>> = db.listingPager(
        listKey = listKey,
        library = library,
        clock = clock,
        fetch = fetch,
        store = { items, baseUrl, now ->
            videos.upsert(items.map { it.toEntity(baseUrl, now) })
            items.map { it.youtubeId }
        },
        source = { videos.pagingSource(listKey) },
        toModel = { it.toModel() },
    )
}
