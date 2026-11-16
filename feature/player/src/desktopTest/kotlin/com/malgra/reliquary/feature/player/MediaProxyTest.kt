package com.malgra.reliquary.feature.player

import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaProxyTest {

    private val server = MockWebServer()

    // Stands in for the app's client and its AuthInterceptor.
    private val signedIn = OkHttpClient.Builder()
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Authorization", "Token secret").build()) }
        .build()
    private val proxy = MediaProxy(signedIn)
    private val player = OkHttpClient()

    @Before fun setUp() = server.start()

    @After fun tearDown() {
        proxy.close()
        server.close()
    }

    @Test fun rangesPassThroughWithTheCredentialsAndWithoutIfRange() {
        server.enqueue(
            MockResponse(
                code = 206,
                headers = headersOf("Content-Range", "bytes 100-104/1000", "Content-Type", "video/mp4"),
                body = "abcde",
            ),
        )
        val local = proxy.register(server.url("/media/v.mp4").toString())

        player.newCall(
            Request.Builder().url(local.url).header("Range", "bytes=100-").header("If-Range", "\"etag\"").build(),
        ).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 100-104/1000", response.header("Content-Range"))
            assertEquals("abcde", response.body.string())
        }
        val upstream = server.takeRequest()
        assertEquals("/media/v.mp4", upstream.url.encodedPath)
        assertEquals("bytes=100-", upstream.headers["Range"])
        assertEquals("Token secret", upstream.headers["Authorization"])
        assertNull(upstream.headers["If-Range"])
    }

    @Test fun onlyRegisteredUrlsBehindTheSecretAreServed() {
        val local = proxy.register(server.url("/media/v.mp4").toString())
        val secretPath = local.url.substringBeforeLast('/')
        val origin = secretPath.substringBeforeLast('/')

        assertEquals(404, status("$secretPath/999"))
        assertEquals(404, status("$origin/wrong-secret/${local.token}"))
        assertEquals(0, server.requestCount)
    }

    @Test fun refusalsAreReported() {
        server.enqueue(MockResponse(code = 401))
        var reported: Pair<String, Int?>? = null
        proxy.onUpstreamError = { token, status -> reported = token to status }
        val local = proxy.register(server.url("/media/v.mp4").toString())

        assertEquals(401, status(local.url))
        assertEquals(local.token to 401, reported)
    }

    @Test fun itListensOnTheLoopbackAddressOnly() {
        val local = proxy.register(server.url("/media/v.mp4").toString())
        assertTrue(local.url.startsWith("http://127.0.0.1:"))
    }

    private fun status(url: String) = player.newCall(Request.Builder().url(url).build()).execute().use { it.code }
}
