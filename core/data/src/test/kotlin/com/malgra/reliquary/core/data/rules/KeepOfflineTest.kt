package com.malgra.reliquary.core.data.rules

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.data.download.DownloadRepository
import com.malgra.reliquary.core.data.fakes.FakeDownloadScheduler
import com.malgra.reliquary.core.data.fakes.FakeLibrarySyncScheduler
import com.malgra.reliquary.core.data.library.inMemoryDatabase
import com.malgra.reliquary.core.data.library.libraryApiFor
import com.malgra.reliquary.core.data.library.videoJson
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.KeepOfflineRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KeepOfflineTest {

    @get:Rule val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private val scheduler = FakeDownloadScheduler()
    private val librarySync = FakeLibrarySyncScheduler()

    /** The channel's unwatched videos, newest first, as the server lists them. */
    @Volatile private var channelVideos = listOf("a", "b", "c")

    private val rule = KeepOfflineRule.defaultFor(KeepOfflineRule.Kind.Channel, "UC1", "Orbital Mechanics Club", null)
        .copy(keepCount = 2)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                return when {
                    path == "/api/video/" && request.url.queryParameter("channel") == "UC1" ->
                        MockResponse(code = 200, body = """{"data":[${channelVideos.joinToString(",") { videoJson(it) }}],"paginate":null}""")
                    path.startsWith("/api/video/") -> MockResponse(code = 200, body = videoJson(path.removePrefix("/api/video/").trimEnd('/')))
                    else -> MockResponse(code = 404)
                }
            }
        }
        server.start()
        db = inMemoryDatabase()
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    private fun TestScope.repos(): Pair<DownloadRepository, KeepOfflineRepository> {
        val settings = AppSettings(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("settings.preferences_pb") },
        )
        val downloads = DownloadRepository(db, DownloadFiles(ApplicationProvider.getApplicationContext()), scheduler, settings)
        return downloads to KeepOfflineRepository(db, libraryApiFor(server), downloads, librarySync) { 0L }
    }

    private suspend fun ruleOwned() = db.downloadDao().byRule(rule.id).map { it.videoId }.toSet()

    @Test fun downloadsTheNewestVideosWithTheRulesNetworkSetting() = runTest {
        val (_, rules) = repos()
        rules.save(rule.copy(wifiOnly = false))
        assertEquals(1, librarySync.syncs)

        assertEquals(KeepOfflineRepository.Outcome.Done, rules.syncAll())

        assertEquals(setOf("a", "b"), ruleOwned())
        assertEquals(listOf("a" to false, "b" to false), scheduler.started)
    }

    @Test fun theWindowMovesAsNewVideosArrive() = runTest {
        val (_, rules) = repos()
        rules.save(rule)
        rules.syncAll()

        channelVideos = listOf("d", "a", "b", "c")
        rules.syncAll()

        assertEquals(setOf("d", "a"), ruleOwned())
        assertEquals(listOf("b"), scheduler.cancelled)
        assertNull(db.downloadDao().get("b"))
    }

    @Test fun theUsersOwnDownloadsAreNeverTouched() = runTest {
        val (downloads, rules) = repos()
        downloads.start("a") // the user downloaded one the rule also wants
        downloads.start("z") // and one the rule doesn't
        rules.save(rule)

        rules.syncAll()
        channelVideos = listOf("d", "e")
        rules.syncAll()

        assertNull(db.downloadDao().get("a")?.ruleId)
        assertNotNull(db.downloadDao().get("a"))
        assertNotNull(db.downloadDao().get("z"))
        assertEquals(setOf("d", "e"), ruleOwned())
    }

    @Test fun watchedVideosAreRemovedAndNotDownloadedAgain() = runTest {
        val (_, rules) = repos()
        rules.save(rule)
        rules.syncAll()
        db.downloadDao().update("a", DownloadStatus.Completed.name, 10, 10)
        // Finished on this device; the server doesn't know yet.
        db.pendingProgressDao().upsert(PendingProgressEntity("a", 0.0, watched = true, updatedAtMillis = 1, sessionStartedAtMillis = 1))

        rules.removeWatched()
        assertNull(db.downloadDao().get("a"))

        rules.syncAll() // the server still lists "a" as unwatched
        assertEquals(setOf("b", "c"), ruleOwned())
    }

    @Test fun deletingARuleKeepsItsVideos() = runTest {
        val (_, rules) = repos()
        rules.save(rule)
        rules.syncAll()

        rules.delete(rule.id)

        assertEquals(emptySet(), ruleOwned())
        assertNotNull(db.downloadDao().get("a"))
        assertNull(rules.rules().first().firstOrNull())
    }

    @Test fun offlineAsksForARetryAndRemovesNothing() = runTest {
        val (_, rules) = repos()
        db.downloadDao().upsert(DownloadEntity("a", DownloadStatus.Completed.name, 10, 10, 0, ruleId = rule.id))
        rules.save(rule)
        server.close()

        assertEquals(KeepOfflineRepository.Outcome.Retry, rules.syncAll())
        assertEquals(setOf("a"), ruleOwned())
    }
}
