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
        val secretPath = local.url.substringBefore("/${local.token}/")
        val origin = secretPath.substringBeforeLast('/')

        assertEquals(404, status("$secretPath/999/v.mp4"))
        assertEquals(404, status("$origin/wrong-secret/${local.token}/v.mp4"))
        assertEquals(0, server.requestCount)
    }

    @Test fun theUrlEndsInTheFileName() {
        // AVFoundation goes by the extension when the Content-Type is generic.
        assertTrue(proxy.register(server.url("/media/UC1/abc.mp4").toString()).url.endsWith("/abc.mp4"))
        assertTrue(proxy.register(server.url("/media/odd%20name").toString()).url.endsWith("/media"))
    }

    @Test fun aGenericContentTypeBecomesTheOneTheExtensionImplies() {
        server.enqueue(MockResponse(code = 200, headers = headersOf("Content-Type", "application/octet-stream"), body = "x"))
        server.enqueue(MockResponse(code = 200, headers = headersOf("Content-Type", "video/webm"), body = "x"))
        val mp4 = proxy.register(server.url("/media/v.mp4").toString())
        val webm = proxy.register(server.url("/media/v.mp4").toString())

        assertEquals("video/mp4", contentType(mp4.url))
        assertEquals("video/webm", contentType(webm.url)) // the server's video type stands
    }

    @Test fun redirectsAreFollowedOnlyWithinTheServer() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "/media/real.mp4")))
        server.enqueue(MockResponse(code = 200, body = "abc"))
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "http://elsewhere.example/v.mp4")))
        var reported: Int? = null
        proxy.onUpstreamError = { _, status -> reported = status }

        player.newCall(Request.Builder().url(proxy.register(server.url("/media/v.mp4").toString()).url).build()).execute().use {
            assertEquals(200, it.code)
            assertEquals("abc", it.body.string())
        }
        server.takeRequest()
        val followed = server.takeRequest()
        assertEquals("/media/real.mp4", followed.url.encodedPath)
        assertEquals("Token secret", followed.headers["Authorization"])

        assertEquals(302, status(proxy.register(server.url("/media/w.mp4").toString()).url))
        assertEquals(302, reported)
        assertEquals(3, server.requestCount) // nothing sent to elsewhere.example
    }

    @Test fun anUnexpectedFailureStillAnswersAndIsReported() {
        val failing = MediaProxy(OkHttpClient.Builder().addInterceptor { error("boom") }.build())
        var reported = false
        failing.onUpstreamError = { _, status -> reported = status == null }
        try {
            assertEquals(502, status(failing.register(server.url("/media/v.mp4").toString()).url))
            assertTrue(reported)
        } finally {
            failing.close()
        }
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

    private fun contentType(url: String) = player.newCall(Request.Builder().url(url).build()).execute().use { it.header("Content-Type") }
}
