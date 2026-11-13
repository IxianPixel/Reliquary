package com.malgra.reliquary.core.network

import com.malgra.reliquary.core.network.auth.AuthInterceptor
import com.malgra.reliquary.core.network.auth.SessionHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the signed-in server answered the last request made to it. The
 * phone can be online while the server isn't (a VPN such as Tailscale is
 * down, or you're away from home), so this, not the network, decides "offline".
 * Null until a request has been made.
 */
@Singleton
class ServerReachability @Inject constructor() {
    private val _reachable = MutableStateFlow<Boolean?>(null)
    val reachable: StateFlow<Boolean?> = _reachable.asStateFlow()

    internal fun report(reachable: Boolean) {
        _reachable.value = reachable
    }

    /** Forgets the last answer, e.g. when the account changes. */
    fun reset() {
        _reachable.value = null
    }
}

/** Reports every request to the signed-in server to [ServerReachability]. */
class ReachabilityInterceptor @Inject constructor(
    private val sessions: SessionHolder,
    private val reachability: ServerReachability,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val base = sessions.current?.baseUrl?.toHttpUrlOrNull()
        val tracked = base != null && AuthInterceptor.sameOrigin(request.url, base)
        return try {
            // Any HTTP answer, even an error, means the server is there.
            chain.proceed(request).also { if (tracked) reachability.report(true) }
        } catch (e: IOException) {
            // A cancelled call (an image scrolled away) says nothing about the server.
            if (tracked && !chain.call().isCanceled()) reachability.report(false)
            throw e
        }
    }
}
