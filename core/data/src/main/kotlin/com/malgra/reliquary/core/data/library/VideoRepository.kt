package com.malgra.reliquary.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.database.entity.VideoEntity
import com.malgra.reliquary.core.model.Comment
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.model.WatchFilter
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.TubeArchivistApi
import com.malgra.reliquary.core.network.commentsOrEmpty
import com.malgra.reliquary.core.network.model.Page
import com.malgra.reliquary.core.network.model.VideoDto
import com.malgra.reliquary.core.network.toFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

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

    /** Newest published first. On-device videos come from this device only, so they work offline. */
    fun channelVideos(channelId: String, type: VideoType, filter: WatchFilter): Flow<PagingData<Video>> =
        if (filter == WatchFilter.OnDevice) {
            Pager(PagingConfig(pageSize = 20, enablePlaceholders = false)) {
                videos.downloadedPagingSource(channelId, type.apiValue)
            }.flow.map { data -> data.map { it.toModel() } }
        } else {
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
        }

    /** In playlist order: the server sorts by the playlist's own index. */
    fun playlistVideos(playlistId: String): Flow<PagingData<Video>> =
        pager(ListKeys.playlistVideos(playlistId)) { page -> videos(page = page, playlist = playlistId) }

    /** Downloaded videos, most recently downloaded first. */
    fun downloaded(): Flow<List<Video>> = videos.observeDownloaded().map { rows -> rows.map { it.toModel() } }

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

    /** The cached video, if any. */
    suspend fun get(id: String): Video? = videos.get(id)?.toModel()

    /** A playlist's videos as cached, in playlist order. */
    fun playlistListing(playlistId: String): Flow<List<Video>> =
        videos.observeListing(ListKeys.playlistVideos(playlistId)).map { rows -> rows.map { it.toModel() } }

    /**
     * Every video in the playlist, in order, for playing it as a queue. Fetches
     * the pages not cached yet; offline it returns what's cached.
     */
    suspend fun playlistQueue(playlistId: String): List<Video> {
        val listKey = ListKeys.playlistVideos(playlistId)
        load {
            val server = library.server()
            val now = clock()
            val all = mutableListOf<VideoEntity>()
            var page: Int? = 1
            var total: Int? = null
            while (page != null && page <= MAX_QUEUE_PAGES) {
                val response = try {
                    server.api.videos(page = page, playlist = playlistId)
                } catch (e: Exception) {
                    val failure = e.toFailure()
                    if (failure is Failure.Http && failure.code == 404) break else throw e // 404 past the end
                }
                all += response.data.map { it.toEntity(server.baseUrl, now) }
                total = response.paginate?.totalHits ?: total
                page = nextPage(response.paginate)
            }
            db.withTransaction {
                videos.upsert(all)
                listings.clearItems(listKey)
                listings.append(listKey, all.map { it.id })
                listings.upsertState(ListingStateEntity(listKey, page, total, now))
            }
        }
        return videos.observeListing(listKey).first().map { it.toModel() }
    }

    /** Fetches one video's details and server watch state; returns the error, or null on success. */
    suspend fun refreshVideo(id: String): LoadError? = load {
        val server = library.server()
        videos.upsert(listOf(server.api.video(id).toEntity(server.baseUrl, clock())))
    }.errorOrNull

    /** [id]'s saved comments from the server, as threads; empty when it saved none. */
    suspend fun comments(id: String): LoadResult<List<Comment>> = load {
        library.server().api.commentsOrEmpty(id).toComments()
    }

    /** Up to 6 videos like [id], from the server. They're cached, so they show progress and downloads. */
    suspend fun similar(id: String): LoadResult<List<Video>> = load {
        val server = library.server()
        val found = server.api.similar(id).filter { it.youtubeId != id }
        videos.upsert(found.map { it.toEntity(server.baseUrl, clock()) })
        found.mapNotNull { videos.get(it.youtubeId)?.toModel() }
    }

    private companion object {
        /** Up to a few hundred videos, at TA's usual page sizes. */
        const val MAX_QUEUE_PAGES = 25
    }

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
