package com.malgra.reliquary.core.data

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDirectoriesTest {

    private fun root(os: String, env: Map<String, String> = emptyMap()) =
        AppDirectories.defaultRoot(os = os, home = "/home/u", env = env::get)

    @Test fun macUsesApplicationSupport() {
        assertEquals(File("/home/u/Library/Application Support/Reliquary"), root("Mac OS X"))
    }

    @Test fun windowsUsesLocalAppData() {
        assertEquals(File("C:\\Users\\u\\AppData\\Local", "Reliquary"), root("Windows 11", mapOf("LOCALAPPDATA" to "C:\\Users\\u\\AppData\\Local")))
    }

    @Test fun linuxFollowsXdgAndFallsBackToLocalShare() {
        assertEquals(File("/data/reliquary"), root("Linux", mapOf("XDG_DATA_HOME" to "/data")))
        assertEquals(File("/home/u/.local/share/reliquary"), root("Linux"))
    }

    @Test fun databaseKeepsTheAndroidName() {
        assertEquals("reliquary.db", AppDirectories(File("/x")).database.name)
    }
}
