package com.malgra.reliquary.core.data.progress

import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.network.model.PlayerDto

/** Tube Archivist ignores positions of 5 seconds or less. */
internal const val MIN_SERVER_POSITION_SECONDS = 5.0

sealed interface ProgressAction {
    data class MarkWatched(val watched: Boolean) : ProgressAction
    data class SetPosition(val seconds: Double) : ProgressAction
    data object ClearPosition : ProgressAction
}

/**
 * What to send so the server matches a change made on this device.
 *
 * The local change wins, with one exception: if the server marked the video
 * watched after the local session started (say, it was finished on the TV
 * while this phone was offline), the server's state stands and nothing is
 * sent. An explicit "mark unwatched" here is never overruled that way only
 * when it came later than the server's mark.
 *
 * Positions at or under 5 seconds mean "start over", so they clear the server's
 * position instead (if it has one).
 */
fun resolveProgress(local: PendingProgressEntity, server: PlayerDto): List<ProgressAction> {
    val watchedAt = server.watchedDate
    val serverWatchedAfterSession = server.watched && watchedAt != null && watchedAt * 1000 > local.sessionStartedAtMillis
    if (serverWatchedAfterSession) return emptyList()

    val localWatched = local.watched
    return buildList {
        if (localWatched != null && localWatched != server.watched) add(ProgressAction.MarkWatched(localWatched))
        if (localWatched == true) return@buildList // a finished video needs no position
        val serverPosition = server.position ?: 0.0
        when {
            local.positionSeconds > MIN_SERVER_POSITION_SECONDS -> add(ProgressAction.SetPosition(local.positionSeconds))
            serverPosition > MIN_SERVER_POSITION_SECONDS -> add(ProgressAction.ClearPosition)
        }
    }
}
