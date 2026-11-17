package com.malgra.reliquary.core.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopSyncSchedulersTest {

    @Test fun progressSyncRetriesWithBackoffUntilDone() = runTest {
        val runs = mutableListOf<Long>()
        val results = ArrayDeque(listOf(false, false, true))
        val scheduler = DesktopProgressSyncScheduler(backgroundScope, { runs += currentTime; results.removeFirst() })

        scheduler.requestSync()
        advanceTimeBy(1.hours)

        assertEquals(listOf(0L, 30_000L, 90_000L), runs)
    }

    @Test fun aNewProgressRequestReplacesOneWaitingToRetry() = runTest {
        val runs = mutableListOf<Long>()
        val scheduler = DesktopProgressSyncScheduler(backgroundScope, { runs += currentTime; runs.size > 1 })

        scheduler.requestSync()
        advanceTimeBy(10_000)
        scheduler.requestSync()
        advanceTimeBy(1.hours)

        // The second request ran at once instead of at the first one's 30 s retry, and then succeeded.
        assertEquals(listOf(0L, 10_000L), runs)
    }

    @Test fun librarySyncRunsAtLaunchThenEvery12Hours() = runTest {
        val runs = mutableListOf<Long>()
        val scheduler = DesktopLibrarySyncScheduler(backgroundScope, { runs += currentTime; true })

        scheduler.schedulePeriodic()
        scheduler.schedulePeriodic() // kept, not restarted
        runCurrent()
        advanceTimeBy(25.hours)

        assertEquals(listOf(0L, 12.hours.inWholeMilliseconds, 24.hours.inWholeMilliseconds), runs)
    }

    @Test fun syncNowRunsAlongsideTheSchedule() = runTest {
        val runs = mutableListOf<Long>()
        val scheduler = DesktopLibrarySyncScheduler(backgroundScope, { runs += currentTime; true })

        scheduler.schedulePeriodic()
        runCurrent()
        advanceTimeBy(1.hours)
        scheduler.syncNow()
        runCurrent()

        assertEquals(listOf(0L, 1.hours.inWholeMilliseconds), runs)
    }
}
