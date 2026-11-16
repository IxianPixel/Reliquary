package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.data.library.PlaylistRepository.SubscribeResult
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.model.LoadError
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val ID = "PLBCF2DAC6FFB574DE"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlaylistSubscriptionTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private lateinit var repository: PlaylistRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDatabase()
        repository = PlaylistRepository(db, libraryApiFor(server)) { 1_000L }
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    @Test fun findsThePlaylistIdInLinksAndBareIds() {
        assertEquals(ID, playlistIdFrom("https://www.youtube.com/playlist?list=$ID"))
        assertEquals(ID, playlistIdFrom("  youtube.com/watch?v=dQw4w9WgXcQ&list=$ID&index=2 "))
        assertEquals(ID, playlistIdFrom("https://m.youtube.com/playlist?list=$ID"))
        assertEquals(ID, playlistIdFrom(ID))
        assertEquals("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG", playlistIdFrom("PLx0sYbCqOb8TBPRdmBHs5Iftvv9TPboYG"))
    }

    @Test fun rejectsWhatIsNotAPlaylist() {
        assertNull(playlistIdFrom(""))
        assertNull(playlistIdFrom("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(playlistIdFrom("https://example.com/playlist?list=$ID"))
        assertNull(playlistIdFrom("https://notyoutube.com/playlist?list=$ID"))
        assertNull(playlistIdFrom("dQw4w9WgXcQ"))
        assertNull(playlistIdFrom("UC_x5XG1OV2P6uZZ5FSM9Ttw"))
        assertNull(playlistIdFrom("PLBCF2DAC6FFB574D!"))
    }

    @Test fun subscribeSendsTheIdAndMarksTheListStale() = runTest {
        db.listingDao().upsertState(ListingStateEntity(ListKeys.PLAYLISTS, nextPage = null, totalCount = 1, refreshedAtMillis = 900L))
        server.enqueue(MockResponse(code = 200, body = """{"message":"playlist subscribe task started","task_id":"t1"}"""))

        val result = repository.subscribe("https://www.youtube.com/playlist?list=$ID")

        assertEquals(SubscribeResult.Started(ID), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/playlist/", request.target)
        assertEquals("""{"data":[{"playlist_id":"$ID","playlist_subscribed":true}]}""", request.body?.utf8())
        assertEquals(0L, db.listingDao().state(ListKeys.PLAYLISTS)?.refreshedAtMillis)
    }

    @Test fun subscribeSendsNothingForABadLink() = runTest {
        assertEquals(SubscribeResult.NotAPlaylist, repository.subscribe("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(0, server.requestCount)
    }

    @Test fun subscribeReportsAServerError() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error":"nothing to subscribe"}"""))

        assertEquals(SubscribeResult.Failed(LoadError.ServerError), repository.subscribe(ID))
    }

    @Test fun awaitSubscribedWaitsUntilTheServerHasIt() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":"playlist not found"}"""))
        // Already indexed but not yet subscribed: the task hasn't run.
        server.enqueue(MockResponse(code = 200, body = playlistJson(subscribed = false)))
        server.enqueue(MockResponse(code = 200, body = playlistJson(subscribed = true)))

        assertTrue(repository.awaitSubscribed(ID))

        assertEquals(3, server.requestCount)
        assertEquals("/api/playlist/$ID/", server.takeRequest().target)
        val cached = repository.playlist(ID).first()
        assertEquals("Orbits explained", cached?.name)
        assertEquals(true, cached?.subscribed)
    }

    @Test fun awaitSubscribedGivesUpAfterAShortWait() = runTest {
        repeat(20) { server.enqueue(MockResponse(code = 404, body = """{"error":"playlist not found"}""")) }

        assertFalse(repository.awaitSubscribed(ID))
        assertNull(repository.playlist(ID).first())
    }

    @Test fun setSubscribedPostsTheStateAndCachesTheAnswer() = runTest {
        server.enqueue(MockResponse(code = 200, body = playlistJson(subscribed = true)))
        repository.refreshPlaylist(ID)
        server.takeRequest()
        server.enqueue(MockResponse(code = 200, body = playlistJson(subscribed = false)))

        assertNull(repository.setSubscribed(ID, subscribed = false))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/playlist/$ID/", request.target)
        assertEquals("""{"playlist_subscribed":false}""", request.body?.utf8())
        assertEquals(false, repository.playlist(ID).first()?.subscribed)
    }

    @Test fun setSubscribedKeepsTheCacheWhenItFails() = runTest {
        server.enqueue(MockResponse(code = 200, body = playlistJson(subscribed = true)))
        repository.refreshPlaylist(ID)
        server.enqueue(MockResponse(code = 500))

        assertEquals(LoadError.ServerError, repository.setSubscribed(ID, subscribed = false))
        assertEquals(true, repository.playlist(ID).first()?.subscribed)
    }

    private fun playlistJson(subscribed: Boolean) = """
        {
          "playlist_id": "$ID",
          "playlist_name": "Orbits explained",
          "playlist_channel": "Orbital Mechanics Club",
          "playlist_channel_id": "UC1",
          "playlist_entries": [],
          "playlist_thumbnail": "/cache/playlists/$ID.jpg",
          "playlist_type": "regular",
          "playlist_subscribed": $subscribed,
          "playlist_active": true,
          "playlist_sort_order": "top",
          "playlist_last_refresh": "2026-11-16"
        }
    """.trimIndent()
}
