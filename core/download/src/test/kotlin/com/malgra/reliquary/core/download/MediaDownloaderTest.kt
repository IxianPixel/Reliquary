package com.malgra.reliquary.core.download

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malgra.reliquary.core.data.download.DownloadFiles
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.database.entity.DownloadEntity
import com.malgra.reliquary.core.model.DownloadStatus
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaDownloaderTest {

    private lateinit var server: MockWebServer
    private lateinit var db: ReliquaryDatabase
    private val files = DownloadFiles(ApplicationProvider.getApplicationContext())
    private val video = ByteArray(10_000) { (it % 251).toByte() }

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReliquaryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        files.delete("v")
    }

    @After fun tearDown() {
        db.close()
        server.close()
        files.delete("v")
    }

    private fun downloader() = MediaDownloader(OkHttpClient(), files, db.downloadDao())

    private suspend fun queued() =
        db.downloadDao().upsert(DownloadEntity("v", DownloadStatus.Queued.name, 0, 0, createdAtMillis = 0))

    private suspend fun run(mediaSize: Long = video.size.toLong()) =
        downloader().download("v", server.url("/media/stream/c/v.mp4").toString(), mediaSize)

    private fun full() = MockResponse.Builder().code(200).body(Buffer().write(video)).build()

    private fun rest(from: Int, total: Int = video.size) = MockResponse.Builder()
        .code(206)
        .addHeader("Content-Range", "bytes $from-${total - 1}/$total")
        .body(Buffer().write(video, from, video.size - from))
        .build()

    @Test fun downloadsTheWholeFile() = runTest {
        queued()
        server.enqueue(full())

        assertEquals(MediaDownloader.Outcome.Done, run())

        assertContentEquals(video, files.complete("v").readBytes())
        assertFalse(files.partial("v").exists())
        val row = db.downloadDao().get("v")!!
        assertEquals(DownloadStatus.Completed.name, row.state)
        assertEquals(video.size.toLong(), row.totalBytes)
        assertNull(server.takeRequest().headers["Range"])
    }

    @Test fun resumesAfterAnInterruptionWithoutIfRange() = runTest {
        queued()
        // The connection drops part-way through the first response.
        server.enqueue(
            MockResponse.Builder().code(200).body(Buffer().write(video))
                .onResponseBody(SocketEffect.ShutdownConnection)
                .build(),
        )
        assertEquals(MediaDownloader.Outcome.Retry, run())
        server.takeRequest()
        val have = files.partial("v").length().toInt()
        assertEquals(DownloadStatus.Queued.name, db.downloadDao().get("v")!!.state)

        server.enqueue(rest(have))
        assertEquals(MediaDownloader.Outcome.Done, run())

        val resumed = server.takeRequest()
        assertEquals("bytes=$have-", resumed.headers["Range"])
        assertNull(resumed.headers["If-Range"])
        assertContentEquals(video, files.complete("v").readBytes())
    }

    @Test fun startsOverWhenTheServerFileChanged() = runTest {
        queued()
        files.partial("v").writeBytes(video.copyOf(4_000))
        server.enqueue(rest(4_000, total = 12_000)) // a different total than media_size

        assertEquals(MediaDownloader.Outcome.Retry, run())
        assertFalse(files.partial("v").exists())

        server.enqueue(full())
        assertEquals(MediaDownloader.Outcome.Done, run())
        assertNull(server.takeRequest().let { server.takeRequest() }.headers["Range"])
        assertContentEquals(video, files.complete("v").readBytes())
    }

    @Test fun aServerThatIgnoresRangeRestartsTheFile() = runTest {
        queued()
        files.partial("v").writeBytes(ByteArray(3_000) { 7 })
        server.enqueue(full()) // 200, not 206

        assertEquals(MediaDownloader.Outcome.Done, run())
        assertContentEquals(video, files.complete("v").readBytes())
    }

    @Test fun aRefusalFailsTheDownload() = runTest {
        queued()
        server.enqueue(MockResponse(code = 404))

        assertEquals(MediaDownloader.Outcome.Failed, run())
        assertEquals(DownloadStatus.Failed.name, db.downloadDao().get("v")!!.state)
    }
}
