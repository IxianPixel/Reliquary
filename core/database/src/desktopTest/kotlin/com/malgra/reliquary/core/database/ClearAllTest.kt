package com.malgra.reliquary.core.database

import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ClearAllTest {

    @get:Rule val tmp = TemporaryFolder()

    // Real time, not runTest: the point is that a live query notices the clear.
    @Test fun emptiesEveryTableAndLiveQueriesSeeIt() = runBlocking {
        val db = inMemoryReliquaryDatabase()
        try {
            val count = MutableStateFlow<Int?>(null)
            val collector = launch { db.pendingProgressDao().observeCount().collect { count.value = it } }
            db.pendingProgressDao().upsert(PendingProgressEntity("v", 42.0, null, 1, 1))
            withTimeout(5.seconds) { count.filterNotNull().first { it == 1 } }

            db.clearAll()

            withTimeout(5.seconds) { count.filterNotNull().first { it == 0 } }
            assertEquals(emptyList(), db.pendingProgressDao().all())
            collector.cancel()
        } finally {
            db.close()
        }
    }

    @Test fun theDatabaseStillOpensAfterwards() = runBlocking {
        val file = tmp.root.resolve("reliquary.db")
        reliquaryDatabase(file).run {
            pendingProgressDao().upsert(PendingProgressEntity("v", 42.0, null, 1, 1))
            clearAll()
            close()
        }

        val reopened = reliquaryDatabase(file)
        try {
            assertEquals(emptyList(), reopened.pendingProgressDao().all())
            reopened.pendingProgressDao().upsert(PendingProgressEntity("w", 10.0, null, 2, 2))
            assertEquals(listOf("w"), reopened.pendingProgressDao().all().map { it.videoId })
        } finally {
            reopened.close()
        }
    }
}
