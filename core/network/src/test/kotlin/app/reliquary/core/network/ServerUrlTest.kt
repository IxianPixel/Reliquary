package app.reliquary.core.network

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ServerUrlTest {
    private fun norm(input: String) = ServerUrl.normalize(input)?.toString()

    @Test fun keepsExplicitScheme() {
        assertEquals("http://example.com/", norm("http://example.com"))
        assertEquals("https://example.com/", norm("https://example.com/"))
    }

    @Test fun localAddressesDefaultToHttp() {
        assertEquals("http://192.168.1.20:8000/", norm("192.168.1.20:8000"))
        assertEquals("http://10.0.2.2:8000/", norm("10.0.2.2:8000"))
        assertEquals("http://nas.local/", norm("nas.local"))
        assertEquals("http://archive.home.arpa/", norm("archive.home.arpa"))
        assertEquals("http://100.101.1.2/", norm("100.101.1.2"))
    }

    @Test fun publicHostsDefaultToHttps() {
        assertEquals("https://ta.example.com/", norm("ta.example.com"))
        assertEquals("https://8.8.8.8/", norm("8.8.8.8"))
    }

    @Test fun keepsSubPathAndDropsQuery() {
        assertEquals("https://example.com/tube/", norm("https://example.com/tube/?x=1#y"))
        assertEquals("https://example.com/tube/", norm("example.com/tube"))
    }

    @Test fun trimsWhitespace() {
        assertEquals("https://example.com/", norm("  example.com  "))
    }

    @Test fun rejectsGarbage() {
        assertNull(norm(""))
        assertNull(norm("not a url"))
        assertNull(norm("http://"))
    }

    @Test fun originOmitsDefaultPort() {
        assertEquals("https://example.com", ServerUrl.origin(ServerUrl.normalize("https://example.com:443/x")!!))
        assertEquals("http://nas.local:8000", ServerUrl.origin(ServerUrl.normalize("nas.local:8000")!!))
    }
}
