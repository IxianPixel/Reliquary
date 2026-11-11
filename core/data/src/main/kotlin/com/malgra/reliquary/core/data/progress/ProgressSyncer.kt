package com.malgra.reliquary.core.data.progress

import android.util.Log
import androidx.room.withTransaction
import com.malgra.reliquary.core.data.library.LibraryApi
import com.malgra.reliquary.core.data.library.NotSignedInException
import com.malgra.reliquary.core.data.library.toEntity
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.deletePosition
import com.malgra.reliquary.core.network.markWatched
import com.malgra.reliquary.core.network.savePosition
import com.malgra.reliquary.core.network.toFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Pushes pending progress to the server. Run by the sync worker. */
@Singleton
class ProgressSyncer internal constructor(
    private val db: ReliquaryDatabase,
    private val library: LibraryApi,
    private val clock: () -> Long,
) {
    @Inject constructor(db: ReliquaryDatabase, library: LibraryApi) : this(db, library, System::currentTimeMillis)

    enum class Outcome {
        /** Nothing left to push, or nothing can be pushed until the user signs in. */
        Done,

        /** Something failed in a way worth retrying later, e.g. no connection. */
        Retry,
    }

    private val pending = db.pendingProgressDao()

    suspend fun syncAll(): Outcome {
        // A few passes pick up positions the player saved while we were syncing.
        repeat(MAX_PASSES) {
            val rows = pending.all()
            if (rows.isEmpty()) return Outcome.Done
            for (row in rows) {
                when (syncOne(row)) {
                    Step.Synced -> Unit
                    Step.Retry -> return Outcome.Retry
                    Step.NotSignedIn -> return Outcome.Done
                }
            }
        }
        return if (pending.all().isEmpty()) Outcome.Done else Outcome.Retry
    }

    private enum class Step { Synced, Retry, NotSignedIn }

    private suspend fun syncOne(row: PendingProgressEntity): Step = try {
        val server = library.server()
        val before = server.api.video(row.videoId)
        val actions = resolveProgress(row, before.player)
        for (action in actions) {
            when (action) {
                is ProgressAction.MarkWatched -> server.api.markWatched(row.videoId, action.watched)
                is ProgressAction.SetPosition -> server.api.savePosition(row.videoId, action.seconds)
                ProgressAction.ClearPosition -> server.api.deletePosition(row.videoId)
            }
        }
        // Store the server's answer, so lists show what it now holds.
        val after = if (actions.isEmpty()) before else server.api.video(row.videoId)
        db.withTransaction {
            db.videoDao().upsert(listOf(after.toEntity(server.baseUrl, clock())))
            pending.deleteIfUnchanged(row.videoId, row.updatedAtMillis)
        }
        Step.Synced
    } catch (e: CancellationException) {
        throw e
    } catch (_: NotSignedInException) {
        Step.NotSignedIn
    } catch (e: Exception) {
        val failure = e.toFailure()
        if (failure is Failure.Http && failure.code == 404) {
            // The video is gone from the archive; there is nothing to sync it to.
            pending.deleteIfUnchanged(row.videoId, row.updatedAtMillis)
            Step.Synced
        } else {
            Log.w(TAG, "Progress sync for ${row.videoId} failed", e)
            Step.Retry
        }
    }

    private companion object {
        const val TAG = "ProgressSyncer"
        const val MAX_PASSES = 3
    }
}
