package com.malgra.reliquary.core.data.progress

import com.malgra.reliquary.core.data.fakes.FakeSyncScheduler
import com.malgra.reliquary.core.data.library.VideoRepository
import com.malgra.reliquary.core.data.library.inMemoryDatabase
import com.malgra.reliquary.core.data.library.libraryApiFor
import com.malgra.reliquary.core.data.library.videoJson
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.PendingProgressEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressSyncTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private var now = 2_000_000L
    private val scheduler = FakeSyncScheduler()
    private val requests = CopyOnWriteArrayList<String>()

    /** What GET /api/video/v/ answers; POSTs update it the way TA would. */
    @Volatile private var serverVideo = videoJson("v", position = 30.0)

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requests += "${request.method} ${request.url.encodedPath} ${request.body?.utf8().orEmpty()}".trim()
                return when ("${request.method} ${request.url.encodedPath}") {
                    "GET /api/video/v/" -> MockResponse(code = 200, body = serverVideo)
                    "POST /api/video/v/progress/" -> {
                        val position = Regex("\"position\":([0-9.]+)").find(request.body!!.utf8())!!.groupValues[1]
                        serverVideo = videoJson("v", position = position.toDouble())
                        MockResponse(code = 200, body = """{"position": $position}""")
                    }
                    "DELETE /api/video/v/progress/" -> MockResponse(code = 204)
                    "POST /api/watched/" -> {
                        serverVideo = videoJson("v", watched = true, watchedDate = now / 1000)
                        MockResponse(code = 200, body = "{}")
                    }
                    else -> MockResponse(code = 404, body = """{"detail":"Not found."}""")
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

    private fun progress() = ProgressRepository(db, scheduler) { now }
    private fun syncer() = ProgressSyncer(db, libraryApiFor(server)) { now }
    private fun videos() = VideoRepository(db, libraryApiFor(server)) { now }

    @Test fun offlinePositionShowsLocallyThenSyncs() = runTest {
        videos().refreshVideo("v")
        progress().recordPosition("v", 180.seconds, sessionStartedAtMillis = now)

        val local = videos().video("v").first()!!
        assertEquals(180.seconds, local.position)
        assertTrue(local.syncPending)

        assertEquals(ProgressSyncer.Outcome.Done, syncer().syncAll())

        assertTrue(requests.any { it.startsWith("POST /api/video/v/progress/") && it.contains("180") })
        assertNull(db.pendingProgressDao().get("v"))
        val synced = videos().video("v").first()!!
        assertEquals(180.seconds, synced.position) // now the server's value
        assertEquals(false, synced.syncPending)
    }

    @Test fun serverMarkedWatchedAfterTheSessionWins() = runTest {
        val sessionStart = now
        progress().recordPosition("v", 180.seconds, sessionStart)
        serverVideo = videoJson("v", watched = true, watchedDate = sessionStart / 1000 + 600)

        assertEquals(ProgressSyncer.Outcome.Done, syncer().syncAll())

        assertTrue(requests.none { it.startsWith("POST") }, "nothing pushed: $requests")
        assertNull(db.pendingProgressDao().get("v"))
        assertEquals(true, videos().video("v").first()?.watched)
    }

    @Test fun markWatchedIsPushed() = runTest {
        progress().setWatched("v", true)
        assertEquals(1, scheduler.requests)
        assertEquals(true, videos().video("v").first()?.watched ?: db.pendingProgressDao().get("v")?.watched)

        syncer().syncAll()

        assertTrue(requests.any { it.startsWith("POST /api/watched/") && it.contains("\"is_watched\":true") })
        assertEquals(true, videos().video("v").first()?.watched)
    }

    @Test fun offlineKeepsTheRowAndAsksForARetry() = runTest {
        progress().recordPosition("v", 180.seconds, now)
        server.close()

        assertEquals(ProgressSyncer.Outcome.Retry, syncer().syncAll())
        assertEquals(180.0, db.pendingProgressDao().get("v")?.positionSeconds)
    }

    @Test fun aVideoGoneFromTheArchiveIsDropped() = runTest {
        progress().recordPosition("gone", 180.seconds, now)

        assertEquals(ProgressSyncer.Outcome.Done, syncer().syncAll())
        assertNull(db.pendingProgressDao().get("gone"))
    }

    @Test fun aChangeMadeDuringSyncIsKept() = runTest {
        val dao = db.pendingProgressDao()
        dao.upsert(PendingProgressEntity("v", 180.0, null, updatedAtMillis = 10, sessionStartedAtMillis = 0))
        // Sync read the row at updatedAt = 10; the player saved again before it finished.
        dao.upsert(PendingProgressEntity("v", 240.0, null, updatedAtMillis = 20, sessionStartedAtMillis = 0))

        assertEquals(0, dao.deleteIfUnchanged("v", 10))
        assertEquals(240.0, dao.get("v")?.positionSeconds)
    }

    @Test fun markWatchedDuringPlaybackSurvivesTheNextSave() = runTest {
        val sessionStart = now
        now += 60_000
        val repo = progress()
        repo.setWatched("v", true)
        repo.recordPosition("v", 70.seconds, sessionStart)
        assertEquals(true, db.pendingProgressDao().get("v")?.watched)
    }

    @Test fun replayingAfterMarkingWatchedDropsTheMarkButKeepsAnUnwatch() = runTest {
        val repo = progress()
        repo.setWatched("v", true)
        now += 1_000
        repo.recordPosition("v", 60.seconds, now)
        assertNull(db.pendingProgressDao().get("v")?.watched)

        repo.setWatched("v", false)
        repo.recordPosition("v", 60.seconds, now)
        assertEquals(false, db.pendingProgressDao().get("v")?.watched)
    }

    @Test fun resumeStartsOverWhenWatchedOrNearlyDone() = runTest {
        videos().refreshVideo("v") // server position 30 s of 842 s
        val repo = progress()
        assertEquals(30.seconds, repo.resumePosition("v"))

        repo.recordPosition("v", 840.seconds, now)
        assertEquals(Duration.ZERO, repo.resumePosition("v"))

        repo.setWatched("v", true)
        assertEquals(Duration.ZERO, repo.resumePosition("v"))
    }
}
