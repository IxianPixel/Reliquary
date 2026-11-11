package com.malgra.reliquary.core.data.download

import com.malgra.reliquary.core.data.library.toModel
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.model.Download
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.DownloadedVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Runs downloads in the background (WorkManager in the app). */
interface DownloadScheduler {
    fun start(videoId: String)
    fun cancel(videoId: String)
    fun cancelAll()
}

@Singleton
class DownloadRepository @Inject constructor(
    private val db: ReliquaryDatabase,
    private val files: DownloadFiles,
    private val scheduler: DownloadScheduler,
) {
    private val downloads = db.downloadDao()

    fun download(videoId: String): Flow<Download?> = downloads.observe(videoId).map { it?.toModel() }

    fun all(): Flow<List<DownloadedVideo>> = downloads.observeAll().map { rows ->
        rows.map { DownloadedVideo(it.download.toModel(), it.video?.toModel()) }
    }

    /** Queues the video; downloading starts once there's a connection. */
    suspend fun start(videoId: String) {
        val existing = downloads.get(videoId)
        if (existing?.state == DownloadStatus.Completed.name && files.complete(videoId).exists()) return
        downloads.upsert(
            DownloadEntity(
                videoId = videoId,
                state = DownloadStatus.Queued.name,
                bytesDownloaded = existing?.bytesDownloaded ?: 0,
                totalBytes = existing?.totalBytes ?: 0,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
            ),
        )
        scheduler.start(videoId)
    }

    /** Cancels a download in progress, or deletes a finished one. */
    suspend fun remove(videoId: String) {
        scheduler.cancel(videoId)
        downloads.delete(videoId)
        withContext(Dispatchers.IO) { files.delete(videoId) }
    }

    /** The finished file for [videoId], if there is one. */
    suspend fun localFile(videoId: String): File? = withContext(Dispatchers.IO) {
        val row = downloads.get(videoId)
        files.complete(videoId).takeIf { row?.state == DownloadStatus.Completed.name && it.exists() }
    }
}

private fun DownloadEntity.toModel() = Download(
    videoId = videoId,
    status = DownloadStatus.entries.firstOrNull { it.name == state } ?: DownloadStatus.Failed,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
)
