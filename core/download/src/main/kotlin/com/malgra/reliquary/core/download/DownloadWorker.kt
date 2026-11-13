package com.malgra.reliquary.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.SystemClock
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.malgra.reliquary.core.data.di.NotificationIcon
import com.malgra.reliquary.core.data.download.TransferRate
import com.malgra.reliquary.core.data.download.timeLeft
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlin.math.ceil

/** Runs [MediaDownloader] for one video as a foreground job, with a progress notification. */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: ReliquaryDatabase,
    private val downloader: MediaDownloader,
    private val extras: DownloadExtras,
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

        val rate = TransferRate()
        val outcome = downloader.download(videoId, video.mediaUrl, video.mediaSizeBytes) { written, total ->
            setProgress(workDataOf(KEY_BYTES to written, KEY_TOTAL to total))
            val stats = statsText(written, total, rate.add(SystemClock.elapsedRealtime(), written))
            runCatching { setForeground(foregroundInfo(videoId, video.title, fraction(written, total), stats)) }
        }
        return when (outcome) {
            MediaDownloader.Outcome.Done -> {
                extras.save(videoId)
                Result.success()
            }
            MediaDownloader.Outcome.Retry -> Result.retry()
            MediaDownloader.Outcome.Failed -> Result.failure()
        }
    }

    private fun fraction(written: Long, total: Long): Float? = if (total > 0) written.toFloat() / total else null

    /** "12 MB of 770 MB · 4.1 MB/s · about 3 min left", as much of it as is known. */
    private fun statsText(written: Long, total: Long, bytesPerSecond: Long?): String {
        val context = applicationContext
        fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)
        val known = total.takeIf { it > 0 }
        val minutes = known?.let { timeLeft(it - written, bytesPerSecond) }
            ?.let { ceil(it.inWholeSeconds / 60.0).toInt().coerceAtLeast(1) }
        return listOfNotNull(
            known?.let { context.getString(R.string.download_notification_of, size(written), size(it)) } ?: size(written),
            bytesPerSecond?.let { context.getString(R.string.download_notification_speed, size(it)) },
            minutes?.let { context.resources.getQuantityString(R.plurals.download_notification_minutes_left, it, it) },
        ).joinToString(" · ")
    }

    private fun foregroundInfo(videoId: String, title: String, fraction: Float?, stats: String? = null): ForegroundInfo {
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
            .setContentText(stats)
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
