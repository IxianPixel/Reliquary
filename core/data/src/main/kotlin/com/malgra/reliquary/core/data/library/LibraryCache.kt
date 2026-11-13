package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.data.download.DownloadScheduler
import com.malgra.reliquary.core.database.ReliquaryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** The cached library of the signed-in server. */
interface LibraryCache {
    /** Drops everything, e.g. when the user signs out or switches server. */
    suspend fun clear()
}

/** Also removes downloads: they belong to the account that made them. */
@Singleton
class RoomLibraryCache @Inject constructor(
    private val db: ReliquaryDatabase,
    private val downloads: DownloadScheduler,
    private val files: DownloadFiles,
) : LibraryCache {
    override suspend fun clear() = withContext(Dispatchers.IO) {
        downloads.cancelAll()
        db.clearAllTables()
        files.deleteAll()
    }
}
