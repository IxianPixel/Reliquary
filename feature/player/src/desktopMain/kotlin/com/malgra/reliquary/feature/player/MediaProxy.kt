package com.malgra.reliquary.feature.player

import com.malgra.reliquary.core.network.log.Log
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
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
 * [verbose] logs every request, not just failures.
 */
internal class MediaProxy(client: OkHttpClient, private val verbose: Boolean = false) : AutoCloseable {

    // Redirects are followed here, and only within the server: OkHttp would carry the cookie anywhere.
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
    private val secret = HexFormat.of().formatHex(ByteArray(16).also(SecureRandom()::nextBytes))
    private val routes = ConcurrentHashMap<String, HttpUrl>()
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

    /**
     * A loopback URL for [url], and its token. The URL ends in the file's name:
     * AVFoundation goes by the extension when the server's Content-Type is generic.
     */
    fun register(url: String): Registration {
        val token = nextToken.incrementAndGet().toString()
        val parsed = url.toHttpUrlOrNull()
        if (parsed != null) routes[token] = parsed
        val name = parsed?.pathSegments?.lastOrNull()?.takeIf { SafeName.matches(it) } ?: "media"
        return Registration(token, "http://127.0.0.1:${server.address.port}/$secret/$token/$name")
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
        val range = exchange.requestHeaders.getFirst("Range")
        if (url == null || (method != "GET" && method != "HEAD")) {
            Log.w(TAG, "#$token $method refused: not a registered media URL")
            exchange.sendResponseHeaders(404, -1)
            return
        }
        try {
            fetch(url, method, range).use { response ->
                val type = response.contentType(url)
                val length = response.body.contentLength()
                val line = "#$token $method range=${range ?: "-"} -> ${response.code} type=${type ?: "-"} " +
                    "upstreamType=${response.header("Content-Type") ?: "-"} length=$length"
                // A redirect still here is one off the server, which isn't followed.
                if (response.code >= 400 || response.isRedirect) {
                    Log.w(TAG, line)
                    onUpstreamError?.invoke(token, response.code)
                } else if (verbose) {
                    Log.i(TAG, line)
                }
                type?.let { exchange.responseHeaders.add("Content-Type", it) }
                PASSED_HEADERS.forEach { name ->
                    response.header(name)?.let { exchange.responseHeaders.add(name, it) }
                }
                val noBody = method == "HEAD" || response.code == 204 || response.code == 304 || response.isRedirect
                exchange.sendResponseHeaders(response.code, if (noBody) -1 else length.coerceAtLeast(0))
                if (noBody) return
                try {
                    response.body.byteStream().use { it.transferTo(exchange.responseBody) }
                } catch (_: IOException) {
                    // The player dropped a range it no longer needs.
                }
            }
        } catch (e: Exception) {
            // Not only IOException: anything else would drop the connection with no status, and the player would just wait.
            Log.w(TAG, "#$token $method range=${range ?: "-"} failed: ${e.javaClass.simpleName}: ${e.message}")
            onUpstreamError?.invoke(token, null)
            runCatching { exchange.sendResponseHeaders(502, -1) }
        }
    }

    /** The response for [url], following redirects that stay on its origin. */
    private fun fetch(url: HttpUrl, method: String, range: String?): Response {
        var target = url
        repeat(MAX_REDIRECTS + 1) {
            val request = Request.Builder()
                .url(target)
                .method(method, null)
                // A compressed body would lose its length and byte offsets.
                .header("Accept-Encoding", "identity")
                .apply { range?.let { header("Range", it) } }
                .build()
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response
            val next = response.header("Location")?.let(target::resolve)
            if (next == null || next.scheme != url.scheme || next.host != url.host || next.port != url.port) {
                Log.w(TAG, "Not following a redirect off the server (${response.code})")
                return response
            }
            response.close()
            if (verbose) Log.i(TAG, "Following a ${response.code} redirect on the server")
            target = next
        }
        throw IOException("Too many redirects")
    }

    /** The server's type when it names video or audio, else the one the file's extension implies. */
    private fun Response.contentType(url: HttpUrl): String? {
        val upstream = header("Content-Type")
        if (upstream != null && (upstream.startsWith("video/") || upstream.startsWith("audio/"))) return upstream
        if (!isSuccessful) return upstream
        val extension = url.pathSegments.lastOrNull()?.substringAfterLast('.', "")?.lowercase()
        return EXTENSION_TYPES[extension] ?: upstream
    }

    class Registration(val token: String, val url: String)

    private companion object {
        const val TAG = "MediaProxy"
        const val MAX_REDIRECTS = 5
        val PASSED_HEADERS = listOf("Content-Range", "Accept-Ranges", "Last-Modified")
        val SafeName = Regex("""[A-Za-z0-9._-]{1,128}""")
        val EXTENSION_TYPES = mapOf(
            "mp4" to "video/mp4",
            "m4v" to "video/mp4",
            "mov" to "video/quicktime",
            "webm" to "video/webm",
            "mkv" to "video/x-matroska",
            "vtt" to "text/vtt",
        )
    }
}
