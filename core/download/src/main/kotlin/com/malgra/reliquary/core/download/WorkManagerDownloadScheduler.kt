package com.malgra.reliquary.core.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.malgra.reliquary.core.data.download.DownloadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One unique job per video; it waits for a connection (Wi-Fi if [start]'s
 * wifiOnly is set) and resumes after failures.
 */
@Singleton
class WorkManagerDownloadScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : DownloadScheduler {

    private val workManager get() = WorkManager.getInstance(context)

    override fun start(videoId: String, wifiOnly: Boolean, replace: Boolean) {
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_VIDEO_ID to videoId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        workManager.enqueueUniqueWork(workName(videoId), policy, request)
    }

    override fun cancel(videoId: String) {
        workManager.cancelUniqueWork(workName(videoId))
    }

    override fun cancelAll() {
        workManager.cancelAllWorkByTag(TAG)
    }

    private fun workName(videoId: String) = "download-$videoId"

    private companion object {
        const val TAG = "download"
    }
}
