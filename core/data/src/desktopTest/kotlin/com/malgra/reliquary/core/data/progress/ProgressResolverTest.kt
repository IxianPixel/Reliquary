package com.malgra.reliquary.core.data.progress

import com.malgra.reliquary.core.data.progress.ProgressAction.ClearPosition
import com.malgra.reliquary.core.data.progress.ProgressAction.MarkWatched
import com.malgra.reliquary.core.data.progress.ProgressAction.SetPosition
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.network.model.PlayerDto
import org.junit.Test
import kotlin.test.assertEquals

class ProgressResolverTest {
    /** The local session started at t = 1,000 s; server dates are epoch seconds. */
    private val sessionStart = 1_000_000L

    private fun local(position: Double = 0.0, watched: Boolean? = null) =
        PendingProgressEntity("v", position, watched, updatedAtMillis = sessionStart + 60_000, sessionStartedAtMillis = sessionStart)

    private fun server(watched: Boolean = false, watchedAt: Long? = null, position: Double? = null) =
        PlayerDto(watched = watched, watchedDate = watchedAt, duration = 842, position = position)

    @Test fun localPositionIsPushed() =
        assertEquals(listOf(SetPosition(120.0)), resolveProgress(local(120.0), server(position = 30.0)))

    @Test fun serverWatchedAfterTheSessionWins() =
        assertEquals(emptyList(), resolveProgress(local(120.0), server(watched = true, watchedAt = 1_500)))

    @Test fun serverWatchedBeforeTheSessionLosesToARewatch() =
        assertEquals(listOf(SetPosition(120.0)), resolveProgress(local(120.0), server(watched = true, watchedAt = 500)))

    @Test fun restartingNearZeroClearsTheServerPosition() =
        assertEquals(listOf(ClearPosition), resolveProgress(local(3.0), server(position = 200.0)))

    @Test fun nothingToClearWhenTheServerHasNoPosition() =
        assertEquals(emptyList(), resolveProgress(local(3.0), server(position = null)))

    @Test fun markWatchedIsSentWithoutAPosition() =
        assertEquals(listOf(MarkWatched(true)), resolveProgress(local(watched = true), server(position = 200.0)))

    @Test fun markWatchedThatTheServerAlreadyHasSendsNothing() =
        assertEquals(emptyList(), resolveProgress(local(watched = true), server(watched = true, watchedAt = 500)))

    @Test fun markUnwatchedAfterTheServerMarkIsSent() =
        assertEquals(listOf(MarkWatched(false)), resolveProgress(local(watched = false), server(watched = true, watchedAt = 500)))

    @Test fun serverMarkAfterAnUnwatchHereWins() =
        assertEquals(emptyList(), resolveProgress(local(watched = false), server(watched = true, watchedAt = 1_500)))

    @Test fun unwatchedThenWatchedAgainSendsBoth() =
        assertEquals(
            listOf(MarkWatched(false), SetPosition(90.0)),
            resolveProgress(local(90.0, watched = false), server(watched = true, watchedAt = 500)),
        )
}
