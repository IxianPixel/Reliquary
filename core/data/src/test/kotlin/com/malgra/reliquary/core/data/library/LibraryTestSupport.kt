package com.malgra.reliquary.core.data.library

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.malgra.reliquary.core.database.ReliquaryDatabase
import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.model.ServerSession
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.auth.SessionHolder
import com.malgra.reliquary.core.network.di.NetworkModule
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient

internal class FakeSessionHolder(override var current: ServerSession?) : SessionHolder {
    override fun onCookiesRotated(updated: Credentials.SessionCookie) = Unit
    override fun onAuthRejected() = Unit
}

internal fun inMemoryDatabase(): ReliquaryDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), ReliquaryDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun libraryApiFor(server: MockWebServer): LibraryApi {
    val sessions = FakeSessionHolder(
        ServerSession(server.url("/").toString(), Credentials.ApiToken("tok"), "alex", "v0.6.0"),
    )
    val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build()
    return LibraryApi(TubeArchivistNetwork(client, NetworkModule.json()), sessions)
}

internal fun videoJson(
    id: String,
    title: String = "Video $id",
    watched: Boolean = false,
    position: Double? = null,
    watchedDate: Long? = null,
    subtitles: String = "[]",
) = """
    {
      "youtube_id": "$id",
      "title": "$title",
      "channel": {"channel_id": "UC1", "channel_name": "Orbital Mechanics Club"},
      "published": "2026-03-03T00:00:00+00:00",
      "date_downloaded": 1772500000,
      "vid_thumb_url": "/media/thumb/video/$id.jpg",
      "vid_type": "videos",
      "media_url": "/media/stream/UC1/$id.mp4",
      "media_size": 1000,
      "player": {"watched": $watched, "duration": 842, "duration_str": "14:02"${position?.let { ", \"position\": $it" } ?: ""}${watchedDate?.let { ", \"watched_date\": $it" } ?: ""}},
      "subtitles": $subtitles,
      "_index": "ta_video",
      "_score": 1.0
    }
""".trimIndent()

/** A page of [ids]; [next] adds a following page. */
internal fun videoPage(ids: List<String>, page: Int, next: Boolean) = """
    {
      "data": [${ids.joinToString(",") { videoJson(it) }}],
      "paginate": {
        "page_size": ${ids.size}, "page_from": 0, "current_page": $page,
        "last_page": ${if (next) page + 1 else page},
        "next_pages": ${if (next) "[${page + 1}]" else "[]"},
        "total_hits": 5, "max_hits": false, "params": ""
      }
    }
""".trimIndent()
