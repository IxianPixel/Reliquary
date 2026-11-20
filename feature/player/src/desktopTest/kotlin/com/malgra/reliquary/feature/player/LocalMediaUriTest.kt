package com.malgra.reliquary.feature.player

import java.io.File
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalMediaUriTest {
    @Test fun aPlainPathIsAFileUriWithThreeSlashes() {
        val file = File("/media/a.mp4")
        val uri = localMediaUri(file)

        assertTrue(uri.startsWith("file:///"), uri)
        assertEquals(file.absoluteFile.toPath(), Paths.get(java.net.URI(uri)))
    }

    @Test fun spacesAndNonAsciiAreEncodedAndDecodeBackToThePath() {
        val path = "/media/Ünï dir/clip #1 (é).mp4"
        val uri = localMediaUri(File(path))

        assertTrue(uri.startsWith("file:///"), uri)
        assertFalse(uri.contains(' '), uri)
        assertEquals(File(path).absoluteFile.toPath(), Paths.get(java.net.URI(uri)))
    }
}
