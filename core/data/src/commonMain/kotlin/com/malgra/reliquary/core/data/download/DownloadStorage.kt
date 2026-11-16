package com.malgra.reliquary.core.data.download

import java.io.File

/** Where the platform keeps downloaded videos, and how much room is left there. */
interface DownloadStorage {
    /** The downloads folder; [DownloadFiles] creates it if it's missing. */
    fun directory(): File

    /** Space downloads can use in [dir]. */
    fun freeBytes(dir: File): Long
}

/** Downloads in a fixed folder, e.g. on desktop or in tests. */
class FolderDownloadStorage(private val folder: File) : DownloadStorage {
    override fun directory(): File = folder

    override fun freeBytes(dir: File): Long = dir.usableSpace
}
