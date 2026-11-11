package com.malgra.reliquary.core.data.progress

import androidx.room.withTransaction
import com.malgra.reliquary.core.data.library.toModel
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Starts a background push of pending progress once the network allows. */
interface ProgressSyncScheduler {
    fun requestSync()
}

/**
 * Where the player saves its place. Every change lands in Room first, so it
 * survives being offline or killed, and is pushed to the server by
 * [ProgressSyncer] in the background.
 */
@Singleton
class ProgressRepository internal constructor(
    private val db: ReliquaryDatabase,
    private val scheduler: ProgressSyncScheduler,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, scheduler: ProgressSyncScheduler) :
        this(db, scheduler, System::currentTimeMillis)

    private val pending = db.pendingProgressDao()

    /** How many videos have changes the server hasn't got yet. */
    val pendingCount: Flow<Int> = pending.observeCount()

    /**
     * Saves the playback position from a session that started at [sessionStartedAtMillis].
     * A "mark unwatched" made here is kept. A "mark watched" is kept if it came
     * during this session (the player saves again after the tap); a session that
     * started after the mark is a rewatch, so the mark goes.
     */
    suspend fun recordPosition(videoId: String, position: Duration, sessionStartedAtMillis: Long) {
        db.withTransaction {
            val existing = pending.get(videoId)
            pending.upsert(
                PendingProgressEntity(
                    videoId = videoId,
                    positionSeconds = position.inWholeMilliseconds / 1000.0,
                    watched = existing?.watched?.takeIf { !it || sessionStartedAtMillis < existing.updatedAtMillis },
                    updatedAtMillis = clock(),
                    sessionStartedAtMillis = sessionStartedAtMillis,
                ),
            )
        }
    }

    /** Marks the video watched or unwatched here; either way its place goes back to the start. */
    suspend fun setWatched(videoId: String, watched: Boolean) {
        val now = clock()
        pending.upsert(PendingProgressEntity(videoId, 0.0, watched, now, now))
        scheduler.requestSync()
    }

    /** Where to start playing: the saved place, or the beginning if it's watched or nearly over. */
    suspend fun resumePosition(videoId: String): Duration {
        val video = db.videoDao().get(videoId)?.toModel() ?: return Duration.ZERO
        val nearlyOver = video.duration > Duration.ZERO && video.remaining < 5.seconds
        return if (video.watched || nearlyOver) Duration.ZERO else video.position
    }

    fun requestSync() = scheduler.requestSync()
}
