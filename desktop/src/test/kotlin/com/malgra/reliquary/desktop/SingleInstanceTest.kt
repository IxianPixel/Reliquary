package com.malgra.reliquary.desktop

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingleInstanceTest {

    // A short path: Unix socket paths are limited to about 100 characters.
    @get:Rule val folder = TemporaryFolder(java.io.File("/tmp").takeIf { it.isDirectory })

    @Test
    fun secondCopyAsksTheFirstToShowAndQuits() {
        val first = assertNotNull(SingleInstance.acquire(folder.root.toPath()))
        val shown = CountDownLatch(1)
        first.onShowRequest = { shown.countDown() }

        assertNull(SingleInstance.acquire(folder.root.toPath()))
        assertTrue(shown.await(5, TimeUnit.SECONDS))
        first.close()
    }

    @Test
    fun lockIsFreeAgainAfterClose() {
        SingleInstance.acquire(folder.root.toPath())!!.close()
        SingleInstance.acquire(folder.root.toPath())!!.close()
    }
}
