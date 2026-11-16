package com.malgra.reliquary.core.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import java.io.File

/** The desktop database at [file]. The JVM has no system SQLite, so it bundles its own. */
fun reliquaryDatabase(file: File): ReliquaryDatabase {
    file.parentFile?.mkdirs()
    return Room.databaseBuilder<ReliquaryDatabase>(name = file.absolutePath)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}

/** A throwaway database for tests. */
fun inMemoryReliquaryDatabase(): ReliquaryDatabase =
    Room.inMemoryDatabaseBuilder<ReliquaryDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
