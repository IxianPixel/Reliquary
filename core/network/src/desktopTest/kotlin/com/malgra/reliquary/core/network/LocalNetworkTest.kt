package com.malgra.reliquary.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.net.InetAddress
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalNetworkTest {

    private fun local(ip: String) = LocalNetwork.isLocal(InetAddress.getByName(ip).address)

    @Test fun privateAndLinkLocalAddressesAreLocal() {
        listOf("10.0.0.5", "172.16.0.1", "172.31.255.1", "192.168.1.20", "169.254.3.3", "fd12::1", "fe80::1")
            .forEach { assertTrue(local(it), it) }
    }

    @Test fun publicAndLoopbackAddressesAreNot() {
        // 100.64.0.0/10 is Tailscale's range, which Android 17 doesn't treat as local.
        listOf("8.8.8.8", "172.32.0.1", "100.87.202.108", "100.128.0.1", "127.0.0.1", "2606:4700::1111", "::1")
            .forEach { assertFalse(local(it), it) }
    }

    @Test fun namesOnlyALocalNetworkAnswers() {
        listOf("nas.local", "archive.lan", "ta.home.arpa", "box.internal", "tubearchivist").forEach {
            assertTrue(LocalNetwork.isLocalName(it), it)
        }
        listOf("archive.example.com", "localhost", "ta.localhost").forEach {
            assertFalse(LocalNetwork.isLocalName(it), it)
        }
    }

    @Test fun addressesAreCheckedWithoutALookup() = runTest {
        assertTrue(LocalNetwork.needsLocalNetwork("192.168.1.20"))
        assertFalse(LocalNetwork.needsLocalNetwork("127.0.0.1"))
        assertFalse(LocalNetwork.needsLocalNetwork("8.8.8.8"))
    }
}
