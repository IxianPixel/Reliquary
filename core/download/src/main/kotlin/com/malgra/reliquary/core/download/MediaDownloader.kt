package com.malgra.reliquary.core.download

import android.util.Log
import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.database.dao.DownloadDao
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.Subtitle
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

/**
 * Copies one video's MP4 to the device through the signed-in client, keeping
 * the download row up to date.
 *
 * It resumes a partial file with `Range: bytes=N-`. It never sends `If-Range`,
 * which makes Tube Archivist send the whole file. With no ETag to go by, a
 * resumed response whose total doesn't match the video's media size means the
 * file changed on the server, so the partial file is thrown away.
 */
class MediaDownloader @Inject constructor(
    private val client: OkHttpClient,
    private val files: DownloadFiles,
    private val downloads: DownloadDao,
) {
    enum class Outcome { Done, Retry, Failed }

    /** [onProgress] is called about every [reportIntervalMillis] with (written, total or -1). */
    suspend fun download(
        videoId: String,
        url: String,
        mediaSize: Long,
        reportIntervalMillis: Long = REPORT_INTERVAL_MS,
        onProgress: suspend (written: Long, total: Long) -> Unit = { _, _ -> },
    ): Outcome = withContext(Dispatchers.IO) {
        try {
            transfer(videoId, url, mediaSize, reportIntervalMillis, onProgress)
        } catch (e: IOException) {
            Log.w(TAG, "Download of $videoId interrupted; will resume", e)
            val total = downloads.get(videoId)?.totalBytes ?: 0
            downloads.update(videoId, DownloadStatus.Queued.name, files.partial(videoId).length(), total)
            Outcome.Retry
        }
    }

    /**
     * Saves the video's subtitle files next to it, so they work offline. Best
     * effort: a subtitle that fails is skipped (the player falls back to the
     * server's copy when online).
     */
    suspend fun downloadSubtitles(videoId: String, subtitles: List<Subtitle>) = withContext(Dispatchers.IO) {
        for (subtitle in subtitles) {
            val target = files.subtitle(videoId, subtitle.key)
            if (!target.exists()) saveSmallFile(subtitle.url, target, "Subtitle ${subtitle.key} for $videoId")
        }
    }

    /**
     * Saves the video's thumbnail next to it and records it on the download, so
     * the thumbnail shows offline. Best effort, like subtitles.
     */
    suspend fun downloadThumbnail(videoId: String, url: String?) = withContext(Dispatchers.IO) {
        if (url == null || !url.startsWith("http")) return@withContext
        val target = files.thumbnail(videoId)
        if (target.exists() || saveSmallFile(url, target, "Thumbnail for $videoId")) {
            downloads.setThumbnail(videoId, target.path)
        }
    }

    /** Fetches [url] into [target] via a `.part` file; false (and logged) if it couldn't. */
    private fun saveSmallFile(url: String, target: File, what: String): Boolean = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val part = File(target.path + ".part")
            part.outputStream().use { out -> response.body.byteStream().copyTo(out) }
            part.renameTo(target).also { saved -> if (!saved) part.delete() }
        }
    } catch (e: IOException) {
        Log.w(TAG, "$what not saved", e)
        false
    }

    private suspend fun transfer(
        videoId: String,
        url: String,
        mediaSize: Long,
        reportIntervalMillis: Long,
        onProgress: suspend (Long, Long) -> Unit,
    ): Outcome {
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
                        return Outcome.Retry
                    }
                }
                200 -> {
                    append = false
                    total = response.body.contentLength().takeIf { it > 0 } ?: mediaSize
                }
                416 -> { // what we have doesn't fit the file any more
                    part.delete()
                    return Outcome.Retry
                }
                in 400..499 -> {
                    Log.w(TAG, "Download of $videoId refused: HTTP ${response.code}")
                    downloads.update(videoId, DownloadStatus.Failed.name, have, mediaSize)
                    return Outcome.Failed
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
                        if (now - lastReport >= reportIntervalMillis) {
                            lastReport = now
                            downloads.update(videoId, DownloadStatus.Downloading.name, written, total.coerceAtLeast(0))
                            onProgress(written, total)
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
        return Outcome.Done
    }

    private companion object {
        const val TAG = "MediaDownloader"
        const val BUFFER_BYTES = 64 * 1024
        const val REPORT_INTERVAL_MS = 750L
    }
}
