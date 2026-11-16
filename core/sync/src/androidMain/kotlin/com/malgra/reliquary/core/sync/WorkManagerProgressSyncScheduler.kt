package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.malgra.reliquary.core.data.progress.ProgressSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One unique "progress-sync" job that waits for a network connection. Each
 * request replaces the job, so a pause syncs now even if an earlier attempt
 * failed and is waiting out its backoff (KEEP would have ignored it until
 * then). Replacing a running sync is safe: it only deletes rows it pushed.
 */
@Singleton
class WorkManagerProgressSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : ProgressSyncScheduler {

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<ProgressSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    companion object {
        const val WORK_NAME = "progress-sync"
    }
}
