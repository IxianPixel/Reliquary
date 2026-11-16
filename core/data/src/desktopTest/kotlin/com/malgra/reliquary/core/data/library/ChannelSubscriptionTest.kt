package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.ChannelEntity
import com.malgra.reliquary.core.database.entity.ListingStateEntity
import com.malgra.reliquary.core.model.LoadError
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val ID = "UC_x5XG1OV2P6uZZ5FSM9Ttw"

class ChannelSubscriptionTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private lateinit var repository: ChannelRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDatabase()
        repository = ChannelRepository(db, libraryApiFor(server)) { 1_000L }
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    @Test fun acceptsChannelLinksHandlesAndIds() {
        assertEquals(ID, ChannelReference.parse("  $ID ")?.query)
        assertEquals("@orbitalclub", ChannelReference.parse("@orbitalclub")?.query)
        assertEquals("https://www.youtube.com/channel/$ID", ChannelReference.parse("https://www.youtube.com/channel/$ID")?.query)
        // TA only treats text with a scheme as a URL.
        assertEquals("https://youtube.com/@orbitalclub", ChannelReference.parse("youtube.com/@orbitalclub")?.query)
        assertEquals("https://m.youtube.com/c/OrbitalClub/videos", ChannelReference.parse("https://m.youtube.com/c/OrbitalClub/videos")?.query)
        assertNotNull(ChannelReference.parse("https://www.youtube.com/user/orbitalclub"))
    }

    @Test fun rejectsWhatIsNotAChannel() {
        assertNull(ChannelReference.parse(""))
        assertNull(ChannelReference.parse("@"))
        assertNull(ChannelReference.parse("two words"))
        assertNull(ChannelReference.parse("dQw4w9WgXcQ"))
        assertNull(ChannelReference.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertNull(ChannelReference.parse("https://youtu.be/dQw4w9WgXcQ"))
        assertNull(ChannelReference.parse("https://www.youtube.com/playlist?list=PLBCF2DAC6FFB574DE"))
        assertNull(ChannelReference.parse("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(ChannelReference.parse("https://www.youtube.com/channel/notanid"))
        assertNull(ChannelReference.parse("https://notyoutube.com/@orbitalclub"))
        assertNull(ChannelReference.parse("https://www.youtube.com/"))
    }

    @Test fun subscribeSendsWhatWasPastedAndMarksTheListStale() = runTest {
        db.listingDao().upsertState(ListingStateEntity(ListKeys.CHANNELS, nextPage = null, totalCount = 1, refreshedAtMillis = 900L))
        server.enqueue(MockResponse(code = 200, body = """{"data":[{"channel_id":"@orbitalclub","channel_subscribed":true}]}"""))

        assertNull(repository.subscribe(ChannelReference.parse("@orbitalclub")!!))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/channel/", request.target)
        assertEquals("""{"data":[{"channel_id":"@orbitalclub","channel_subscribed":true}]}""", request.body?.utf8())
        assertEquals(0L, db.listingDao().state(ListKeys.CHANNELS)?.refreshedAtMillis)
    }

    @Test fun subscribeReportsAServerError() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"detail":"You do not have permission to perform this action."}"""))

        assertEquals(LoadError.ServerError, repository.subscribe(ChannelReference.parse(ID)!!))
    }

    @Test fun awaitSubscribedWaitsUntilTheServerHasIt() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":"channel not found: $ID"}"""))
        // Already on the server but not yet subscribed: the task hasn't run.
        server.enqueue(MockResponse(code = 200, body = channelJson(subscribed = false)))
        server.enqueue(MockResponse(code = 200, body = channelJson(subscribed = true)))

        assertEquals(ID, repository.awaitSubscribed(ChannelReference.parse("@orbitalclub")!!))

        assertEquals(3, server.requestCount)
        assertEquals("/api/channel/search/?q=%40orbitalclub", server.takeRequest().target)
        val cached = repository.channel(ID).first()
        assertEquals("Orbital Mechanics Club", cached?.name)
        assertEquals(true, cached?.subscribed)
    }

    @Test fun awaitSubscribedGivesUpAfterAShortWait() = runTest {
        repeat(20) { server.enqueue(MockResponse(code = 404, body = """{"error":"channel not found: $ID"}""")) }

        assertNull(repository.awaitSubscribed(ChannelReference.parse(ID)!!))
        assertNull(repository.channel(ID).first())
    }

    @Test fun setSubscribedPostsTheStateAndUpdatesTheCache() = runTest {
        cacheChannel(subscribed = true)
        server.enqueue(MockResponse(code = 200, body = """{"channel_subscribed":false}"""))

        assertNull(repository.setSubscribed(ID, subscribed = false))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/channel/$ID/", request.target)
        assertEquals("""{"channel_subscribed":false}""", request.body?.utf8())
        val cached = repository.channel(ID).first()
        assertEquals(false, cached?.subscribed)
        // Only the flag changes; the tabs from /nav/ stay.
        assertEquals(true, cached?.tabs?.shorts)
    }

    @Test fun setSubscribedKeepsTheCacheWhenItFails() = runTest {
        cacheChannel(subscribed = true)
        server.enqueue(MockResponse(code = 500))

        assertEquals(LoadError.ServerError, repository.setSubscribed(ID, subscribed = false))
        assertEquals(true, repository.channel(ID).first()?.subscribed)
    }

    private suspend fun cacheChannel(subscribed: Boolean) = db.channelDao().upsert(
        listOf(
            ChannelEntity(
                id = ID,
                name = "Orbital Mechanics Club",
                description = null,
                thumbnailUrl = null,
                bannerUrl = null,
                subscribed = subscribed,
                subscriberCount = 0,
                hasVideos = true,
                hasStreams = false,
                hasShorts = true,
                hasPlaylists = false,
                fetchedAtMillis = 900L,
            ),
        ),
    )

    private fun channelJson(subscribed: Boolean) = """
        {
          "channel_id": "$ID",
          "channel_name": "Orbital Mechanics Club",
          "channel_active": true,
          "channel_subscribed": $subscribed,
          "channel_thumb_url": "/cache/channels/$ID.jpg"
        }
    """.trimIndent()
}
