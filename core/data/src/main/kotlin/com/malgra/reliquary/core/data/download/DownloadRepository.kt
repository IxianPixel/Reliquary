package com.malgra.reliquary.core.data.download

import com.malgra.reliquary.core.data.library.toModel
import com.malgra.reliquary.core.data.library.withLocalThumbnail
import com.malgra.reliquary.core.data.settings.AppSettings
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
    /** [replace] restarts a queued or running job, e.g. to change its network rule; it resumes. */
    fun start(videoId: String, wifiOnly: Boolean, replace: Boolean = false)
    fun cancel(videoId: String)
    fun cancelAll()
}

@Singleton
class DownloadRepository @Inject constructor(
    private val db: ReliquaryDatabase,
    private val files: DownloadFiles,
    private val scheduler: DownloadScheduler,
    private val settings: AppSettings,
) {
    private val downloads = db.downloadDao()

    fun download(videoId: String): Flow<Download?> = downloads.observe(videoId).map { it?.toModel() }

    fun all(): Flow<List<DownloadedVideo>> = downloads.observeAll().map { rows ->
        rows.map { DownloadedVideo(it.download.toModel(), it.video?.toModel()?.withLocalThumbnail(it.download.thumbnailPath)) }
    }

    /** Queues the video for the user; it becomes theirs even if a rule downloaded it. */
    suspend fun start(videoId: String) {
        val existing = downloads.get(videoId)
        if (existing?.state == DownloadStatus.Completed.name && files.complete(videoId).exists()) {
            if (existing.ruleId != null) downloads.upsert(existing.copy(ruleId = null))
            return
        }
        queue(videoId, existing, ruleId = null, wifiOnly = settings.wifiOnlyDownloads())
    }

    /**
     * Queues the video for a keep-offline rule, unless it's already downloaded
     * or downloading (the user's own downloads stay theirs).
     */
    suspend fun startForRule(videoId: String, ruleId: String, wifiOnly: Boolean) {
        val existing = downloads.get(videoId)
        if (existing != null && existing.state != DownloadStatus.Failed.name) return
        queue(videoId, existing, ruleId, wifiOnly)
    }

    private suspend fun queue(videoId: String, existing: DownloadEntity?, ruleId: String?, wifiOnly: Boolean) {
        downloads.upsert(
            DownloadEntity(
                videoId = videoId,
                state = DownloadStatus.Queued.name,
                bytesDownloaded = existing?.bytesDownloaded ?: 0,
                totalBytes = existing?.totalBytes ?: 0,
                createdAtMillis = existing?.createdAtMillis ?: System.currentTimeMillis(),
                ruleId = ruleId,
                thumbnailPath = existing?.thumbnailPath,
            ),
        )
        scheduler.start(videoId, wifiOnly)
    }

    /** Applies a changed Wi-Fi-only setting to the user's unfinished downloads (rules have their own). */
    suspend fun setWifiOnly(enabled: Boolean) {
        settings.setWifiOnlyDownloads(enabled)
        downloads.unfinishedManualIds().forEach { scheduler.start(it, enabled, replace = true) }
    }

    /** Cancels a download in progress, or deletes a finished one. */
    suspend fun remove(videoId: String) {
        scheduler.cancel(videoId)
        downloads.delete(videoId)
        withContext(Dispatchers.IO) { files.delete(videoId) }
    }

    /** Space used by downloads and space free, re-read whenever the downloads change. */
    fun storage(): Flow<Storage> = downloads.observeAll().map {
        withContext(Dispatchers.IO) { Storage(files.usedBytes(), files.freeBytes()) }
    }

    /** Subtitle files saved with [videoId]'s download, by track key. */
    suspend fun localSubtitles(videoId: String): Map<String, File> = withContext(Dispatchers.IO) {
        val video = db.videoDao().get(videoId)?.toModel() ?: return@withContext emptyMap()
        video.subtitles.mapNotNull { sub -> files.subtitle(videoId, sub.key).takeIf { it.exists() }?.let { sub.key to it } }
            .toMap()
    }

    /** The finished file for [videoId], if there is one. */
    suspend fun localFile(videoId: String): File? = withContext(Dispatchers.IO) {
        val row = downloads.get(videoId)
        files.complete(videoId).takeIf { row?.state == DownloadStatus.Completed.name && it.exists() }
    }
}

data class Storage(val usedBytes: Long, val freeBytes: Long) {
    /** Share of the space downloads could have (used + free) that they use. */
    val usedFraction: Float
        get() = if (usedBytes + freeBytes > 0) usedBytes.toFloat() / (usedBytes + freeBytes) else 0f
}

private fun DownloadEntity.toModel() = Download(
    videoId = videoId,
    status = DownloadStatus.entries.firstOrNull { it.name == state } ?: DownloadStatus.Failed,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
)
