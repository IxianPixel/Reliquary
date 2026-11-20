package com.malgra.reliquary.core.data.download

import com.malgra.reliquary.core.network.log.Log
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
        dir.listFiles()?.forEach(::remove)
    }

    fun delete(videoId: String) {
        remove(complete(videoId))
        remove(partial(videoId))
        remove(thumbnail(videoId))
        val subtitlePrefix = "${safe(videoId)}."
        dir.listFiles { file -> file.name.startsWith(subtitlePrefix) && file.name.endsWith(".vtt") }
            ?.forEach(::remove)
    }

    /**
     * Deletes [file] and logs it when it's still there. Windows won't delete a
     * file a player has open, so this can fail; the file is then left behind.
     */
    private fun remove(file: File) {
        if (file.exists() && !file.delete()) Log.w(TAG, "Couldn't delete ${file.name}; it may be open in a player")
    }

    /**
     * YouTube IDs are already safe; this guards against anything else reaching a file name.
     * Windows reserves device names such as CON and NUL even with an extension, so those get a prefix.
     */
    private fun safe(id: String): String {
        val clean = id.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        return if (clean.substringBefore('.').uppercase() in WINDOWS_RESERVED) "_$clean" else clean
    }

    private companion object {
        const val TAG = "DownloadFiles"
        val WINDOWS_RESERVED = setOf("CON", "PRN", "AUX", "NUL") +
            (1..9).flatMap { listOf("COM$it", "LPT$it") }
    }
}
