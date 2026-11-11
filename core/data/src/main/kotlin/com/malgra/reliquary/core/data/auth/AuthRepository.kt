package com.malgra.reliquary.core.data.auth

import com.malgra.reliquary.core.data.library.LibraryCache
import com.malgra.reliquary.core.data.session.SessionRepository
import com.malgra.reliquary.core.data.session.SessionState
import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.model.ServerSession
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.LoginResult
import com.malgra.reliquary.core.network.NetworkResult
import com.malgra.reliquary.core.network.ServerCheck
import com.malgra.reliquary.core.network.ServerUrl
import com.malgra.reliquary.core.network.TaVersion
import com.malgra.reliquary.core.network.TubeArchivistNetwork
import com.malgra.reliquary.core.network.auth.RequestAuth
import okhttp3.HttpUrl
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ServerCheckResult {
    data object InvalidUrl : ServerCheckResult
    data class Checked(val baseUrl: String, val check: ServerCheck) : ServerCheckResult
}

sealed interface SignInResult {
    data object Success : SignInResult
    data object InvalidUrl : SignInResult
    data object InvalidCredentials : SignInResult
    data object TokenRejected : SignInResult
    data class UnsupportedVersion(val version: String) : SignInResult
    data class Failed(val failure: Failure) : SignInResult
}

/**
 * Server checks, sign-in and sign-out. Writes the result to [SessionRepository]
 * and clears the [LibraryCache] when the account changes.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val network: TubeArchivistNetwork,
    private val sessions: SessionRepository,
    private val cache: LibraryCache,
) {
    suspend fun checkServer(input: String): ServerCheckResult {
        val url = ServerUrl.normalize(input) ?: return ServerCheckResult.InvalidUrl
        return ServerCheckResult.Checked(url.toString(), network.checkServer(url))
    }

    suspend fun signInWithPassword(server: String, username: String, password: String): SignInResult {
        val url = ServerUrl.normalize(server) ?: return SignInResult.InvalidUrl
        val name = username.trim()
        return when (val login = network.login(url, name, password)) {
            LoginResult.InvalidCredentials -> SignInResult.InvalidCredentials
            is LoginResult.Failed -> SignInResult.Failed(login.failure)
            is LoginResult.Success -> finishSignIn(url, login.credentials, name)
        }
    }

    suspend fun signInWithToken(server: String, token: String): SignInResult {
        val url = ServerUrl.normalize(server) ?: return SignInResult.InvalidUrl
        return finishSignIn(url, Credentials.ApiToken(token.trim()), username = null)
    }

    suspend fun signOut() {
        val session = sessions.current
        if (session?.credentials is Credentials.SessionCookie) {
            ServerUrl.normalize(session.baseUrl)?.let { network.logout(it) }
        }
        sessions.signOut()
        cache.clear()
    }

    /** Pings with the new credentials to prove they work and to check the server version. */
    private suspend fun finishSignIn(url: HttpUrl, credentials: Credentials, username: String?): SignInResult {
        val auth = RequestAuth(url.toString(), credentials)
        val ping = when (val result = network.ping(auth)) {
            is NetworkResult.Ok -> result.value
            is NetworkResult.Error -> {
                val failure = result.failure
                val rejected = failure is Failure.Http && failure.code in 401..403
                return when {
                    rejected && credentials is Credentials.ApiToken -> SignInResult.TokenRejected
                    rejected -> SignInResult.InvalidCredentials
                    else -> SignInResult.Failed(failure)
                }
            }
        }

        val version = TaVersion.parse(ping.version)
        if (version == null || version < TaVersion.MINIMUM) {
            if (credentials is Credentials.SessionCookie) network.logout(url, auth)
            return SignInResult.UnsupportedVersion(ping.version)
        }

        val name = username ?: (network.account(auth) as? NetworkResult.Ok)?.value?.name
        // Signing back in after the session expired keeps the cache; anyone or anywhere else starts clean.
        val previous = sessions.state.value as? SessionState.SignedOut
        val sameAccount = previous?.expired == true &&
            previous.lastServerUrl == url.toString() && previous.lastUsername == name
        if (!sameAccount) cache.clear()
        sessions.signIn(ServerSession(url.toString(), credentials, name, ping.version))
        return SignInResult.Success
    }
}
