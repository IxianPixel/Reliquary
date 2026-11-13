package com.malgra.reliquary.core.data.library

import androidx.paging.PagingData
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.LoadError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChannelRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    private val channels = db.channelDao()

    fun channels(): Flow<PagingData<Channel>> = db.listingPager(
        listKey = ListKeys.CHANNELS,
        library = library,
        clock = clock,
        fetch = { page -> channels(page = page) },
        store = { items, baseUrl, now ->
            val previous = channels.getAll(items.map { it.channelId }).associateBy { it.id }
            channels.upsert(items.map { it.toEntity(baseUrl, now, previous[it.channelId]) })
            items.map { it.channelId }
        },
        source = { channels.pagingSource(ListKeys.CHANNELS) },
        toModel = { it.toModel() },
    )

    fun channel(id: String): Flow<Channel?> = channels.observe(id).map { it?.toModel() }

    /** Fetches the channel and which tabs it has; returns the error, or null on success. */
    suspend fun refreshChannel(id: String): LoadError? = load {
        val server = library.server()
        val (dto, nav) = coroutineScope {
            val dto = async { server.api.channel(id) }
            val nav = async { server.api.channelNav(id) }
            dto.await() to nav.await()
        }
        channels.upsert(
            listOf(
                dto.toEntity(server.baseUrl, clock(), previous = null).copy(
                    hasVideos = nav.hasVideos,
                    hasStreams = nav.hasStreams,
                    hasShorts = nav.hasShorts,
                    hasPlaylists = nav.hasPlaylists,
                ),
            ),
        )
    }.errorOrNull
}
