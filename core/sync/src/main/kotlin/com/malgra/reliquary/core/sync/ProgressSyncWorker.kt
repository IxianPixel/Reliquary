package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malgra.reliquary.core.data.progress.ProgressSyncer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Pushes progress saved on this device to the server; retried with backoff while offline. */
@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncer: ProgressSyncer,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (syncer.syncAll()) {
        ProgressSyncer.Outcome.Done -> Result.success()
        ProgressSyncer.Outcome.Retry -> Result.retry()
    }
}
