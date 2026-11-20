package com.malgra.reliquary.core.data.download

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadFilesTest {

    @get:Rule val tmp = TemporaryFolder()

    private val files by lazy { DownloadFiles(FolderDownloadStorage(tmp.root)) }

    @Test fun ordinaryIdsKeepTheirNames() {
        assertEquals("dQw4w9WgXcQ.mp4", files.complete("dQw4w9WgXcQ").name)
    }

    @Test fun windowsDeviceNamesGetAPrefix() {
        // Windows reserves these even with an extension, so CON.mp4 can't be created there.
        assertEquals("_CON.mp4", files.complete("CON").name)
        assertEquals("_nul.mp4", files.complete("nul").name)
        assertEquals("_COM1.mp4", files.complete("COM1").name)
        assertEquals("_LPT9.mp4.part", files.partial("LPT9").name)
        assertEquals("COM10.mp4", files.complete("COM10").name)
    }

    @Test fun deleteRemovesEverythingForTheVideo() {
        files.complete("v").writeText("video")
        files.partial("v").writeText("part")
        files.thumbnail("v").writeText("jpg")
        files.subtitle("v", "en.user").writeText("WEBVTT")
        files.complete("other").writeText("keep")

        files.delete("v")

        assertFalse(files.complete("v").exists())
        assertFalse(files.partial("v").exists())
        assertFalse(files.thumbnail("v").exists())
        assertFalse(files.subtitle("v", "en.user").exists())
        assertTrue(files.complete("other").exists())
    }

    @Test fun aFileThatCantBeDeletedIsLeftWithoutFailing() {
        // A non-empty folder stands in for a file that Windows won't delete while a player has it open.
        val stuck = files.complete("v").apply { mkdirs() }
        File(stuck, "inside").writeText("x")

        files.delete("v")

        assertTrue(stuck.exists())
    }
}
