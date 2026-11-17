package com.malgra.reliquary.core.database

import androidx.room.withTransaction

actual suspend fun <R> ReliquaryDatabase.transaction(block: suspend () -> R): R = withTransaction(block)
