package com.malgra.reliquary.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress

/**
 * Whether reaching a server goes over the local network, which Android 17
 * blocks for apps that target it unless they hold ACCESS_LOCAL_NETWORK.
 * Loopback doesn't count, and nor do Tailscale-style 100.64.0.0/10 addresses:
 * tested on Android 17, a server there stays reachable without the permission.
 */
object LocalNetwork {

    /** True for a private or link-local address (IPv4 or IPv6). */
    fun isLocal(address: ByteArray): Boolean = when (address.size) {
        4 -> {
            val a = address[0].toInt() and 0xFF
            val b = address[1].toInt() and 0xFF
            a == 10 ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 169 && b == 254)
        }
        16 -> {
            val first = address[0].toInt() and 0xFF
            val second = address[1].toInt() and 0xFF
            (first and 0xFE) == 0xFC || // unique local, fc00::/7
                (first == 0xFE && (second and 0xC0) == 0x80) || // link-local, fe80::/10
                (address.take(10).all { it.toInt() == 0 } && address[10].toInt() == -1 && address[11].toInt() == -1 &&
                    isLocal(address.copyOfRange(12, 16))) // IPv4-mapped
        }
        else -> false
    }

    /** True for names only a local network can answer, like `nas.local` or `archive.home.arpa`. */
    fun isLocalName(host: String): Boolean {
        val h = host.lowercase().trimEnd('.')
        if (h == "localhost" || h.endsWith(".localhost")) return false
        return LOCAL_SUFFIXES.any { h.endsWith(it) } || (!h.contains('.') && !h.contains(':'))
    }

    /**
     * Whether [host] is on the local network: by its name, or else by the
     * addresses it resolves to (a name like `archive.example.com` can point at
     * 192.168.x.x). A name that can't be resolved here counts as not local.
     */
    suspend fun needsLocalNetwork(host: String): Boolean {
        if (isLocalName(host)) return true
        val addresses = withContext(Dispatchers.IO) {
            runCatching { InetAddress.getAllByName(host).toList() }.getOrDefault(emptyList())
        }
        return addresses.any { !it.isLoopbackAddress && isLocal(it.address) }
    }

    private val LOCAL_SUFFIXES = listOf(".local", ".lan", ".home", ".home.arpa", ".internal")
}
