package com.malgra.reliquary.core.database

/** Deletes every row in every table, e.g. on sign-out. Observed queries re-emit. Call it off the main thread. */
expect suspend fun ReliquaryDatabase.clearAll()
