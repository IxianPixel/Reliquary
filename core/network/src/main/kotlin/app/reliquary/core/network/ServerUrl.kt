package app.reliquary.core.network

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object ServerUrl {
    /**
     * Turns what the user typed into a base URL ending in `/`.
     * Without a scheme, local addresses (private IPs, `localhost`, `.local`,
     * `.lan`, `.home.arpa`, `.internal`) get `http://` and anything else `https://`.
     */
    fun normalize(input: String): HttpUrl? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val withScheme = if (SCHEME.containsMatchIn(trimmed)) trimmed else {
            val host = trimmed.substringBefore('/').substringBefore(':')
            (if (isLocalHost(host)) "http://" else "https://") + trimmed
        }
        val url = withScheme.toHttpUrlOrNull() ?: return null
        if (url.host.isEmpty()) return null
        return url.newBuilder()
            .encodedPath(url.encodedPath.trimEnd('/') + "/")
            .query(null)
            .fragment(null)
            .build()
    }

    private val SCHEME = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")

    /** `scheme://host[:port]`, as browsers send in the `Origin` header. */
    fun origin(url: HttpUrl): String {
        val defaultPort = HttpUrl.defaultPort(url.scheme)
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port"
    }

    fun isLocalHost(host: String): Boolean {
        val h = host.lowercase()
        if (h == "localhost" || h.endsWith(".localhost")) return true
        if (listOf(".local", ".lan", ".home.arpa", ".internal", ".home").any { h.endsWith(it) }) return true
        val parts = h.split('.').map { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it == null || it !in 0..255 }) return false
        val (a, b) = parts.requireNoNulls()
        return a == 10 || a == 127 ||
            (a == 192 && b == 168) ||
            (a == 172 && b in 16..31) ||
            (a == 100 && b in 64..127) || // CGNAT, e.g. Tailscale
            (a == 169 && b == 254)
    }
}
