package com.malgra.reliquary.core.download

import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.data.download.StorageLimit
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import javax.inject.Inject

/**
 * One video's download from start to finish, whatever runs it (WorkManager on
 * Android, the app's own queue on desktop): the video itself, then its
 * subtitles and thumbnail.
 */
class DownloadJob @Inject constructor(
    private val db: ReliquaryDatabase,
    private val downloader: MediaDownloader,
    private val extras: DownloadExtras,
    private val files: DownloadFiles,
    private val storageLimit: StorageLimit,
) {
    private val downloads = db.downloadDao()

    /**
     * [onStart] gets the video's title once it's known to exist. With
     * [checkFreeSpace] (where the platform doesn't wait for storage itself) it
     * retries later rather than start without room for the rest of the file.
     */
    suspend fun run(
        videoId: String,
        checkFreeSpace: Boolean = false,
        onStart: suspend (title: String) -> Unit = {},
        onProgress: suspend (written: Long, total: Long) -> Unit = { _, _ -> },
    ): MediaDownloader.Outcome {
        downloads.get(videoId) ?: return MediaDownloader.Outcome.Done // removed while queued
        val video = db.videoDao().get(videoId)?.video
        if (video == null || video.mediaUrl.isEmpty()) {
            downloads.update(videoId, DownloadStatus.Failed.name, 0, 0)
            return MediaDownloader.Outcome.Failed
        }
        if (checkFreeSpace && files.freeBytes() < video.mediaSizeBytes - files.partial(videoId).length()) {
            return MediaDownloader.Outcome.Retry
        }
        if (!storageLimit.reserve(videoId, video.mediaSizeBytes)) return MediaDownloader.Outcome.Held
        onStart(video.title)

        val outcome = downloader.download(videoId, video.mediaUrl, video.mediaSizeBytes, onProgress = onProgress)
        if (outcome == MediaDownloader.Outcome.Done) extras.save(videoId)
        return outcome
    }
}
