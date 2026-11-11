package com.malgra.reliquary.core.data.library

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

@Singleton
class RoomLibraryCache @Inject constructor(private val db: ReliquaryDatabase) : LibraryCache {
    override suspend fun clear() = withContext(Dispatchers.IO) { db.clearAllTables() }
}
