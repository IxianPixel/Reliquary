package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.download.DownloadExtras
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Applies keep-offline rules, refreshes downloaded videos' details and fills in
 * any subtitles or thumbnails their downloads are missing.
 */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val keepOffline: KeepOfflineRepository,
    private val downloadExtras: DownloadExtras,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = when (keepOffline.syncAll()) {
        KeepOfflineRepository.Outcome.Done -> {
            downloadExtras.fillInMissing()
            Result.success()
        }
        KeepOfflineRepository.Outcome.Retry -> Result.retry()
    }
}
