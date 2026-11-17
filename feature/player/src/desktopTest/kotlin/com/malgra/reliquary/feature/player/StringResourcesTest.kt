package com.malgra.reliquary.feature.player

import com.malgra.reliquary.feature.player.resources.Res
import com.malgra.reliquary.feature.player.resources.player_comment_likes
import com.malgra.reliquary.feature.player.resources.player_downloading
import com.malgra.reliquary.feature.player.resources.player_time
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.junit.Test
import kotlin.test.assertEquals

/** Strings moved from Android resources must read the same through Compose resources. */
class StringResourcesTest {
    @Test fun percentSignsAreLiteral() = runBlocking {
        assertEquals("Downloading 42%", getString(Res.string.player_downloading, 42))
    }

    @Test fun placeholdersAreFilled() = runBlocking {
        assertEquals("1:02 / 10:00", getString(Res.string.player_time, "1:02", "10:00"))
        assertEquals("1.2K likes", getPluralString(Res.plurals.player_comment_likes, 1200, "1.2K"))
    }
}
