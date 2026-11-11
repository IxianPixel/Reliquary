package com.malgra.reliquary.core.data.library

import androidx.room.withTransaction
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.SearchResults
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    /**
     * Searches the server, which also understands TA's `channel:`, `playlist:` and
     * `full:` prefixes. When the server can't be reached it falls back to a title
     * match over what is cached on this device.
     */
    suspend fun search(query: String): LoadResult<SearchResults> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return LoadResult.Ok(SearchResults.Empty)
        return when (val remote = load { searchServer(trimmed) }) {
            is LoadResult.Ok -> remote
            is LoadResult.Failed ->
                if (remote.error == LoadError.Offline) load { searchCache(trimmed) } else remote
        }
    }

    private suspend fun searchServer(query: String): SearchResults {
        val server = library.server()
        val results = server.api.search(query).results
        val now = clock()
        // Hits are full objects, so they refresh the cache (and offline search) too.
        val videos = results.videos.map { it.toEntity(server.baseUrl, now) }
        val playlists = results.playlists.map { it.toEntity(server.baseUrl, now) }
        val channels = db.withTransaction {
            val previous = db.channelDao().getAll(results.channels.map { it.channelId }).associateBy { it.id }
            val channels = results.channels.map { it.toEntity(server.baseUrl, now, previous[it.channelId]) }
            db.videoDao().upsert(videos)
            db.channelDao().upsert(channels)
            db.playlistDao().upsert(playlists)
            channels
        }
        return SearchResults(
            videos = videos.map { it.toModel() },
            channels = channels.map { it.toModel() },
            playlists = playlists.map { it.toModel() },
            fromCache = false,
        )
    }

    private suspend fun searchCache(query: String) = SearchResults(
        videos = db.videoDao().search(query).map { it.toModel() },
        channels = db.channelDao().search(query).map { it.toModel() },
        playlists = db.playlistDao().search(query).map { it.toModel() },
        fromCache = true,
    )
}
