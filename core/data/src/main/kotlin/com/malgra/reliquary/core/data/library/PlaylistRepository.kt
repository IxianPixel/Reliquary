package com.malgra.reliquary.core.data.library

import androidx.paging.PagingData
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Playlist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    private val playlists = db.playlistDao()

    /** All playlists, or one channel's when [channelId] is set. */
    fun playlists(channelId: String? = null): Flow<PagingData<Playlist>> {
        val listKey = channelId?.let(ListKeys::channelPlaylists) ?: ListKeys.PLAYLISTS
        return db.listingPager(
            listKey = listKey,
            library = library,
            clock = clock,
            fetch = { page -> playlists(page = page, channel = channelId) },
            store = { items, baseUrl, now ->
                playlists.upsert(items.map { it.toEntity(baseUrl, now) })
                items.map { it.playlistId }
            },
            source = { playlists.pagingSource(listKey) },
            toModel = { it.toModel() },
        )
    }

    fun playlist(id: String): Flow<Playlist?> = playlists.observe(id).map { it?.toModel() }

    /** Returns the error, or null on success. */
    suspend fun refreshPlaylist(id: String): LoadError? = load {
        val server = library.server()
        playlists.upsert(listOf(server.api.playlist(id).toEntity(server.baseUrl, clock())))
    }.errorOrNull
}
