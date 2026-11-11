package app.reliquary.core.network

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TaVersionTest {
    @Test fun parsesReleaseStrings() {
        assertEquals(TaVersion(0, 6, 0), TaVersion.parse("v0.6.0"))
        assertEquals(TaVersion(0, 5, 12), TaVersion.parse("0.5.12"))
        assertEquals(TaVersion(1, 0, 0), TaVersion.parse("v1.0"))
        assertEquals(TaVersion(0, 6, 1), TaVersion.parse("v0.6.1-unstable"))
        assertNull(TaVersion.parse("unknown"))
    }

    @Test fun comparesNumerically() {
        assertTrue(TaVersion(0, 5, 12) < TaVersion.MINIMUM)
        assertTrue(TaVersion(0, 6, 0) >= TaVersion.MINIMUM)
        assertTrue(TaVersion(0, 10, 0) > TaVersion(0, 9, 9))
    }
}
