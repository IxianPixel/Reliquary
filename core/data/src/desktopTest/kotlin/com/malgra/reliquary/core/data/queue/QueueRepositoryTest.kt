package com.malgra.reliquary.core.data.queue

import com.malgra.reliquary.core.data.library.LoadResult
import com.malgra.reliquary.core.data.library.libraryApiFor
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.model.VideoType
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class QueueRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var queue: QueueRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        queue = QueueRepository(libraryApiFor(server))
    }

    @After fun tearDown() {
        server.close()
    }

    @Test fun pageMapsItemsAndDropsThumbnailsFromOtherOrigins() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"data":[
                    ${itemJson("a", thumb = "/cache/download/a.jpg", duration = "1:02:03", message = "Video unavailable")},
                    ${itemJson("b", thumb = "https://i.ytimg.com/vi/b/hqdefault.jpg", duration = "NA", status = "ignore")}
                ],"paginate":{"current_page":1,"last_page":2,"total_hits":60}}""",
            ),
        )

        val page = assertIs<LoadResult.Ok<QueuePage>>(queue.page(ignored = false)).value

        assertEquals("/api/download/?filter=pending&page=1", server.takeRequest().target)
        assertEquals(2, page.next)
        assertEquals(60, page.total)
        val (a, b) = page.items
        assertEquals(server.url("/cache/download/a.jpg").toString(), a.thumbnailUrl)
        assertEquals(3723.seconds, a.duration)
        assertEquals(Instant.parse("2026-03-03T00:00:00Z"), a.published)
        assertEquals(VideoType.Shorts, a.type)
        assertEquals("Video unavailable", a.error)
        assertEquals(false, a.ignored)
        assertNull(b.thumbnailUrl)
        assertEquals(0.seconds, b.duration)
        assertTrue(b.ignored)
    }

    @Test fun anEmptyQueueIsA404() = runTest {
        server.enqueue(MockResponse(code = 404, body = """{"error":"not found"}"""))

        val page = assertIs<LoadResult.Ok<QueuePage>>(queue.page(ignored = true)).value

        assertEquals("/api/download/?filter=ignore&page=1", server.takeRequest().target)
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }

    @Test fun addSendsEachEntryAsPending() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"message":"add to queue task started","task_id":"t1"}"""))

        assertIs<LoadResult.Ok<Unit>>(queue.add(listOf("https://youtu.be/a", "UC1"), downloadNow = false))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/download/", request.target)
        assertEquals(
            """{"data":[{"youtube_id":"https://youtu.be/a","status":"pending"},{"youtube_id":"UC1","status":"pending"}]}""",
            request.body?.utf8(),
        )
    }

    @Test fun itemChangesPostTheirStatus() = runTest {
        repeat(3) { server.enqueue(MockResponse(code = 200, body = """{"status":"x"}""")) }

        queue.downloadNext("a")
        queue.ignore("b")
        queue.restore("c")

        listOf("a" to "priority", "b" to "ignore", "c" to "pending").forEach { (id, status) ->
            val request = server.takeRequest()
            assertEquals("POST /api/download/$id/", "${request.method} ${request.target}")
            assertEquals("""{"status":"$status"}""", request.body?.utf8())
        }
    }

    @Test fun removeAndClearDelete() = runTest {
        repeat(2) { server.enqueue(MockResponse(code = 204)) }

        assertIs<LoadResult.Ok<Unit>>(queue.remove("a"))
        assertIs<LoadResult.Ok<Unit>>(queue.clear(ignored = true))

        assertEquals("DELETE /api/download/a/", server.takeRequest().let { "${it.method} ${it.target}" })
        assertEquals("DELETE /api/download/?filter=ignore", server.takeRequest().let { "${it.method} ${it.target}" })
    }

    @Test fun tasksStartByName() = runTest {
        repeat(2) { server.enqueue(MockResponse(code = 200, body = """{"task_id":"t1"}""")) }

        queue.rescanSubscriptions()
        queue.downloadAll()

        assertEquals("POST /api/task/by-name/update_subscribed/", server.takeRequest().let { "${it.method} ${it.target}" })
        assertEquals("POST /api/task/by-name/download_pending/", server.takeRequest().let { "${it.method} ${it.target}" })
    }

    @Test fun tasksReadDownloadNotifications() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """[{"id":"n1","title":"Downloading","group":"download:run","api_start":true,"api_stop":true,
                    "level":"info","messages":["Processing 2/5"],"progress":0.4}]""",
            ),
        )

        val tasks = assertIs<LoadResult.Ok<List<com.malgra.reliquary.core.model.ServerTask>>>(queue.tasks()).value

        assertEquals("/api/notification/?filter=download", server.takeRequest().target)
        assertEquals("Downloading", tasks.single().title)
        assertEquals(listOf("Processing 2/5"), tasks.single().messages)
        assertEquals(0.4f, tasks.single().progress)
    }

    @Test fun aRejectedChangeIsAServerError() = runTest {
        server.enqueue(MockResponse(code = 403, body = """{"detail":"forbidden"}"""))

        assertEquals(LoadError.ServerError, assertIs<LoadResult.Failed>(queue.ignore("a")).error)
    }

    private fun itemJson(
        id: String,
        thumb: String,
        duration: String,
        status: String = "pending",
        message: String? = null,
    ) = """{"youtube_id":"$id","title":"Video $id","channel_id":"UC1","channel_name":"Orbital Mechanics Club",
        "channel_indexed":true,"duration":"$duration","published":"2026-03-03T00:00:00+00:00","status":"$status",
        "timestamp":1772496000,"vid_thumb_url":"$thumb","vid_type":"shorts"${message?.let { ""","message":"$it"""" }.orEmpty()}}"""
}
