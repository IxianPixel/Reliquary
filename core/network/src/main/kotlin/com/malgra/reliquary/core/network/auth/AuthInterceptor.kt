package com.malgra.reliquary.core.network.auth

import com.malgra.reliquary.core.model.Credentials
import com.malgra.reliquary.core.network.ServerUrl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds credentials to requests for the signed-in server, and only that server.
 *
 * - Token: `Authorization: Token <key>`.
 * - Session: the `sessionid` and `csrftoken` cookies. DRF's SessionAuthentication
 *   runs Django's CSRF check on unsafe methods, so those also carry
 *   `X-CSRFToken`, plus `Origin` and `Referer` for the HTTPS origin check.
 *
 * It also picks up rotated session cookies and reports rejected credentials.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessions: SessionHolder,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(NoAuth::class.java) != null) return chain.proceed(request)
        val override = request.tag(RequestAuth::class.java)
        val stored = sessions.current
        val (baseUrlString, credentials) = when {
            override != null -> override.baseUrl to override.credentials
            stored != null -> stored.baseUrl to stored.credentials
            else -> return chain.proceed(request)
        }
        val baseUrl = baseUrlString.toHttpUrlOrNull()
        if (baseUrl == null || !sameOrigin(request.url, baseUrl)) return chain.proceed(request)

        val response = chain.proceed(request.withCredentials(credentials, baseUrl))

        // Only the stored session is updated or invalidated; sign-in handles its own.
        if (override == null) {
            if (credentials is Credentials.SessionCookie) {
                rotatedCookies(response, credentials)?.let(sessions::onCookiesRotated)
            }
            if (isAuthRejection(response)) sessions.onAuthRejected()
        }
        return response
    }

    private fun Request.withCredentials(credentials: Credentials, baseUrl: HttpUrl): Request {
        val builder = newBuilder()
        when (credentials) {
            is Credentials.ApiToken -> builder.header("Authorization", "Token ${credentials.token}")
            is Credentials.SessionCookie -> {
                builder.header(
                    "Cookie",
                    "$SESSION_COOKIE=${credentials.sessionId}; $CSRF_COOKIE=${credentials.csrfToken}",
                )
                if (method !in SAFE_METHODS) {
                    builder.header("X-CSRFToken", credentials.csrfToken)
                    builder.header("Origin", ServerUrl.origin(baseUrl))
                    builder.header("Referer", baseUrl.toString())
                }
            }
        }
        return builder.build()
    }

    private fun rotatedCookies(
        response: Response,
        current: Credentials.SessionCookie,
    ): Credentials.SessionCookie? {
        val cookies = Cookie.parseAll(response.request.url, response.headers)
        if (cookies.isEmpty()) return null
        val sessionId = cookies.lastOrNull { it.name == SESSION_COOKIE }?.value ?: current.sessionId
        val csrf = cookies.lastOrNull { it.name == CSRF_COOKIE }?.value ?: current.csrfToken
        if (sessionId.isEmpty()) return null // Django clears the cookie on logout
        val updated = Credentials.SessionCookie(sessionId, csrf)
        return updated.takeIf { it != current }
    }

    companion object {
        const val SESSION_COOKIE = "sessionid"
        const val CSRF_COOKIE = "csrftoken"
        private val SAFE_METHODS = setOf("GET", "HEAD", "OPTIONS", "TRACE")

        // DRF's messages for missing or bad credentials. Its 403 also covers
        // "You do not have permission" (admin-only endpoints), which is not a sign-out.
        private val REJECTION_DETAILS = listOf(
            "Authentication credentials were not provided",
            "Invalid token",
            "User inactive or deleted",
        )

        internal fun isAuthRejection(response: Response): Boolean = when (response.code) {
            401 -> true
            403 -> response.peekBody(4096).string().let { body ->
                REJECTION_DETAILS.any { it in body }
            }
            else -> false
        }

        fun sameOrigin(url: HttpUrl, base: HttpUrl): Boolean =
            url.scheme == base.scheme && url.host == base.host && url.port == base.port
    }
}
