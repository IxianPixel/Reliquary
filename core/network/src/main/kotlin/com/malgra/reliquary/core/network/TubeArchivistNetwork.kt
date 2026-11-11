package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.auth.RequestAuth
import com.malgra.reliquary.core.network.model.AccountDto
import com.malgra.reliquary.core.network.model.ErrorDto
import com.malgra.reliquary.core.network.model.LoginRequest
import com.malgra.reliquary.core.network.model.PingDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.create
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLException

/** Entry point to a Tube Archivist server: builds the API per base URL and wraps sign-in calls. */
@Singleton
class TubeArchivistNetwork @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    private val apis = ConcurrentHashMap<HttpUrl, TubeArchivistApi>()

    fun api(baseUrl: HttpUrl): TubeArchivistApi = apis.getOrPut(baseUrl) {
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create()
    }

    /** `GET /api/health/` needs no auth and answers `"OK"`. */
    suspend fun checkServer(baseUrl: HttpUrl): ServerCheck = try {
        val response = api(baseUrl).health()
        if (response.isSuccessful && response.body() == "OK") ServerCheck.Found else ServerCheck.NotTubeArchivist
    } catch (_: SerializationException) {
        ServerCheck.NotTubeArchivist
    } catch (e: IOException) {
        ServerCheck.Unreachable(e.toFailure())
    }

    /** Password sign-in. Success carries the session cookies from the 204 response. */
    suspend fun login(baseUrl: HttpUrl, username: String, password: String): LoginResult = try {
        val response = api(baseUrl).login(LoginRequest(username, password, rememberMe = "on"))
        when {
            response.isSuccessful -> {
                val cookies = Cookie.parseAll(response.raw().request.url, response.headers())
                val sessionId = cookies.lastOrNull { it.name == AuthInterceptor.SESSION_COOKIE }?.value
                val csrf = cookies.lastOrNull { it.name == AuthInterceptor.CSRF_COOKIE }?.value
                if (sessionId.isNullOrEmpty() || csrf.isNullOrEmpty()) {
                    LoginResult.Failed(Failure.NoSessionCookie)
                } else {
                    LoginResult.Success(Credentials.SessionCookie(sessionId, csrf))
                }
            }
            response.code() == 400 -> LoginResult.InvalidCredentials
            else -> LoginResult.Failed(Failure.Http(response.code(), errorMessage(response.errorBody()?.string())))
        }
    } catch (e: IOException) {
        LoginResult.Failed(e.toFailure())
    }

    /** `GET /api/ping/` with the given credentials: proves they work and returns the server version. */
    suspend fun ping(auth: RequestAuth): NetworkResult<PingDto> = call { api(auth.baseUrl.toHttpUrl()).ping(auth) }

    suspend fun account(auth: RequestAuth): NetworkResult<AccountDto> = call { api(auth.baseUrl.toHttpUrl()).account(auth) }

    /**
     * Ends the server-side session, using [auth] or else the stored session.
     * Best effort: failures are ignored.
     */
    suspend fun logout(baseUrl: HttpUrl, auth: RequestAuth? = null) {
        runCatching { api(baseUrl).logout(auth) }
    }

    private suspend fun <T> call(block: suspend () -> T): NetworkResult<T> = try {
        NetworkResult.Ok(block())
    } catch (e: HttpException) {
        NetworkResult.Error(Failure.Http(e.code(), errorMessage(e.response()?.errorBody()?.string())))
    } catch (_: SerializationException) {
        NetworkResult.Error(Failure.UnexpectedResponse)
    } catch (e: IOException) {
        NetworkResult.Error(e.toFailure())
    }

    private fun errorMessage(body: String?): String? = body?.let {
        runCatching { json.decodeFromString<ErrorDto>(it) }.getOrNull()?.let { dto -> dto.error ?: dto.detail }
    }

    private fun String.toHttpUrl(): HttpUrl = ServerUrl.normalize(this) ?: error("Invalid base URL")
}

sealed interface ServerCheck {
    data object Found : ServerCheck
    data object NotTubeArchivist : ServerCheck
    data class Unreachable(val failure: Failure) : ServerCheck
}

sealed interface LoginResult {
    data class Success(val credentials: Credentials.SessionCookie) : LoginResult
    data object InvalidCredentials : LoginResult
    data class Failed(val failure: Failure) : LoginResult
}

sealed interface NetworkResult<out T> {
    data class Ok<T>(val value: T) : NetworkResult<T>
    data class Error(val failure: Failure) : NetworkResult<Nothing>
}

/** Why a request failed, in terms the UI can explain. */
sealed interface Failure {
    data object UnknownHost : Failure
    data object ConnectionRefused : Failure
    data object Timeout : Failure
    data class Tls(val message: String?) : Failure
    data class Io(val message: String?) : Failure
    data class Http(val code: Int, val message: String?) : Failure
    data object NoSessionCookie : Failure
    data object UnexpectedResponse : Failure
}

internal fun IOException.toFailure(): Failure = when (this) {
    is UnknownHostException -> Failure.UnknownHost
    is ConnectException -> Failure.ConnectionRefused
    is SocketTimeoutException -> Failure.Timeout
    is SSLException -> Failure.Tls(message)
    else -> Failure.Io(message)
}
