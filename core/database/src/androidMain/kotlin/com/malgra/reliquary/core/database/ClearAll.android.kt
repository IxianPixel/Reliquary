package com.malgra.reliquary.core.database

actual suspend fun ReliquaryDatabase.clearAll() = clearAllTables()
