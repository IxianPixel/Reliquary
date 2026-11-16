package com.malgra.reliquary.core.sync

import com.malgra.reliquary.core.data.di.ApplicationScope
import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import com.malgra.reliquary.core.data.retryWithBackoff
import com.malgra.reliquary.core.data.rules.LibrarySyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One job at a time, like WorkManager's unique work with REPLACE: a new
 * request cancels the one waiting out its backoff and runs now.
 */
private class ReplacingJob(private val scope: CoroutineScope, private val initialBackoff: Duration) {
    private var job: Job? = null

    @Synchronized fun replace(attempt: suspend () -> Boolean) {
        job?.cancel()
        job = scope.launch { retryWithBackoff(initialBackoff, attempt) }
    }
}

/**
 * Desktop progress sync, in the app's own process. Each request replaces the
 * pending one, as on Android; replacing a running sync is safe because it only
 * deletes rows it pushed. Unsynced progress stays in Room, so whatever is left
 * when the app quits is pushed on the next request.
 */
@Singleton
class DesktopProgressSyncScheduler internal constructor(
    scope: CoroutineScope,
    private val sync: suspend () -> Boolean,
    initialBackoff: Duration = 30.seconds,
) : ProgressSyncScheduler {

    @Inject constructor(@ApplicationScope scope: CoroutineScope, sync: ProgressSync) : this(scope, sync::run)

    private val job = ReplacingJob(scope, initialBackoff)

    override fun requestSync() = job.replace(sync)
}

/**
 * Desktop library sync, in the app's own process: every 12 hours while the app
 * is open (starting at launch, since nothing runs while it's closed), plus
 * one-off runs that replace each other.
 */
@Singleton
class DesktopLibrarySyncScheduler internal constructor(
    private val scope: CoroutineScope,
    private val sync: suspend () -> Boolean,
    private val interval: Duration = 12.hours,
    private val periodicBackoff: Duration = 1.minutes,
    nowBackoff: Duration = 30.seconds,
) : LibrarySyncScheduler {

    @Inject constructor(@ApplicationScope scope: CoroutineScope, sync: LibrarySync) : this(scope, sync::run)

    private var periodic: Job? = null
    private val now = ReplacingJob(scope, nowBackoff)

    /** Keeps an existing schedule, like WorkManager's KEEP. */
    @Synchronized override fun schedulePeriodic() {
        if (periodic?.isActive == true) return
        periodic = scope.launch {
            while (true) {
                retryWithBackoff(periodicBackoff, sync)
                delay(interval)
            }
        }
    }

    override fun syncNow() = now.replace(sync)
}
