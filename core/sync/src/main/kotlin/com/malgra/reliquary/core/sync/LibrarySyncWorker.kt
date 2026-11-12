package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Applies keep-offline rules and refreshes downloaded videos' details. */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val keepOffline: KeepOfflineRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (keepOffline.syncAll()) {
        KeepOfflineRepository.Outcome.Done -> Result.success()
        KeepOfflineRepository.Outcome.Retry -> Result.retry()
    }
}
