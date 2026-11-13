package com.malgra.reliquary.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.malgra.reliquary.core.data.di.NotificationIcon
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Runs [MediaDownloader] for one video as a foreground job, with a progress notification. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: ReliquaryDatabase,
    private val downloader: MediaDownloader,
    private val videos: VideoRepository,
    private val icon: NotificationIcon,
) : CoroutineWorker(context, params) {

    private val downloads = db.downloadDao()

    override suspend fun doWork(): Result {
        val videoId = inputData.getString(KEY_VIDEO_ID) ?: return Result.failure()
        downloads.get(videoId) ?: return Result.success() // removed while queued
        val video = db.videoDao().get(videoId)?.video
        if (video == null || video.mediaUrl.isEmpty()) {
            downloads.update(videoId, DownloadStatus.Failed.name, 0, 0)
            return Result.failure()
        }
        // Can be refused when started from the background; the download still runs, just shorter-lived.
        runCatching { setForeground(foregroundInfo(videoId, video.title, null)) }

        val outcome = downloader.download(videoId, video.mediaUrl, video.mediaSizeBytes) { written, total ->
            setProgress(workDataOf(KEY_BYTES to written, KEY_TOTAL to total))
            runCatching { setForeground(foregroundInfo(videoId, video.title, fraction(written, total))) }
        }
        return when (outcome) {
            MediaDownloader.Outcome.Done -> {
                videos.get(videoId)?.let { downloader.downloadSubtitles(videoId, it.subtitles) }
                Result.success()
            }
            MediaDownloader.Outcome.Retry -> Result.retry()
            MediaDownloader.Outcome.Failed -> Result.failure()
        }
    }

    private fun fraction(written: Long, total: Long): Float? = if (total > 0) written.toFloat() / total else null

    private fun foregroundInfo(videoId: String, title: String, fraction: Float?): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.download_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = applicationContext.getString(R.string.download_channel_description) },
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(icon.smallIcon)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title, title))
            .setProgress(100, ((fraction ?: 0f) * 100).toInt(), fraction == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(videoId.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_BYTES = "bytes"
        const val KEY_TOTAL = "total"
        private const val CHANNEL_ID = "downloads"
    }
}
