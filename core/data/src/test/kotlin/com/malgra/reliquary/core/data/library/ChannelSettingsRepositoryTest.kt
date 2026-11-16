package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.model.ChannelSettings
import com.malgra.reliquary.core.model.LoadError
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChannelSettingsRepositoryTest {

    private lateinit var server: MockWebServer
    private lateinit var repository: ChannelSettingsRepository

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        repository = ChannelSettingsRepository(libraryApiFor(server))
    }

    @After fun tearDown() = server.close()

    @Test fun readsTheChannelsOverwrites() = runTest {
        server.enqueue(
            MockResponse(
                code = 200,
                body = """{"channel_id": "UC1", "channel_name": "Orbital Mechanics Club", "channel_overwrites": {
                    "download_format": "bestvideo[height<=720]+bestaudio", "autodelete_days": 30,
                    "index_playlists": true, "integrate_sponsorblock": false,
                    "subscriptions_channel_size": 10, "subscriptions_shorts_channel_size": 0}}""",
            ),
        )

        val settings = assertIs<LoadResult.Ok<ChannelSettings>>(repository.settings("UC1")).value

        assertEquals(
            ChannelSettings(
                videosPageSize = 10,
                shortsPageSize = 0,
                streamsPageSize = null,
                downloadFormat = "bestvideo[height<=720]+bestaudio",
                autoDeleteDays = 30,
                indexPlaylists = true,
                sponsorBlock = false,
            ),
            settings,
        )
        assertEquals("/api/channel/UC1/", server.takeRequest().url.encodedPath)
    }

    @Test fun aChannelWithNoOverwritesUsesTheServersSettings() = runTest {
        server.enqueue(MockResponse(code = 200, body = """{"channel_id": "UC1", "channel_name": "Orbital Mechanics Club"}"""))

        assertEquals(ChannelSettings(), assertIs<LoadResult.Ok<ChannelSettings>>(repository.settings("UC1")).value)
    }

    @Test fun savingSendsOnlyChangesAndClearsWithNull() = runTest {
        server.enqueue(MockResponse(code = 200, body = "{}"))
        val old = ChannelSettings(videosPageSize = 10, downloadFormat = "best", indexPlaylists = true, sponsorBlock = true)
        val new = old.copy(videosPageSize = 25, downloadFormat = " ", sponsorBlock = null, autoDeleteDays = 7)

        assertNull(repository.save("UC1", old, new))

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/channel/UC1/", request.url.encodedPath)
        assertEquals(
            Json.parseToJsonElement(
                """{"channel_overwrites": {"download_format": null, "autodelete_days": 7,
                    "integrate_sponsorblock": null, "subscriptions_channel_size": 25}}""",
            ),
            Json.parseToJsonElement(request.body!!.utf8()),
        )
    }

    @Test fun turningPlaylistIndexingOffClearsIt() = runTest {
        server.enqueue(MockResponse(code = 200, body = "{}"))

        assertNull(repository.save("UC1", ChannelSettings(indexPlaylists = true), ChannelSettings()))

        assertEquals(
            Json.parseToJsonElement("""{"channel_overwrites": {"index_playlists": null}}"""),
            Json.parseToJsonElement(server.takeRequest().body!!.utf8()),
        )
    }

    @Test fun savingNothingChangedSendsNothing() = runTest {
        assertNull(repository.save("UC1", ChannelSettings(autoDeleteDays = 3), ChannelSettings(autoDeleteDays = 3)))

        assertEquals(0, server.requestCount)
    }

    @Test fun savingReportsServerErrors() = runTest {
        server.enqueue(MockResponse(code = 400, body = """{"error": "bad request"}"""))

        assertEquals(LoadError.ServerError, repository.save("UC1", ChannelSettings(), ChannelSettings(shortsPageSize = 0)))
    }
}
