package com.malgra.reliquary.core.download

import com.malgra.reliquary.core.data.di.ApplicationScope
import com.malgra.reliquary.core.data.download.DownloadScheduler
import com.malgra.reliquary.core.data.retryWithBackoff
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Desktop downloads, run in the app's own process: one job per video, like
 * WorkManager's unique work, retried with the same backoff, a few at a time.
 * Desktop can't tell Wi-Fi from a metered network, so `wifiOnly` is ignored;
 * instead of waiting for storage it checks there's room before each attempt.
 * Jobs end with the app, so it calls [resumeUnfinished] at launch.
 */
@Singleton
class DesktopDownloadScheduler internal constructor(
    private val scope: CoroutineScope,
    private val run: suspend (videoId: String) -> MediaDownloader.Outcome,
    private val unfinished: suspend () -> List<String>,
    private val initialBackoff: Duration = 30.seconds,
) : DownloadScheduler {

    @Inject constructor(@ApplicationScope scope: CoroutineScope, job: DownloadJob, db: ReliquaryDatabase) : this(
        scope,
        run = { job.run(it, checkFreeSpace = true) },
        unfinished = {
            val waiting = setOf(DownloadStatus.Queued.name, DownloadStatus.Downloading.name)
            db.downloadDao().observeAll().first().map { it.download }.filter { it.state in waiting }.map { it.videoId }
        },
    )

    private val jobs = mutableMapOf<String, Job>()
    private val slots = Semaphore(MAX_AT_ONCE)

    override fun start(videoId: String, wifiOnly: Boolean, replace: Boolean) {
        synchronized(jobs) {
            val existing = jobs[videoId]
            if (existing?.isActive == true && !replace) return
            existing?.cancel()
            val job = scope.launch {
                existing?.join() // the replaced run must stop writing the partial file first
                retryWithBackoff(initialBackoff) {
                    slots.withPermit { run(videoId) } != MediaDownloader.Outcome.Retry
                }
            }
            jobs[videoId] = job
            job.invokeOnCompletion { synchronized(jobs) { if (jobs[videoId] === job) jobs.remove(videoId) } }
        }
    }

    override fun cancel(videoId: String) {
        synchronized(jobs) { jobs.remove(videoId)?.cancel() }
    }

    override fun cancelAll() {
        synchronized(jobs) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
    }

    /** Restarts the downloads that were queued or under way when the app last closed. */
    fun resumeUnfinished() {
        scope.launch { unfinished().forEach { start(it, wifiOnly = false) } }
    }

    private companion object {
        const val MAX_AT_ONCE = 2
    }
}
