package com.malgra.reliquary.core.download

import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.database.dao.DownloadDao
import javax.inject.Inject

/**
 * The files saved alongside a downloaded video (subtitles and thumbnail), so a
 * download is complete offline. Each is best effort; whatever is missing is
 * filled in on a later library sync.
 */
class DownloadExtras @Inject constructor(
    private val downloader: MediaDownloader,
    private val videos: VideoRepository,
    private val downloads: DownloadDao,
) {
    /** Saves whatever [videoId]'s download is missing, using its cached details. */
    suspend fun save(videoId: String) {
        val video = videos.get(videoId) ?: return
        downloader.downloadSubtitles(videoId, video.subtitles)
        if (downloads.get(videoId)?.thumbnailPath == null) downloader.downloadThumbnail(videoId, video.thumbnailUrl)
    }

    /** Fills in the extras for every finished download, e.g. ones made before extras were saved. */
    suspend fun fillInMissing() {
        for (videoId in downloads.completedIds()) save(videoId)
    }
}
