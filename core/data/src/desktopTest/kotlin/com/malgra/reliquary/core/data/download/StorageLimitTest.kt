package com.malgra.reliquary.core.data.download

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.malgra.reliquary.core.data.fakes.FakeDownloadScheduler
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.database.entity.KeepOfflineRuleEntity
import com.malgra.reliquary.core.database.entity.VideoEntity
import com.malgra.reliquary.core.database.inMemoryReliquaryDatabase
import com.malgra.reliquary.core.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StorageLimitTest {

    @get:Rule val tmp = TemporaryFolder()

    private val db: ReliquaryDatabase = inMemoryReliquaryDatabase()
    private val scheduler = FakeDownloadScheduler()
    private val files by lazy { DownloadFiles(FolderDownloadStorage(tmp.newFolder("downloads"))) }

    @After fun tearDown() = db.close()

    private fun TestScope.settings() = AppSettings(
        PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("settings.preferences_pb") },
    )

    private suspend fun video(id: String, size: Long, state: DownloadStatus = DownloadStatus.Queued, written: Long = 0, ruleId: String? = null) {
        db.videoDao().upsert(listOf(videoEntity(id, size)))
        db.downloadDao().upsert(DownloadEntity(id, state.name, written, if (written > 0) size else 0, 0, ruleId = ruleId))
        if (written > 0) files.partial(id).writeBytes(ByteArray(written.toInt()))
    }

    private suspend fun state(id: String) = db.downloadDao().get(id)?.state

    @Test fun withoutALimitEveryDownloadFits() = runTest {
        val limit = StorageLimit(db, files, settings())
        video("a", 5_000)

        assertTrue(limit.reserve("a", 5_000))
    }

    @Test fun aDownloadThatFitsIsMarkedDownloading() = runTest {
        val settings = settings()
        settings.setStorageLimitBytes(1_000)
        video("a", 800)

        assertTrue(StorageLimit(db, files, settings).reserve("a", 800))
        assertEquals(DownloadStatus.Downloading.name, state("a"))
    }

    @Test fun aDownloadThatDoesNotFitWaits() = runTest {
        val settings = settings()
        settings.setStorageLimitBytes(1_000)
        video("a", 1_200)

        assertFalse(StorageLimit(db, files, settings).reserve("a", 1_200))
        assertEquals(DownloadStatus.OverStorageLimit.name, state("a"))
    }

    @Test fun downloadsAlreadyRunningCountInFull() = runTest {
        val settings = settings()
        settings.setStorageLimitBytes(1_000)
        video("running", 600, DownloadStatus.Downloading, written = 100) // 100 on disk, 500 still to come
        video("a", 500)

        // 100 + 500 + 500 is over 1,000, though only 100 bytes are on disk so far.
        assertFalse(StorageLimit(db, files, settings).reserve("a", 500))
    }

    @Test fun aResumedDownloadCountsItsPartialFileOnce() = runTest {
        val settings = settings()
        settings.setStorageLimitBytes(600)
        video("a", 500, written = 300)

        assertTrue(StorageLimit(db, files, settings).reserve("a", 500))
    }

    @Test fun removingADownloadStartsTheWaitingOnesAgain() = runTest {
        val settings = settings()
        val downloads = DownloadRepository(db, files, scheduler, settings)
        video("done", 100, DownloadStatus.Completed)
        video("waiting", 900, DownloadStatus.OverStorageLimit)

        downloads.remove("done")

        assertEquals(DownloadStatus.Queued.name, state("waiting"))
        assertEquals(listOf("waiting" to true), scheduler.started)
    }

    @Test fun changingTheLimitStartsTheWaitingOnesWithTheirOwnNetworkRule() = runTest {
        val settings = settings()
        val downloads = DownloadRepository(db, files, scheduler, settings)
        db.keepOfflineRuleDao().upsert(
            KeepOfflineRuleEntity("channel:c", "channel", "c", "C", null, 5, false, wifiOnly = false, removeWhenWatched = false, createdAtMillis = 0),
        )
        video("mine", 900, DownloadStatus.OverStorageLimit)
        video("rule's", 900, DownloadStatus.OverStorageLimit, ruleId = "channel:c")

        downloads.setStorageLimit(5_000)

        assertEquals(5_000, settings.storageLimitBytes.first())
        assertEquals(setOf("mine" to true, "rule's" to false), scheduler.started.toSet())
    }

    private fun videoEntity(id: String, size: Long) = VideoEntity(
        id = id, title = id, channelId = "c", channelName = "C", description = null, type = "videos",
        durationSeconds = 60, publishedEpochSeconds = null, addedEpochSeconds = null, mediaUrl = "/media/$id.mp4",
        mediaSizeBytes = size, thumbnailUrl = null, watched = false, watchedEpochSeconds = null,
        positionSeconds = 0.0, fetchedAtMillis = 0,
    )
}
