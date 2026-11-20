package com.malgra.reliquary.core.database

/** Runs [block] in one write transaction; DAO calls inside it join the transaction. */
expect suspend fun <R> ReliquaryDatabase.transaction(block: suspend () -> R): R
