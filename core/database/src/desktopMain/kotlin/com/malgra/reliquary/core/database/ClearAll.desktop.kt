package com.malgra.reliquary.core.database

import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/**
 * Room has no clearAllTables() off Android, so this deletes from each table
 * in one transaction, like Room's: foreign keys are deferred (none today) so
 * the order doesn't matter, and Room's own room_master_table (the schema's
 * identity hash) is kept so the database still opens. Room's invalidation log
 * is a temp table, so it isn't in sqlite_master.
 */
actual suspend fun ReliquaryDatabase.clearAll() = useWriterConnection { connection ->
    connection.immediateTransaction {
        execSQL("PRAGMA defer_foreign_keys = TRUE")
        val tables = usePrepared(
            "SELECT name FROM sqlite_master WHERE type = 'table' " +
                "AND name NOT LIKE 'sqlite_%' AND name NOT IN ('room_master_table', 'android_metadata')",
        ) { statement ->
            buildList { while (statement.step()) add(statement.getText(0)) }
        }
        tables.forEach { execSQL("DELETE FROM `$it`") }
    }
}
