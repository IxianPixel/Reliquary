package app.reliquary.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.reliquary.core.data.auth.AuthRepository
import app.reliquary.core.data.auth.SignInResult
import app.reliquary.core.data.session.SessionCipher
import app.reliquary.core.data.session.SessionRepository
import app.reliquary.core.data.session.SessionState
import app.reliquary.core.model.Credentials
import app.reliquary.core.network.NetworkResult
import app.reliquary.core.network.ServerUrl
import app.reliquary.core.network.TubeArchivistNetwork
import app.reliquary.core.network.auth.AuthInterceptor
import app.reliquary.core.network.auth.RequestAuth
import app.reliquary.core.network.di.NetworkModule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Runs sign-in against a real Tube Archivist. Skipped unless these are set:
 * TA_TEST_URL, TA_TEST_USERNAME, TA_TEST_PASSWORD, TA_TEST_TOKEN.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveServerSignInTest {

    @get:Rule val tmp = TemporaryFolder()

    private val url = System.getenv("TA_TEST_URL")
    private val username = System.getenv("TA_TEST_USERNAME")
    private val password = System.getenv("TA_TEST_PASSWORD")
    private val token = System.getenv("TA_TEST_TOKEN")

    private object PlainCipher : SessionCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(ciphertext: ByteArray) = ciphertext
    }

    @Test fun signInFlowsAgainstRealServer() = runTest(UnconfinedTestDispatcher()) {
        assumeTrue("TA_TEST_URL not set", url != null && username != null && password != null && token != null)

        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { tmp.root.resolve("s.preferences_pb") }
        val sessions = SessionRepository(store, PlainCipher, backgroundScope)
        val network = TubeArchivistNetwork(
            OkHttpClient.Builder().addInterceptor(AuthInterceptor(sessions)).build(),
            NetworkModule.json(),
        )
        val auth = AuthRepository(network, sessions)

        assertEquals(SignInResult.InvalidCredentials, auth.signInWithPassword(url!!, username!!, "definitely-wrong"))
        assertEquals(SignInResult.TokenRejected, auth.signInWithToken(url, "not-a-real-token"))

        // Password: session cookie stored, and the stored session works for a later request.
        assertEquals(SignInResult.Success, auth.signInWithPassword(url, username, password!!))
        val session = assertIs<SessionState.SignedIn>(sessions.state.value).session
        assertIs<Credentials.SessionCookie>(session.credentials)
        assertEquals(username, session.username)
        val base = ServerUrl.normalize(url)!!
        network.api(base).ping() // throws on 403

        // Sign-out POSTs logout, which needs CSRF + Origin; afterwards the old cookie is dead.
        val oldCookie = session.credentials
        auth.signOut()
        val afterLogout = network.ping(RequestAuth(session.baseUrl, oldCookie))
        val failure = assertIs<NetworkResult.Error>(afterLogout).failure
        println("ping with logged-out cookie: $failure")

        // Token: username comes from /api/user/account/.
        assertEquals(SignInResult.Success, auth.signInWithToken(url, token!!))
        val tokenSession = assertIs<SessionState.SignedIn>(sessions.state.value).session
        assertEquals(username, tokenSession.username)
        assertEquals("v0.6.0", tokenSession.serverVersion)
    }
}
