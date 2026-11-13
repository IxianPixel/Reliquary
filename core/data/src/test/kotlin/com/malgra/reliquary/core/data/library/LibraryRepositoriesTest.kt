package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.model.Comment
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.SearchResults
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.VideoType
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
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

    @Test fun subtitlesAreKeptWithAbsoluteUrls() = runTest {
        val subtitles = """[
            {"lang":"en","name":"English","ext":"vtt","source":"user","media_url":"/media/subtitle/a.en.vtt"},
            {"lang":"de","name":"German","ext":"vtt","source":"auto","media_url":"/media/subtitle/a.de.vtt"}
        ]"""
        server.enqueue(MockResponse(code = 200, body = videoJson("a", subtitles = subtitles)))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        videos.refreshVideo("a")

        val tracks = videos.get("a")!!.subtitles
        assertEquals(listOf("en.user", "de.auto"), tracks.map { it.key })
        assertEquals(server.url("/media/subtitle/a.en.vtt").toString(), tracks.first().url)
        assertEquals(listOf("English", "German"), tracks.map { it.name })
    }

    @Test fun aSavedThumbnailReplacesTheServersOnceDownloaded() = runTest {
        server.enqueue(MockResponse(code = 200, body = videoJson("a")))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }
        videos.refreshVideo("a")
        val serverThumbnail = videos.get("a")!!.thumbnailUrl
        assertTrue(serverThumbnail!!.startsWith("http"))

        db.downloadDao().upsert(DownloadEntity("a", DownloadStatus.Completed.name, 1, 1, createdAtMillis = 0))
        assertEquals(serverThumbnail, videos.get("a")!!.thumbnailUrl) // nothing saved yet

        db.downloadDao().setThumbnail("a", "/files/a.jpg")
        assertEquals("file:///files/a.jpg", videos.get("a")!!.thumbnailUrl)
    }

    @Test fun sponsorSegmentsKeepOnlySkipsInOrder() = runTest {
        val sponsorblock = """{
            "is_enabled": true,
            "segments": [
                {"segment": [300.5, 320.0], "category": "outro", "actionType": "skip", "UUID": "b"},
                {"segment": [12.0, 45.25], "category": "sponsor", "actionType": "skip", "UUID": "a"},
                {"segment": [90.0, 90.0], "category": "poi_highlight", "actionType": "poi", "UUID": "c"},
                {"segment": [50.0, 40.0], "category": "sponsor", "actionType": "skip", "UUID": "d"}
            ]
        }"""
        server.enqueue(MockResponse(code = 200, body = videoJson("a", sponsorblock = sponsorblock)))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        videos.refreshVideo("a")

        val segments = videos.get("a")!!.sponsorSegments
        assertEquals(listOf("sponsor", "outro"), segments.map { it.category })
        assertEquals(12.seconds, segments[0].start)
        assertEquals(45.25.seconds, segments[0].end)
    }

    @Test fun sponsorSegmentsAreIgnoredWhenTheServerHasSponsorBlockOff() = runTest {
        val sponsorblock = """{"is_enabled": false, "segments": [
            {"segment": [12.0, 45.0], "category": "sponsor", "actionType": "skip", "UUID": "a"}
        ]}"""
        server.enqueue(MockResponse(code = 200, body = videoJson("a", sponsorblock = sponsorblock)))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        videos.refreshVideo("a")

        assertTrue(videos.get("a")!!.sponsorSegments.isEmpty())
    }

    @Test fun commentsComeBackAsThreads() = runTest {
        // One nested reply (as the server sends them) and one flat, plus a reply whose comment is missing.
        val comments = """[
            {"comment_id": "c1", "comment_text": "First!", "comment_author": "@ana", "comment_timestamp": 1700000000,
             "comment_likecount": 12, "comment_is_favorited": true, "comment_author_is_uploader": false, "comment_parent": "root",
             "comment_replies": [
                {"comment_id": "r2", "comment_text": "Later", "comment_author": "@bo", "comment_timestamp": 1700000200, "comment_parent": "c1"}
             ]},
            {"comment_id": "r1", "comment_text": "Earlier", "comment_author": "@chan", "comment_timestamp": 1700000100,
             "comment_author_is_uploader": true, "comment_parent": "c1"},
            {"comment_id": "c2", "comment_text": "Second", "comment_author": "@di", "comment_likecount": null, "comment_parent": "root"},
            {"comment_id": "r9", "comment_text": "Orphan", "comment_author": "@ed", "comment_parent": "gone"}
        ]"""
        server.enqueue(MockResponse(code = 200, body = comments))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        val threads = assertIs<LoadResult.Ok<List<Comment>>>(videos.comments("a")).value

        assertEquals(listOf("c1", "c2", "r9"), threads.map { it.id })
        assertEquals(listOf("r1", "r2"), threads[0].replies.map { it.id }) // oldest first
        assertTrue(threads[0].replies[0].byUploader)
        assertTrue(threads[0].favorited)
        assertEquals(12, threads[0].likeCount)
        assertNull(threads[1].likeCount)
        assertEquals("/api/video/a/comment/", server.takeRequest().url.encodedPath)
    }

    @Test fun aVideoWithNoSavedCommentsHasNone() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error": "video not found"}"""))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        assertEquals(emptyList(), assertIs<LoadResult.Ok<List<Comment>>>(videos.comments("a")).value)
    }

    @Test fun commentsReportServerErrors() = runTest {
        server.enqueue(MockResponse(code = 500))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        assertEquals(LoadError.ServerError, assertIs<LoadResult.Failed>(videos.comments("a")).error)
    }

    @Test fun similarVideosAreCachedAndLeaveOutTheVideoItself() = runTest {
        server.enqueue(MockResponse(code = 200, body = "[${videoJson("b")}, ${videoJson("a")}, ${videoJson("c")}]"))
        val videos = VideoRepository(db, libraryApiFor(server)) { 0L }

        val similar = assertIs<LoadResult.Ok<List<Video>>>(videos.similar("a")).value

        assertEquals(listOf("b", "c"), similar.map { it.id })
        assertEquals("Video b", videos.get("b")?.title) // cached
        assertEquals("/api/video/a/similar/", server.takeRequest().url.encodedPath)
    }
}
