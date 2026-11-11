package com.malgra.reliquary.core.model

/** How requests to the Tube Archivist server are authenticated. */
sealed interface Credentials {
    /** An admin's API token, sent as `Authorization: Token <key>`. */
    data class ApiToken(val token: String) : Credentials {
        override fun toString() = "ApiToken(***)"
    }

    /** Django session cookies from `POST /api/user/login/`. */
    data class SessionCookie(val sessionId: String, val csrfToken: String) : Credentials {
        override fun toString() = "SessionCookie(***)"
    }
}

/** A signed-in server. [baseUrl] always ends with `/`. */
data class ServerSession(
    val baseUrl: String,
    val credentials: Credentials,
    val username: String?,
    val serverVersion: String?,
)
