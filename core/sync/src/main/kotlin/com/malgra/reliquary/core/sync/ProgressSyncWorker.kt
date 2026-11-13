package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malgra.reliquary.core.data.progress.ProgressSyncer
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Pushes progress saved on this device to the server; retried with backoff while offline. */
@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: ProgressSyncer,
    private val keepOffline: KeepOfflineRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val outcome = syncer.syncAll()
        // A video just finished may be one a rule should now remove.
        keepOffline.removeWatched()
        return when (outcome) {
            ProgressSyncer.Outcome.Done -> Result.success()
            ProgressSyncer.Outcome.Retry -> Result.retry()
        }
    }
}
