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
import kotlinx.coroutines.flow.combine
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
        resumeOverLimit()
    }

    /** Sets the storage limit (null for none); downloads waiting on the old one try again. */
    suspend fun setStorageLimit(bytes: Long?) {
        settings.setStorageLimitBytes(bytes)
        resumeOverLimit()
    }

    /** Queues the downloads waiting under the storage limit again; [StorageLimit] lets in the ones that now fit. */
    private suspend fun resumeOverLimit() {
        val waiting = downloads.withState(DownloadStatus.OverStorageLimit.name).ifEmpty { return }
        val wifiOnly = settings.wifiOnlyDownloads()
        for (row in waiting) {
            downloads.update(row.videoId, DownloadStatus.Queued.name, row.bytesDownloaded, row.totalBytes)
            val rule = row.ruleId?.let { db.keepOfflineRuleDao().get(it) }
            scheduler.start(row.videoId, rule?.wifiOnly ?: wifiOnly)
        }
    }

    /** Space used by downloads, space free and the limit, re-read whenever the downloads or the limit change. */
    fun storage(): Flow<Storage> = combine(downloads.observeAll(), settings.storageLimitBytes) { _, limit ->
        withContext(Dispatchers.IO) { Storage(files.usedBytes(), files.freeBytes(), limit) }
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

/** [limitBytes] is the storage limit from Settings, or null for none. */
data class Storage(val usedBytes: Long, val freeBytes: Long, val limitBytes: Long? = null) {
    /** Share of the space downloads may have that they use: of the limit if there is one, else of used + free. */
    val usedFraction: Float
        get() {
            val room = limitBytes ?: (usedBytes + freeBytes)
            return if (room > 0) (usedBytes.toFloat() / room).coerceIn(0f, 1f) else 0f
        }
}

private fun DownloadEntity.toModel() = Download(
    videoId = videoId,
    status = DownloadStatus.entries.firstOrNull { it.name == state } ?: DownloadStatus.Failed,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
)
