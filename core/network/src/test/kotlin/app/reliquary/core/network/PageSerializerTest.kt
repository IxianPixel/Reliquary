package app.reliquary.core.network

import app.reliquary.core.network.model.Page
import app.reliquary.core.network.model.VideoDto
import kotlinx.serialization.json.Json
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageSerializerTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private val video = """
        {"youtube_id":"abc","title":"T","media_url":"/media/stream/ch/abc.mp4","media_size":10,
         "player":{"watched":false,"duration":60,"duration_str":"1m","position":12.5},
         "channel":{"channel_id":"ch","channel_name":"C","channel_subs":1,"channel_subscribed":true,
                    "channel_active":true,"channel_last_refresh":"x","channel_tabs":["videos"]},
         "some_new_field": 1}
    """.trimIndent()

    @Test fun decodesDataAndPagination() {
        val page = json.decodeFromString<Page<VideoDto>>(
            """{"data":[$video],"paginate":{"page_size":12,"page_from":0,"current_page":1,"max_hits":false,
               "params":"","last_page":3,"total_hits":30}}""",
        )
        assertEquals("abc", page.data.single().youtubeId)
        assertEquals(12.5, page.data.single().player.position)
        assertEquals(3, page.paginate?.lastPage)
    }

    @Test fun acceptsBareArrayFromContinueWatching() {
        val empty = json.decodeFromString<Page<VideoDto>>("[]")
        assertEquals(emptyList(), empty.data)
        assertNull(empty.paginate)

        val one = json.decodeFromString<Page<VideoDto>>("[$video]")
        assertEquals(1, one.data.size)
    }
}
