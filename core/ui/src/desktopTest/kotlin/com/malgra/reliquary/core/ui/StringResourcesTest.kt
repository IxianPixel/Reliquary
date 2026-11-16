package com.malgra.reliquary.core.ui

import com.malgra.reliquary.core.ui.resources.Res
import com.malgra.reliquary.core.ui.resources.keep_offline_estimate
import com.malgra.reliquary.core.ui.resources.ui_added_on
import com.malgra.reliquary.core.ui.resources.ui_meta_separator
import com.malgra.reliquary.core.ui.resources.ui_video_count
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Test
import kotlin.test.assertEquals

/** Strings moved from Android resources must read the same through Compose resources. */
class StringResourcesTest {
    @Test fun quotedWhitespaceIsKept() = runBlocking {
        assertEquals(" · ", getString(Res.string.ui_meta_separator))
    }

    @Test fun placeholdersAreFilled() = runBlocking {
        assertEquals("Added 3 Mar 2026", getString(Res.string.ui_added_on, "3 Mar 2026"))
        assertEquals("1 video", getPluralString(Res.plurals.ui_video_count, 1, 1))
        assertEquals("5 videos", getPluralString(Res.plurals.ui_video_count, 5, 5))
        assertEquals(
            "About 1.2 GB for 3 videos, based on file sizes on the server",
            getPluralString(Res.plurals.keep_offline_estimate, 3, "1.2 GB", 3),
        )
    }
}
