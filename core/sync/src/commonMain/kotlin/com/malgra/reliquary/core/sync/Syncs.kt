package com.malgra.reliquary.core.sync

import com.malgra.reliquary.core.data.progress.ProgressSyncer
import com.malgra.reliquary.core.data.rules.KeepOfflineRepository
import com.malgra.reliquary.core.download.DownloadExtras
import javax.inject.Inject

/**
 * Applies keep-offline rules, refreshes downloaded videos' details and fills in
 * any subtitles or thumbnails their downloads are missing. True when done,
 * false to retry later.
 */
class LibrarySync @Inject constructor(
    private val keepOffline: KeepOfflineRepository,
    private val downloadExtras: DownloadExtras,
) {
    suspend fun run(): Boolean = when (keepOffline.syncAll()) {
        KeepOfflineRepository.Outcome.Done -> {
            downloadExtras.fillInMissing()
            true
        }
        KeepOfflineRepository.Outcome.Retry -> false
    }
}

/** Pushes progress saved on this device to the server. True when done, false to retry later. */
class ProgressSync @Inject constructor(
    private val syncer: ProgressSyncer,
    private val keepOffline: KeepOfflineRepository,
) {
    suspend fun run(): Boolean {
        val outcome = syncer.syncAll()
        // A video just finished may be one a rule should now remove.
        keepOffline.removeWatched()
        return outcome == ProgressSyncer.Outcome.Done
    }
}
