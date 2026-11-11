package com.malgra.reliquary.core.data.library

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MappersTest {
    @Test fun serverDatesParse() {
        assertEquals(1609459200L, parseServerDate("2021-01-01T00:00:00+00:00"))
        assertEquals(1609459200L, parseServerDate("2021-01-01"))
        assertNull(parseServerDate(""))
        assertNull(parseServerDate("soon"))
    }
}
