package com.malgra.reliquary.core.data.download

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class TransferRateTest {

    @Test fun needsASecondBeforeItKnows() {
        val rate = TransferRate()
        assertNull(rate.add(0, 0))
        assertNull(rate.add(750, 750_000))
        assertEquals(1_000_000, rate.add(1_500, 1_500_000))
    }

    @Test fun averagesOverTheRecentWindowOnly() {
        val rate = TransferRate(windowMillis = 5_000)
        rate.add(0, 0)
        rate.add(1_000, 10_000_000) // a fast start…
        rate.add(6_000, 15_000_000)
        // …then 1 MB/s; the first sample has dropped out of the window.
        assertEquals(1_000_000, rate.add(11_000, 20_000_000))
    }

    @Test fun startsAgainWhenTheDownloadDoes() {
        val rate = TransferRate()
        rate.add(0, 0)
        rate.add(2_000, 4_000_000)
        assertNull(rate.add(3_000, 0)) // restarted from scratch
        assertEquals(2_000_000, rate.add(4_000, 2_000_000))
    }

    @Test fun timeLeftFollowsTheSpeed() {
        assertEquals(10.seconds, timeLeft(remainingBytes = 20_000_000, bytesPerSecond = 2_000_000))
        assertEquals(2.minutes, timeLeft(remainingBytes = 120_000_000, bytesPerSecond = 1_000_000))
        assertNull(timeLeft(remainingBytes = 1_000, bytesPerSecond = null))
        assertNull(timeLeft(remainingBytes = 1_000, bytesPerSecond = 0))
    }
}
