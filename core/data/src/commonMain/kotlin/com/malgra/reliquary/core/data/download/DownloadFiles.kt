package com.malgra.reliquary.core.data.download

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How downloaded videos are laid out in the platform's [DownloadStorage]
 * folder. A download writes to a `.part` file and renames it when complete.
 */
@Singleton
class DownloadFiles @Inject constructor(private val storage: DownloadStorage) {

    private val dir: File
        get() = storage.directory().also { it.mkdirs() }

    fun complete(videoId: String): File = File(dir, "${safe(videoId)}.mp4")

    fun partial(videoId: String): File = File(dir, "${safe(videoId)}.mp4.part")

    /** The thumbnail saved with the download. */
    fun thumbnail(videoId: String): File = File(dir, "${safe(videoId)}.jpg")

    /** A subtitle saved with the download; [key] is the track's key, e.g. "en.user". */
    fun subtitle(videoId: String, key: String): File = File(dir, "${safe(videoId)}.${safe(key)}.vtt")

    /** Bytes used by downloads (finished and partial). */
    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    /** Space downloads can use. */
    fun freeBytes(): Long = storage.freeBytes(dir)

    /** Every downloaded and partial file, e.g. on sign-out. */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun delete(videoId: String) {
        complete(videoId).delete()
        partial(videoId).delete()
        thumbnail(videoId).delete()
        val subtitlePrefix = "${safe(videoId)}."
        dir.listFiles { file -> file.name.startsWith(subtitlePrefix) && file.name.endsWith(".vtt") }
            ?.forEach { it.delete() }
    }

    /** YouTube IDs are already safe; this guards against anything else reaching a file name. */
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
}
