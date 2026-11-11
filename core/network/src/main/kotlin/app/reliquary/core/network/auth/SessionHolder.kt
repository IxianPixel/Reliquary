package app.reliquary.core.network.auth

import app.reliquary.core.model.Credentials
import app.reliquary.core.model.ServerSession

/** The signed-in session as the network layer sees it. Implemented in core:data. */
interface SessionHolder {
    /** Read on every request, so it must be cheap and non-blocking. */
    val current: ServerSession?

    /** The server sent new `sessionid` / `csrftoken` cookies (Django rotates them). */
    fun onCookiesRotated(updated: Credentials.SessionCookie)

    /** The server no longer accepts the stored credentials. */
    fun onAuthRejected()
}

/** Credentials for one request, used during sign-in before a session is stored. */
data class RequestAuth(val baseUrl: String, val credentials: Credentials)

/** Tag for requests that must go out without stored credentials, such as login. */
data object NoAuth
