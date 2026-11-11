package app.reliquary.core.data.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.reliquary.core.data.di.ApplicationScope
import app.reliquary.core.data.di.SessionStore
import app.reliquary.core.model.Credentials
import app.reliquary.core.model.ServerSession
import app.reliquary.core.network.auth.SessionHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SessionState {
    data object Loading : SessionState

    /** [expired] is true when the server rejected the stored credentials. */
    data class SignedOut(
        val lastServerUrl: String? = null,
        val lastUsername: String? = null,
        val expired: Boolean = false,
    ) : SessionState

    data class SignedIn(val session: ServerSession) : SessionState
}

/**
 * The single source of truth for who is signed in. The session is kept in
 * DataStore, encrypted with [SessionCipher]; the last server URL and username
 * are kept in clear text so sign-in can be prefilled.
 */
@Singleton
class SessionRepository @Inject constructor(
    @SessionStore private val dataStore: DataStore<Preferences>,
    private val cipher: SessionCipher,
    @ApplicationScope private val scope: CoroutineScope,
) : SessionHolder {

    private val json = Json { ignoreUnknownKeys = true }
    private val writes = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    override val current: ServerSession?
        get() = (_state.value as? SessionState.SignedIn)?.session

    init {
        scope.launch { load() }
    }

    private suspend fun load() {
        val prefs = dataStore.data.first()
        val session = prefs[SESSION]?.let(::decode)
        _state.value = session?.let(SessionState::SignedIn) ?: SessionState.SignedOut(
            lastServerUrl = prefs[LAST_SERVER_URL],
            lastUsername = prefs[LAST_USERNAME],
            expired = prefs[EXPIRED] == true,
        )
    }

    suspend fun signIn(session: ServerSession) {
        writes.withLock {
            dataStore.edit { prefs ->
                prefs[SESSION] = encode(session)
                prefs[LAST_SERVER_URL] = session.baseUrl
                session.username?.let { prefs[LAST_USERNAME] = it } ?: prefs.remove(LAST_USERNAME)
                prefs[EXPIRED] = false
            }
            _state.value = SessionState.SignedIn(session)
        }
    }

    suspend fun signOut() {
        writes.withLock {
            val previous = current
            dataStore.edit { prefs ->
                prefs.remove(SESSION)
                prefs[EXPIRED] = false
            }
            _state.value = SessionState.SignedOut(previous?.baseUrl, previous?.username, expired = false)
        }
    }

    override fun onCookiesRotated(updated: Credentials.SessionCookie) {
        val session = current ?: return
        val rotated = session.copy(credentials = updated)
        _state.value = SessionState.SignedIn(rotated)
        scope.launch {
            writes.withLock {
                if (current == rotated) dataStore.edit { it[SESSION] = encode(rotated) }
            }
        }
    }

    override fun onAuthRejected() {
        val session = current ?: return
        _state.value = SessionState.SignedOut(session.baseUrl, session.username, expired = true)
        scope.launch {
            writes.withLock {
                dataStore.edit { prefs ->
                    prefs.remove(SESSION)
                    prefs[EXPIRED] = true
                }
            }
        }
    }

    private fun encode(session: ServerSession): String {
        val stored = when (val c = session.credentials) {
            is Credentials.ApiToken -> StoredSession(session.baseUrl, session.username, session.serverVersion, token = c.token)
            is Credentials.SessionCookie -> StoredSession(
                session.baseUrl, session.username, session.serverVersion,
                sessionId = c.sessionId, csrfToken = c.csrfToken,
            )
        }
        val bytes = json.encodeToString(StoredSession.serializer(), stored).encodeToByteArray()
        return Base64.getEncoder().encodeToString(cipher.encrypt(bytes))
    }

    /** Null when the data is unreadable, e.g. the Keystore key is gone; the user signs in again. */
    private fun decode(value: String): ServerSession? = runCatching {
        val bytes = cipher.decrypt(Base64.getDecoder().decode(value))
        val stored = json.decodeFromString(StoredSession.serializer(), bytes.decodeToString())
        val credentials = when {
            stored.token != null -> Credentials.ApiToken(stored.token)
            stored.sessionId != null && stored.csrfToken != null ->
                Credentials.SessionCookie(stored.sessionId, stored.csrfToken)
            else -> return null
        }
        ServerSession(stored.baseUrl, credentials, stored.username, stored.serverVersion)
    }.getOrNull()

    @Serializable
    private data class StoredSession(
        val baseUrl: String,
        val username: String?,
        val serverVersion: String?,
        val token: String? = null,
        val sessionId: String? = null,
        val csrfToken: String? = null,
    )

    private companion object {
        val SESSION = stringPreferencesKey("session")
        val LAST_SERVER_URL = stringPreferencesKey("last_server_url")
        val LAST_USERNAME = stringPreferencesKey("last_username")
        val EXPIRED = booleanPreferencesKey("expired")
    }
}
