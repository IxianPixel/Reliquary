package com.malgra.reliquary.core.network.auth

import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.model.ServerSession
import com.malgra.reliquary.core.network.LoginResult
import com.malgra.reliquary.core.network.ServerCheck
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.di.NetworkModule
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AuthInterceptorTest {

    private class FakeHolder(override var current: ServerSession?) : SessionHolder {
        var rotated: Credentials.SessionCookie? = null
        var rejected = false
        override fun onCookiesRotated(updated: Credentials.SessionCookie) {
            rotated = updated
            current = current?.copy(credentials = updated)
        }
        override fun onAuthRejected() { rejected = true }
    }

    private lateinit var server: MockWebServer
    private lateinit var holder: FakeHolder
    private lateinit var client: OkHttpClient
    private lateinit var baseUrl: String

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
        baseUrl = server.url("/").toString()
        holder = FakeHolder(null)
        client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(holder)).build()
    }

    @After fun tearDown() = server.close()

    private fun signIn(credentials: Credentials) {
        holder.current = ServerSession(baseUrl, credentials, "alex", "v0.6.0")
    }

    private fun get(path: String) = client.newCall(Request.Builder().url(server.url(path)).build()).execute().close()

    private fun post(path: String) = client.newCall(
        Request.Builder().url(server.url(path)).post("{}".toRequestBody()).build(),
    ).execute().close()

    @Test fun tokenGoesInAuthorizationHeader() {
        signIn(Credentials.ApiToken("abc123"))
        server.enqueue(MockResponse(code = 200))
        get("/api/ping/")
        val request = server.takeRequest()
        assertEquals("Token abc123", request.headers["Authorization"])
        assertNull(request.headers["Cookie"])
    }

    @Test fun sessionSendsCookiesAndCsrfOnlyOnWrites() {
        signIn(Credentials.SessionCookie("sess", "csrf"))
        server.enqueue(MockResponse(code = 200))
        server.enqueue(MockResponse(code = 200))
        get("/api/video/")
        post("/api/video/x/progress/")

        val read = server.takeRequest()
        assertEquals("sessionid=sess; csrftoken=csrf", read.headers["Cookie"])
        assertNull(read.headers["X-CSRFToken"])

        val write = server.takeRequest()
        assertEquals("csrf", write.headers["X-CSRFToken"])
        assertEquals(baseUrl.trimEnd('/'), write.headers["Origin"])
        assertEquals(baseUrl, write.headers["Referer"])
    }

    @Test fun neverSendsCredentialsToAnotherOrigin() {
        signIn(Credentials.ApiToken("abc123"))
        val other = MockWebServer().apply { start() }
        other.enqueue(MockResponse(code = 200))
        client.newCall(Request.Builder().url(other.url("/x")).build()).execute().close()
        assertNull(other.takeRequest().headers["Authorization"])
        other.close()
    }

    @Test fun picksUpRotatedSessionCookies() {
        signIn(Credentials.SessionCookie("old", "csrf"))
        server.enqueue(
            MockResponse.Builder().code(200)
                .addHeader("Set-Cookie", "sessionid=new; Path=/; HttpOnly")
                .build(),
        )
        get("/api/ping/")
        assertEquals(Credentials.SessionCookie("new", "csrf"), holder.rotated)
    }

    @Test fun missingCredentialsMessageSignsOut() {
        signIn(Credentials.SessionCookie("sess", "csrf"))
        server.enqueue(
            MockResponse(code = 403, body = """{"detail":"Authentication credentials were not provided."}"""),
        )
        get("/api/ping/")
        assertTrue(holder.rejected)
    }

    @Test fun adminOnlyForbiddenDoesNotSignOut() {
        signIn(Credentials.SessionCookie("sess", "csrf"))
        server.enqueue(
            MockResponse(code = 403, body = """{"detail":"You do not have permission to perform this action."}"""),
        )
        get("/api/appsettings/token/")
        assertFalse(holder.rejected)
    }

    @Test fun loginReturnsSessionCookiesWithoutSendingOldOnes() = runTest {
        signIn(Credentials.SessionCookie("stale", "stale"))
        val network = TubeArchivistNetwork(client, NetworkModule.json())
        server.enqueue(
            MockResponse.Builder().code(204)
                .addHeader("Set-Cookie", "csrftoken=c1; Path=/")
                .addHeader("Set-Cookie", "sessionid=s1; Path=/; HttpOnly")
                .build(),
        )
        val result = network.login(server.url("/"), "alex", "pw")
        assertEquals(LoginResult.Success(Credentials.SessionCookie("s1", "c1")), result)
        val request = server.takeRequest()
        assertNull(request.headers["Cookie"])
        assertTrue(request.body!!.utf8().contains("\"remember_me\":\"on\""))
    }

    @Test fun wrongPasswordIsInvalidCredentials() = runTest {
        val network = TubeArchivistNetwork(client, NetworkModule.json())
        server.enqueue(MockResponse(code = 400, body = """{"error":"Invalid credentials"}"""))
        assertEquals(LoginResult.InvalidCredentials, network.login(server.url("/"), "alex", "nope"))
    }

    @Test fun healthCheckRecognisesTubeArchivist() = runTest {
        val network = TubeArchivistNetwork(client, NetworkModule.json())
        server.enqueue(MockResponse(code = 200, body = "\"OK\""))
        server.enqueue(MockResponse(code = 200, body = "<html>router login</html>"))
        assertEquals(ServerCheck.Found, network.checkServer(server.url("/")))
        assertEquals(ServerCheck.NotTubeArchivist, network.checkServer(server.url("/")))
    }

    @Test fun unreachableServerIsReported() = runTest {
        val network = TubeArchivistNetwork(client, NetworkModule.json())
        val url = server.url("/")
        server.close()
        assertIs<ServerCheck.Unreachable>(network.checkServer(url))
    }
}
