package com.malgra.reliquary.core.data.download

import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps downloads under the storage limit in Settings. A download that won't
 * fit waits as [DownloadStatus.OverStorageLimit]; nothing is ever deleted to
 * make room. [DownloadRepository] starts the waiting ones again when space is
 * freed or the limit changes.
 */
@Singleton
class StorageLimit @Inject constructor(
    private val db: ReliquaryDatabase,
    private val files: DownloadFiles,
    private val settings: AppSettings,
) {
    private val downloads = db.downloadDao()

    // Several downloads can start at once (WorkManager runs them in parallel): each one's check has to
    // see the others that already passed theirs.
    private val mutex = Mutex()

    /**
     * True when [videoId] fits, and marks it downloading so later checks count
     * the rest of it. Otherwise marks it over the limit and returns false.
     */
    suspend fun reserve(videoId: String, mediaSizeBytes: Long): Boolean = mutex.withLock {
        val limit = settings.storageLimitBytes() ?: return true
        val row = downloads.get(videoId) ?: return true
        val fits = withContext(Dispatchers.IO) {
            val partial = files.partial(videoId).length()
            // On disk now, plus what the downloads already running have still to write, plus this one's rest.
            files.usedBytes() + stillToWrite(except = videoId) + (mediaSizeBytes - partial).coerceAtLeast(0) <= limit
        }
        val state = if (fits) DownloadStatus.Downloading else DownloadStatus.OverStorageLimit
        downloads.update(videoId, state.name, row.bytesDownloaded, row.totalBytes)
        fits
    }

    private suspend fun stillToWrite(except: String): Long = downloads.observeAll().first()
        .filter { it.download.state == DownloadStatus.Downloading.name && it.download.videoId != except }
        .sumOf { row ->
            val total = row.download.totalBytes.takeIf { it > 0 } ?: row.video?.mediaSizeBytes ?: 0
            (total - row.download.bytesDownloaded).coerceAtLeast(0)
        }
}
