package com.malgra.reliquary.feature.player

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Serves server media to the desktop player, which can't send headers (TA media
 * needs the session cookie or token). It listens on the loopback address only,
 * and every path holds a random per-run secret and a token for one URL the
 * player registered, so nothing else on the machine can use it to reach the
 * server. Requests go through [client], whose AuthInterceptor adds credentials
 * for the signed-in server only.
 *
 * Byte ranges pass through; If-Range never does (TA then sends the whole file).
 */
internal class MediaProxy(client: OkHttpClient) : AutoCloseable {

    // The player follows no redirects of ours, and a redirect could leave the server.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val secret = HexFormat.of().formatHex(ByteArray(16).also(SecureRandom()::nextBytes))
    private val routes = ConcurrentHashMap<String, String>()
    private val nextToken = AtomicLong()

    /** Called (on a proxy thread) when the server refuses or can't be reached: its status, or null for a network error. */
    @Volatile var onUpstreamError: ((token: String, status: Int?) -> Unit)? = null

    init {
        server.createContext("/$secret/") { exchange ->
            try {
                forward(exchange)
            } finally {
                exchange.close()
            }
        }
        server.executor = Executors.newCachedThreadPool { runnable ->
            Thread(runnable, "media-proxy").apply { isDaemon = true }
        }
        server.start()
    }

    /** A loopback URL for [url], and its token. */
    fun register(url: String): Registration {
        val token = nextToken.incrementAndGet().toString()
        routes[token] = url
        return Registration(token, "http://127.0.0.1:${server.address.port}/$secret/$token")
    }

    /** Forgets every URL registered so far. */
    fun clear() = routes.clear()

    override fun close() {
        routes.clear()
        server.stop(0)
    }

    private fun forward(exchange: HttpExchange) {
        val token = exchange.requestURI.rawPath.removePrefix("/$secret/").substringBefore('/')
        val url = routes[token]
        val method = exchange.requestMethod
        if (url == null || (method != "GET" && method != "HEAD")) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        val request = Request.Builder()
            .url(url)
            .method(method, null)
            // A compressed body would lose its length and byte offsets.
            .header("Accept-Encoding", "identity")
            .apply { exchange.requestHeaders.getFirst("Range")?.let { header("Range", it) } }
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.code >= 400) onUpstreamError?.invoke(token, response.code)
                RESPONSE_HEADERS.forEach { name ->
                    response.header(name)?.let { exchange.responseHeaders.add(name, it) }
                }
                val length = response.body.contentLength()
                val noBody = method == "HEAD" || response.code == 204 || response.code == 304
                exchange.sendResponseHeaders(response.code, if (noBody) -1 else length.coerceAtLeast(0))
                if (noBody) return
                try {
                    response.body.byteStream().use { it.transferTo(exchange.responseBody) }
                } catch (_: IOException) {
                    // The player dropped a range it no longer needs.
                }
            }
        } catch (e: IOException) {
            onUpstreamError?.invoke(token, null)
            runCatching { exchange.sendResponseHeaders(502, -1) }
        }
    }

    class Registration(val token: String, val url: String)

    private companion object {
        val RESPONSE_HEADERS = listOf("Content-Type", "Content-Range", "Accept-Ranges", "Last-Modified")
    }
}
