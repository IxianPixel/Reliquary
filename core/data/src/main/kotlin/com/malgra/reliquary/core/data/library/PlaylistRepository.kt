package com.malgra.reliquary.core.data.library

import androidx.paging.PagingData
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.network.model.PlaylistUpdateRequest
import com.malgra.reliquary.core.network.subscribeToPlaylist
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

@Singleton
class PlaylistRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    private val playlists = db.playlistDao()
    private val listings = db.listingDao()

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

    /**
     * Asks the server to add and subscribe to the playlist in [input], a
     * YouTube link or ID. The server does that in a background task, so
     * [awaitSubscribed] tells when it has.
     */
    suspend fun subscribe(input: String): SubscribeResult {
        val id = playlistIdFrom(input) ?: return SubscribeResult.NotAPlaylist
        return when (val result = load { library.server().api.subscribeToPlaylist(id) }) {
            is LoadResult.Ok -> {
                // In case nobody waits for it: the next visit to the list fetches it.
                markStale(ListKeys.PLAYLISTS)
                SubscribeResult.Started(id)
            }
            is LoadResult.Failed -> SubscribeResult.Failed(result.error)
        }
    }

    /**
     * Polls until the server has the playlist [id] and is subscribed to it,
     * then caches it and marks the playlist lists stale. False if that takes
     * longer than a short wait, which a big playlist or a busy server can.
     */
    suspend fun awaitSubscribed(id: String): Boolean {
        repeat(SUBSCRIBE_POLLS) { attempt ->
            if (attempt > 0) delay(SUBSCRIBE_POLL_INTERVAL)
            val dto = try {
                library.server().api.playlist(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 404 until the task has indexed it; a dropped connection may pass too.
                null
            }
            if (dto != null && dto.playlistSubscribed) {
                val server = library.server()
                val entity = dto.toEntity(server.baseUrl, clock())
                playlists.upsert(listOf(entity))
                markStale(ListKeys.PLAYLISTS)
                entity.channelId?.let { markStale(ListKeys.channelPlaylists(it)) }
                return true
            }
        }
        return false
    }

    /** Subscribes to or unsubscribes from a playlist the server already has. Returns the error, or null on success. */
    suspend fun setSubscribed(id: String, subscribed: Boolean): LoadError? = load {
        val server = library.server()
        val dto = server.api.updatePlaylist(id, PlaylistUpdateRequest(playlistSubscribed = subscribed))
        playlists.upsert(listOf(dto.toEntity(server.baseUrl, clock())))
    }.errorOrNull

    /** So the list refreshes from the server next time it is shown, picking up the new playlist in its place. */
    private suspend fun markStale(listKey: String) {
        listings.state(listKey)?.let { listings.upsertState(it.copy(refreshedAtMillis = 0)) }
    }

    sealed interface SubscribeResult {
        /** The server is adding [playlistId]. */
        data class Started(val playlistId: String) : SubscribeResult

        /** [input] wasn't a playlist link or ID; nothing was sent. */
        data object NotAPlaylist : SubscribeResult

        data class Failed(val error: LoadError) : SubscribeResult
    }

    private companion object {
        const val SUBSCRIBE_POLLS = 15
        val SUBSCRIBE_POLL_INTERVAL = 2.seconds
    }
}

/**
 * The playlist ID in a YouTube link (its `list` parameter) or a bare ID, using
 * the lengths Tube Archivist's own parser accepts. Checked here because the
 * server parses in its background task, where a bad link fails unseen.
 */
internal fun playlistIdFrom(input: String): String? {
    val text = input.trim()
    if (text.isEmpty()) return null
    val looksLikeUrl = "://" in text || text.startsWith("www.") || text.startsWith("youtube.com") || text.startsWith("m.youtube.com")
    if (!looksLikeUrl) return text.takeIf(::isPlaylistId)
    val url = (if ("://" in text) text else "https://$text").toHttpUrlOrNull() ?: return null
    if (url.host != "youtube.com" && !url.host.endsWith(".youtube.com")) return null
    return url.queryParameter("list")?.takeIf(::isPlaylistId)
}

private val PLAYLIST_ID = Regex("[A-Za-z0-9_-]+")

private fun isPlaylistId(id: String): Boolean =
    id == "LL" || id == "WL" || (id.length in setOf(13, 18, 26, 34) && PLAYLIST_ID.matches(id))
