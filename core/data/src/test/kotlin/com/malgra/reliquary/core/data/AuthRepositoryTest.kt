package com.malgra.reliquary.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.malgra.reliquary.core.data.auth.AuthRepository
import com.malgra.reliquary.core.data.fakes.FakeLibraryCache
import com.malgra.reliquary.core.data.auth.SignInResult
import com.malgra.reliquary.core.data.session.SessionCipher
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.di.NetworkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Reverses bytes: enough to prove the stored value goes through the cipher. */
    private object FakeCipher : SessionCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext.reversedArray()
        override fun decrypt(ciphertext: ByteArray) = ciphertext.reversedArray()
    }

    private lateinit var server: MockWebServer
    private val cache = FakeLibraryCache()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.close()

    private fun TestScope.newSessions(scope: CoroutineScope = backgroundScope): SessionRepository {
        val store = PreferenceDataStoreFactory.create(scope = scope) { tmp.root.resolve("session.preferences_pb") }
        return SessionRepository(store, FakeCipher, scope)
    }

    private fun TestScope.newAuth(sessions: SessionRepository): AuthRepository {
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build()
        return AuthRepository(TubeArchivistNetwork(client, NetworkModule.json()), sessions, cache)
    }

    private fun ping(version: String) = MockResponse(
        code = 200,
        body = """{"response":"pong","user":1,"version":"$version"}""",
    )

    private fun loginOk() = MockResponse.Builder().code(204)
        .addHeader("Set-Cookie", "csrftoken=c1; Path=/")
        .addHeader("Set-Cookie", "sessionid=s1; Path=/; HttpOnly")
        .build()

    @Test fun passwordSignInStoresSessionAndSurvivesRestart() = runTest(UnconfinedTestDispatcher()) {
        // DataStore allows one instance per file, so the first "app run" gets its own scope.
        val firstRun = CoroutineScope(backgroundScope.coroutineContext + Job())
        val sessions = newSessions(firstRun)
        val auth = newAuth(sessions)
        server.enqueue(loginOk())
        server.enqueue(ping("v0.6.0"))

        val result = auth.signInWithPassword(server.url("/").toString(), " alex ", "pw")

        assertEquals(SignInResult.Success, result)
        val signedIn = assertIs<SessionState.SignedIn>(sessions.state.value)
        assertEquals(Credentials.SessionCookie("s1", "c1"), signedIn.session.credentials)
        assertEquals("alex", signedIn.session.username)
        assertEquals("v0.6.0", signedIn.session.serverVersion)

        server.takeRequest() // login
        assertEquals("sessionid=s1; csrftoken=c1", server.takeRequest().headers["Cookie"]) // ping

        // A second repository on the same file reads the session back.
        firstRun.cancel()
        val reloaded = newSessions()
        val restored = reloaded.state.first { it !is SessionState.Loading }
        assertEquals(signedIn, restored)
    }

    @Test fun tokenSignInUsesAccountName() = runTest(UnconfinedTestDispatcher()) {
        val sessions = newSessions()
        val auth = newAuth(sessions)
        server.enqueue(ping("v0.6.2"))
        server.enqueue(MockResponse(code = 200, body = """{"id":1,"name":"admin","is_superuser":true,"is_staff":true}"""))

        assertEquals(SignInResult.Success, auth.signInWithToken(server.url("/").toString(), "tok"))
        assertEquals("Token tok", server.takeRequest().headers["Authorization"])
        val session = assertIs<SessionState.SignedIn>(sessions.state.value).session
        assertEquals("admin", session.username)
    }

    @Test fun rejectedTokenIsReported() = runTest(UnconfinedTestDispatcher()) {
        val sessions = newSessions()
        val auth = newAuth(sessions)
        server.enqueue(MockResponse(code = 403, body = """{"detail":"Invalid token."}"""))

        assertEquals(SignInResult.TokenRejected, auth.signInWithToken(server.url("/").toString(), "bad"))
        assertIs<SessionState.SignedOut>(sessions.state.value)
    }

    @Test fun oldServerIsRefusedAndLoggedOut() = runTest(UnconfinedTestDispatcher()) {
        val sessions = newSessions()
        val auth = newAuth(sessions)
        server.enqueue(loginOk())
        server.enqueue(ping("v0.5.12"))
        server.enqueue(MockResponse(code = 204))

        val result = auth.signInWithPassword(server.url("/").toString(), "alex", "pw")

        assertEquals(SignInResult.UnsupportedVersion("v0.5.12"), result)
        assertIs<SessionState.SignedOut>(sessions.state.value)
        server.takeRequest()
        server.takeRequest()
        val logout = server.takeRequest()
        assertEquals("/api/user/logout/", logout.url.encodedPath)
        assertEquals("c1", logout.headers["X-CSRFToken"])
    }

    @Test fun serverRejectingSessionSignsOutWithPrefill() = runTest(UnconfinedTestDispatcher()) {
        val sessions = newSessions()
        val auth = newAuth(sessions)
        server.enqueue(loginOk())
        server.enqueue(ping("v0.6.0"))
        auth.signInWithPassword(server.url("/").toString(), "alex", "pw")

        server.enqueue(
            MockResponse(code = 403, body = """{"detail":"Authentication credentials were not provided."}"""),
        )
        val client = OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build()
        client.newCall(Request.Builder().url(server.url("/api/video/")).build()).execute().close()

        val state = assertIs<SessionState.SignedOut>(sessions.state.value)
        assertEquals(true, state.expired)
        assertEquals("alex", state.lastUsername)
        assertNotEquals(null, state.lastServerUrl)
    }

    @Test fun cacheIsKeptOnlyWhenTheSameAccountSignsBackIn() = runTest(UnconfinedTestDispatcher()) {
        val sessions = newSessions()
        val auth = newAuth(sessions)
        val url = server.url("/").toString()
        server.enqueue(loginOk())
        server.enqueue(ping("v0.6.0"))
        auth.signInWithPassword(url, "alex", "pw")
        assertEquals(1, cache.clears) // first sign-in starts clean

        sessions.onAuthRejected()
        server.enqueue(loginOk())
        server.enqueue(ping("v0.6.0"))
        auth.signInWithPassword(url, "alex", "pw")
        assertEquals(1, cache.clears) // same account after expiry keeps the cache

        server.enqueue(MockResponse(code = 204)) // logout
        auth.signOut()
        assertEquals(2, cache.clears)
    }
}
