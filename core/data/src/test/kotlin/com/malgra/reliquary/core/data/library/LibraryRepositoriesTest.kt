package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.SearchResults
import com.malgra.reliquary.core.model.VideoType
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LibraryRepositoriesTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = inMemoryDatabase()
    }

    @After fun tearDown() {
        db.close()
        server.close()
    }

    @Test fun continueWatchingResolvesUrlsAndKeepsPosition() = runTest {
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }
        server.enqueue(MockResponse(code = 200, body = """{"data":[${videoJson("a", position = 421.0)}],"paginate":null}"""))

        assertNull(videos.refreshContinueWatching())

        assertEquals("/api/video/?watch=continue", server.takeRequest().target)
        val video = videos.continueWatching().first().single()
        assertEquals(server.url("/media/thumb/video/a.jpg").toString(), video.thumbnailUrl)
        assertEquals(server.url("/media/stream/UC1/a.mp4").toString(), video.mediaUrl)
        assertEquals(842.seconds, video.duration)
        assertEquals(421.seconds, video.position)
        assertEquals(0.5f, video.progress)
        assertEquals(Instant.parse("2026-03-03T00:00:00Z"), video.published)
        assertEquals(VideoType.Videos, video.type)
    }

    @Test fun continueWatchingAcceptsTheBareEmptyArray() = runTest {
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }
        server.enqueue(MockResponse(code = 200, body = """{"data":[${videoJson("a", position = 10.0)}],"paginate":null}"""))
        videos.refreshContinueWatching()
        server.enqueue(MockResponse(code = 200, body = "[]"))

        assertNull(videos.refreshContinueWatching())
        assertTrue(videos.continueWatching().first().isEmpty())
    }

    @Test fun searchFallsBackToTheCacheWhenOffline() = runTest {
        val library = libraryApiFor(server)
        val videos = VideoRepository(db, library) { 0L }
        val search = SearchRepository(db, library) { 0L }
        server.enqueue(
            MockResponse(code = 200, body = """{"data":[${videoJson("a", title = "Why the Moon drifts")}, ${videoJson("b", title = "Tidal locking")}]}"""),
        )
        videos.refreshContinueWatching()

        server.close() // nothing listens on the port any more

        val result = assertIs<LoadResult.Ok<SearchResults>>(search.search("moon"))
        assertTrue(result.value.fromCache)
        assertEquals(listOf("a"), result.value.videos.map { it.id })
    }

    @Test fun searchReportsServerErrors() = runTest {
        val search = SearchRepository(db, libraryApiFor(server)) { 0L }
        server.enqueue(MockResponse(code = 500))

        assertEquals(LoadResult.Failed(LoadError.ServerError), search.search("moon"))
    }

    @Test fun searchStoresHits() = runTest {
        val search = SearchRepository(db, libraryApiFor(server)) { 0L }
        server.enqueue(
            MockResponse(
                code = 200,
                body = """
                    {"results": {
                      "video_results": [${videoJson("a")}],
                      "channel_results": [{"channel_id": "UC1", "channel_name": "Orbital Mechanics Club", "channel_thumb_url": "/media/thumb/channel/UC1/thumb.jpg", "_index": "ta_channel"}],
                      "playlist_results": [],
                      "fulltext_results": []
                    }, "queryType": "simple"}
                """.trimIndent(),
            ),
        )

        val result = assertIs<LoadResult.Ok<SearchResults>>(search.search(" moon "))

        assertEquals("/api/search/?query=moon", server.takeRequest().target)
        assertFalse(result.value.fromCache)
        assertEquals(listOf("UC1"), result.value.channels.map { it.id })
        assertEquals("a", db.videoDao().observe("a").first()?.video?.id)
    }

    @Test fun channelRefreshStoresTabs() = runTest {
        // The channel and its nav are fetched in parallel, so answer by path.
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.url.encodedPath) {
                "/api/channel/UC1/" -> MockResponse(
                    code = 200,
                    body = """{"channel_id":"UC1","channel_name":"Orbital Mechanics Club","channel_subscribed":true}""",
                )
                "/api/channel/UC1/nav/" -> MockResponse(
                    code = 200,
                    body = """{"has_pending":false,"has_ignored":false,"has_playlists":true,"has_videos":true,"has_streams":false,"has_shorts":true}""",
                )
                else -> MockResponse(code = 404)
            }
        }
        val channels = ChannelRepository(db, libraryApiFor(server)) { 0L }

        assertNull(channels.refreshChannel("UC1"))

        val channel = channels.channel("UC1").first()
        assertEquals(true, channel?.subscribed)
        assertEquals(true, channel?.tabs?.shorts)
        assertEquals(false, channel?.tabs?.streams)
        assertEquals(true, channel?.tabs?.playlists)
    }

    @Test fun playlistQueueFetchesEveryPageInOrder() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = when (request.url.queryParameter("page")) {
                "1" -> MockResponse(code = 200, body = videoPage(listOf("p1", "p2"), page = 1, next = true))
                "2" -> MockResponse(code = 200, body = videoPage(listOf("p3"), page = 2, next = false))
                else -> MockResponse(code = 404)
            }
        }
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        assertEquals(listOf("p1", "p2", "p3"), videos.playlistQueue("PL1").map { it.id })
        assertEquals(listOf("p1", "p2", "p3"), videos.playlistListing("PL1").first().map { it.id })
    }

    @Test fun playlistQueueOfflineReturnsWhatIsCached() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoPage(listOf("p1", "p2"), page = 1, next = false)))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }
        videos.playlistQueue("PL1")
        server.close()

        assertEquals(listOf("p1", "p2"), videos.playlistQueue("PL1").map { it.id })
    }
}
