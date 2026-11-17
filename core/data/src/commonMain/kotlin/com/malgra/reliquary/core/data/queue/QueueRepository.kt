package com.malgra.reliquary.core.data.queue

import com.malgra.reliquary.core.data.library.LibraryApi
import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.data.library.absolute
import com.malgra.reliquary.core.data.library.load
import com.malgra.reliquary.core.data.library.nextPage
import com.malgra.reliquary.core.data.library.parseServerDate
import com.malgra.reliquary.core.model.QueueItem
import com.malgra.reliquary.core.model.ServerTask
import com.malgra.reliquary.core.model.VideoType
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.clearQueueByStatus
import com.malgra.reliquary.core.network.dropFromQueue
import com.malgra.reliquary.core.network.model.NotificationDto
import com.malgra.reliquary.core.network.model.QueueItemDto
import com.malgra.reliquary.core.network.queueUp
import com.malgra.reliquary.core.network.setQueueItemStatus
import com.malgra.reliquary.core.network.toFailure
import okhttp3.HttpUrl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** One page of the queue, and the page after it (null on the last). */
data class QueuePage(val items: List<QueueItem>, val next: Int?, val total: Int?)

/**
 * TA's download queue and the tasks that work on it (#23). Admin only and online
 * only: the queue is the server's to-do list, not something to read offline, so
 * it isn't cached in Room.
 */
@Singleton
class QueueRepository @Inject constructor(private val library: LibraryApi) {

    suspend fun page(ignored: Boolean, page: Int = 1): LoadResult<QueuePage> = load {
        val server = library.server()
        val response = try {
            server.api.queue(filter = if (ignored) IGNORE else PENDING, page = page)
        } catch (e: Exception) {
            // A page past the end, or an empty queue, is a 404.
            val failure = e.toFailure()
            if (failure is Failure.Http && failure.code == 404) return@load QueuePage(emptyList(), null, 0) else throw e
        }
        QueuePage(
            items = response.data.map { it.toModel(server.baseUrl) },
            next = nextPage(response.paginate),
            total = response.paginate?.totalHits,
        )
    }

    /** Video, channel or playlist URLs or IDs, one per entry. TA adds them in the background. */
    suspend fun add(entries: List<String>, downloadNow: Boolean): LoadResult<Unit> = load {
        library.server().api.queueUp(entries, autostart = downloadNow)
    }

    /** Moves an item to the front and starts downloading it. */
    suspend fun downloadNext(videoId: String): LoadResult<Unit> = setStatus(videoId, "priority")

    suspend fun ignore(videoId: String): LoadResult<Unit> = setStatus(videoId, IGNORE)

    suspend fun restore(videoId: String): LoadResult<Unit> = setStatus(videoId, PENDING)

    suspend fun remove(videoId: String): LoadResult<Unit> = load { library.server().api.dropFromQueue(videoId) }

    suspend fun clear(ignored: Boolean): LoadResult<Unit> = load {
        library.server().api.clearQueueByStatus(if (ignored) IGNORE else PENDING)
    }

    /** Looks for new videos from subscribed channels and playlists. */
    suspend fun rescanSubscriptions(): LoadResult<Unit> = startTask("update_subscribed")

    suspend fun downloadAll(): LoadResult<Unit> = startTask("download_pending")

    /** Rescans and downloads running on the server now; empty when it's idle. */
    suspend fun tasks(): LoadResult<List<ServerTask>> = load {
        library.server().api.notifications(filter = "download").map(NotificationDto::toModel)
    }

    /** TA answers with the task's ID, which nothing here needs: progress comes from [tasks]. */
    private suspend fun startTask(name: String): LoadResult<Unit> = load {
        library.server().api.startTask(name)
        Unit
    }

    private suspend fun setStatus(videoId: String, status: String) = load {
        library.server().api.setQueueItemStatus(videoId, status)
    }

    private companion object {
        const val PENDING = "pending"
        const val IGNORE = "ignore"
    }
}

internal fun QueueItemDto.toModel(baseUrl: HttpUrl) = QueueItem(
    videoId = youtubeId,
    title = title,
    channelId = channelId,
    channelName = channelName,
    duration = parseClock(duration),
    published = parseServerDate(published)?.let(Instant::fromEpochSeconds),
    thumbnailUrl = baseUrl.absolute(vidThumbUrl),
    type = VideoType.fromApi(vidType),
    ignored = status == "ignore",
    error = message?.takeIf { it.isNotBlank() },
)

/** The queue gives durations as TA formats them: "12:34", "1:02:03", or "NA". */
internal fun parseClock(text: String): Duration {
    val parts = text.split(':').map { it.toLongOrNull() ?: return Duration.ZERO }
    return parts.fold(0L) { total, part -> total * 60 + part }.seconds
}

internal fun NotificationDto.toModel() = ServerTask(
    title = title,
    messages = messages,
    progress = progress?.toFloat()?.coerceIn(0f, 1f),
    failed = level == "error",
)
