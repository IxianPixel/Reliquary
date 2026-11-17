package com.malgra.reliquary.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs a [LibrarySync]. */
@HiltWorker
class LibrarySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: LibrarySync,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = if (sync.run()) Result.success() else Result.retry()
}
