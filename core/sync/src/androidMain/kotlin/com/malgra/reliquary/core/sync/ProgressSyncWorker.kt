package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs a [ProgressSync]; retried with backoff while offline. */
@HiltWorker
class ProgressSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: ProgressSync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (sync.run()) Result.success() else Result.retry()
}
