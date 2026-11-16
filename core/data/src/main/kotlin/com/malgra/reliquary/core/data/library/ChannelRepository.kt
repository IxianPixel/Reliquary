package com.malgra.reliquary.core.data.library

import androidx.paging.PagingData
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.network.setChannelSubscribed
import com.malgra.reliquary.core.network.subscribeToChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Singleton
class ChannelRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    private val channels = db.channelDao()
    private val listings = db.listingDao()

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

    /**
     * Asks TA to subscribe to [reference]. That only queues a task, so success
     * means queued: [awaitSubscribed] waits for the channel itself.
     */
    suspend fun subscribe(reference: ChannelReference): LoadError? = load {
        library.server().api.subscribeToChannel(reference.query)
        markListStale()
    }.errorOrNull

    /**
     * Polls until TA's subscribe task has added [reference] and marked it
     * subscribed, then caches it and returns its ID; null if that hasn't
     * happened in time. A channel already on the server shows up straight away
     * but unsubscribed until the task gets to it, hence checking the flag.
     */
    suspend fun awaitSubscribed(
        reference: ChannelReference,
        attempts: Int = 10,
        interval: Duration = 3.seconds,
    ): String? {
        repeat(attempts) { attempt ->
            if (attempt > 0) delay(interval)
            val found = load {
                val server = library.server()
                server.api.findChannel(reference.query).takeIf { it.channelSubscribed }?.let { dto ->
                    val previous = channels.getAll(listOf(dto.channelId)).singleOrNull()
                    channels.upsert(listOf(dto.toEntity(server.baseUrl, clock(), previous)))
                    markListStale()
                    dto.channelId
                }
            }
            if (found is LoadResult.Ok && found.value != null) return found.value
        }
        return null
    }

    /** Subscribes to or unsubscribes from a channel the server has, and updates the cached copy to match. */
    suspend fun setSubscribed(id: String, subscribed: Boolean): LoadError? = load {
        library.server().api.setChannelSubscribed(id, subscribed)
        channels.getAll(listOf(id)).singleOrNull()?.let { channels.upsert(listOf(it.copy(subscribed = subscribed))) }
    }.errorOrNull

    /** So the Channels list fetches again next time rather than showing what it had. */
    private suspend fun markListStale() {
        listings.state(ListKeys.CHANNELS)?.let { listings.upsertState(it.copy(refreshedAtMillis = 0)) }
    }
}
