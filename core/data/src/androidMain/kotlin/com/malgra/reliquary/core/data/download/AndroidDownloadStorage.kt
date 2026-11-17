package com.malgra.reliquary.core.data.download

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The app's own external files folder: no permission needed, removed with the app. */
@Singleton
class AndroidDownloadStorage @Inject constructor(@ApplicationContext private val context: Context) : DownloadStorage {

    override fun directory(): File =
        context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: File(context.filesDir, "videos")

    /** Counts cached data the system would clear to make room. */
    override fun freeBytes(dir: File): Long {
        val storage = context.getSystemService(StorageManager::class.java)
        return runCatching { storage.getAllocatableBytes(storage.getUuidForPath(dir)) }
            .getOrElse { dir.usableSpace }
    }
}
