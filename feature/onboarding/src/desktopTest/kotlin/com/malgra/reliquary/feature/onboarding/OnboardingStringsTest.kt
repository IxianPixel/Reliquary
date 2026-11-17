package com.malgra.reliquary.feature.onboarding

import com.malgra.reliquary.feature.onboarding.resources.Res
import com.malgra.reliquary.feature.onboarding.resources.onboarding_error_other
import com.malgra.reliquary.feature.onboarding.resources.onboarding_server_invalid
import kotlinx.coroutines.runBlocking
import org.jetbrains.compose.resources.getString
import org.junit.Test
import kotlin.test.assertEquals

/** Android's `\'` escapes must still read as apostrophes through Compose resources. */
class OnboardingStringsTest {
    @Test fun apostrophes() = runBlocking {
        assertEquals("That doesn't look like a web address", getString(Res.string.onboarding_server_invalid))
        assertEquals("Couldn't reach the server: timeout", getString(Res.string.onboarding_error_other, "timeout"))
    }
}
