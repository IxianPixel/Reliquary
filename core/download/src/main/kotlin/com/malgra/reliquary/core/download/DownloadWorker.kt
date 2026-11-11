package com.malgra.reliquary.core.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

/**
 * Saves one video's MP4 to the device through the signed-in client.
 *
 * It resumes a partial file with `Range: bytes=N-`. It never sends `If-Range`,
 * which makes Tube Archivist send the whole file. With no ETag to go by, a
 * resumed response whose total doesn't match the video's media size means the
 * file changed on the server, so the partial file is thrown away.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: ReliquaryDatabase,
    private val files: DownloadFiles,
    private val client: OkHttpClient,
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

        return try {
            withContext(Dispatchers.IO) { download(videoId, video.title, video.mediaUrl, video.mediaSizeBytes) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "Download of $videoId interrupted; will resume", e)
            val have = files.partial(videoId).length()
            downloads.update(videoId, DownloadStatus.Queued.name, have, downloads.get(videoId)?.totalBytes ?: 0)
            Result.retry()
        }
    }

    private suspend fun download(videoId: String, title: String, url: String, mediaSize: Long): Result {
        val part = files.partial(videoId)
        val have = if (part.exists()) part.length() else 0L
        val request = Request.Builder().url(url).apply { if (have > 0) header("Range", "bytes=$have-") }.build()

        client.newCall(request).execute().use { response ->
            val append: Boolean
            val total: Long
            when (response.code) {
                206 -> {
                    append = true
                    total = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1
                    if (mediaSize > 0 && total > 0 && total != mediaSize) {
                        part.delete()
                        return Result.retry()
                    }
                }
                200 -> {
                    append = false
                    total = response.body.contentLength().takeIf { it > 0 } ?: mediaSize
                }
                416 -> { // what we have doesn't fit the file any more
                    part.delete()
                    return Result.retry()
                }
                in 400..499 -> {
                    Log.w(TAG, "Download of $videoId refused: HTTP ${response.code}")
                    downloads.update(videoId, DownloadStatus.Failed.name, have, mediaSize)
                    return Result.failure()
                }
                else -> throw IOException("HTTP ${response.code}")
            }

            var written = if (append) have else 0L
            downloads.update(videoId, DownloadStatus.Downloading.name, written, total.coerceAtLeast(0))
            var lastReport = 0L
            FileOutputStream(part, append).use { out ->
                response.body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= REPORT_INTERVAL_MS) {
                            lastReport = now
                            downloads.update(videoId, DownloadStatus.Downloading.name, written, total.coerceAtLeast(0))
                            setProgress(workDataOf(KEY_BYTES to written, KEY_TOTAL to total))
                            runCatching { setForeground(foregroundInfo(videoId, title, fraction(written, total))) }
                        }
                    }
                }
            }
            if (total > 0 && written != total) throw IOException("Got $written of $total bytes")
        }

        val size = part.length()
        val complete = files.complete(videoId)
        complete.delete()
        if (!part.renameTo(complete)) throw IOException("Couldn't move the finished download into place")
        downloads.update(videoId, DownloadStatus.Completed.name, size, size)
        return Result.success()
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
            .setSmallIcon(android.R.drawable.stat_sys_download)
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
        private const val TAG = "DownloadWorker"
        private const val CHANNEL_ID = "downloads"
        private const val BUFFER_BYTES = 64 * 1024
        private const val REPORT_INTERVAL_MS = 750L
    }
}
