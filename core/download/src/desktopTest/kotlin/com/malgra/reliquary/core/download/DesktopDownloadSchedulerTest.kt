package com.malgra.reliquary.core.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DesktopDownloadSchedulerTest {

    /** Each run waits on its own gate, so a test decides when (and how) it finishes. */
    private class Runs {
        val started = mutableListOf<String>()
        val gates = mutableListOf<CompletableDeferred<MediaDownloader.Outcome>>()
        val startTimes = mutableListOf<Long>()

        /** Indexes (into [started]) of the runs that were cancelled. */
        val cancelled = mutableListOf<Int>()

        suspend fun run(scope: TestScope, videoId: String): MediaDownloader.Outcome {
            val index = started.size
            started += videoId
            startTimes += scope.currentTime
            val gate = CompletableDeferred<MediaDownloader.Outcome>().also { gates += it }
            try {
                return gate.await()
            } catch (e: CancellationException) {
                cancelled += index
                throw e
            }
        }
    }

    private fun TestScope.scheduler(runs: Runs, unfinished: List<String> = emptyList()) =
        DesktopDownloadScheduler(backgroundScope, { runs.run(this, it) }, { unfinished })

    @Test fun keepsARunningJobUnlessReplaced() = runTest {
        val runs = Runs()
        val scheduler = scheduler(runs)

        scheduler.start("v", wifiOnly = false)
        scheduler.start("v", wifiOnly = true)
        runCurrent()
        assertEquals(listOf("v"), runs.started)

        scheduler.start("v", wifiOnly = false, replace = true)
        runCurrent()
        assertEquals(listOf("v", "v"), runs.started)
        assertEquals(listOf(0), runs.cancelled)
    }

    @Test fun retriesWithDoublingBackoff() = runTest {
        val runs = Runs()
        scheduler(runs).start("v", wifiOnly = false)
        runCurrent()

        runs.gates[0].complete(MediaDownloader.Outcome.Retry)
        advanceTimeBy(30_000)
        runCurrent()
        runs.gates[1].complete(MediaDownloader.Outcome.Retry)
        advanceTimeBy(60_000)
        runCurrent()
        runs.gates[2].complete(MediaDownloader.Outcome.Done)
        advanceTimeBy(1_000_000)

        assertEquals(listOf(0L, 30_000L, 90_000L), runs.startTimes)
    }

    @Test fun aFailureIsNotRetried() = runTest {
        val runs = Runs()
        scheduler(runs).start("v", wifiOnly = false)
        runCurrent()
        runs.gates[0].complete(MediaDownloader.Outcome.Failed)
        advanceTimeBy(1_000_000)
        assertEquals(1, runs.started.size)
    }

    @Test fun runsTwoAtATime() = runTest {
        val runs = Runs()
        val scheduler = scheduler(runs)
        listOf("a", "b", "c").forEach { scheduler.start(it, wifiOnly = false) }
        runCurrent()
        assertEquals(listOf("a", "b"), runs.started)

        runs.gates[0].complete(MediaDownloader.Outcome.Done)
        runCurrent()
        assertEquals(listOf("a", "b", "c"), runs.started)
    }

    @Test fun cancelStopsAJobAndItCanStartAgain() = runTest {
        val runs = Runs()
        val scheduler = scheduler(runs)
        scheduler.start("v", wifiOnly = false)
        runCurrent()

        scheduler.cancel("v")
        runCurrent()
        assertEquals(listOf(0), runs.cancelled)

        scheduler.start("v", wifiOnly = false)
        runCurrent()
        assertEquals(2, runs.started.size)
    }

    @Test fun cancelAllStopsEverything() = runTest {
        val runs = Runs()
        val scheduler = scheduler(runs)
        scheduler.start("a", wifiOnly = false)
        scheduler.start("b", wifiOnly = false)
        runCurrent()

        scheduler.cancelAll()
        runCurrent()
        assertEquals(setOf(0, 1), runs.cancelled.toSet())
    }

    @Test fun resumesUnfinishedDownloadsAtLaunch() = runTest {
        val runs = Runs()
        scheduler(runs, unfinished = listOf("a", "b")).resumeUnfinished()
        runCurrent()
        assertEquals(listOf("a", "b"), runs.started)
    }
}
