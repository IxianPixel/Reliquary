package com.malgra.reliquary.core.database

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

actual suspend fun <R> ReliquaryDatabase.transaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
