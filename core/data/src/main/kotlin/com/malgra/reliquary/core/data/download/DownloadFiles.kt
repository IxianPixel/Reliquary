package com.malgra.reliquary.core.data.download

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Where downloaded videos live: the app's own external files folder (no
 * permission needed, removed with the app). A download writes to a `.part`
 * file and renames it when complete.
 */
@Singleton
class DownloadFiles @Inject constructor(@ApplicationContext private val context: Context) {

    private val dir: File
        get() = (context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "videos"))
            .also { it.mkdirs() }

    fun complete(videoId: String): File = File(dir, "${safe(videoId)}.mp4")

    fun partial(videoId: String): File = File(dir, "${safe(videoId)}.mp4.part")

    /** Bytes used by downloads (finished and partial). */
    fun usedBytes(): Long = dir.listFiles()?.sumOf { it.length() } ?: 0

    /** Space downloads can use, counting cached data the system would clear to make room. */
    fun freeBytes(): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(dir)) }
            .getOrElse { dir.usableSpace }
    }

    /** Every downloaded and partial file, e.g. on sign-out. */
    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }

    fun delete(videoId: String) {
        complete(videoId).delete()
        partial(videoId).delete()
    }

    /** YouTube IDs are already safe; this guards against anything else reaching a file name. */
    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")
}
