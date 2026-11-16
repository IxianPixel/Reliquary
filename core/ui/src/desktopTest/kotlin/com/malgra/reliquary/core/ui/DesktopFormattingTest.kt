package com.malgra.reliquary.core.ui

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

/** The desktop stand-ins for Android's Formatter and ICU compact numbers. */
class DesktopFormattingTest {
    @Test fun fileSizesMatchAndroid() {
        assertEquals("512 B", formatFileSize(512, Locale.UK))
        assertEquals("0.95 kB", formatFileSize(950, Locale.UK))
        assertEquals("1.5 kB", formatFileSize(1_500, Locale.UK))
        assertEquals("15 MB", formatFileSize(15_000_000, Locale.UK))
        assertEquals("1.2 GB", formatFileSize(1_234_567_890, Locale.UK))
        assertEquals("250 GB", formatFileSize(250_000_000_000, Locale.UK))
    }

    @Test fun compactCounts() {
        assertEquals("999", formatCompactCount(999, Locale.UK))
        assertEquals("1.2K", formatCompactCount(1_234, Locale.UK))
        assertEquals("12K", formatCompactCount(12_345, Locale.UK))
        assertEquals("123K", formatCompactCount(123_456, Locale.UK))
        assertEquals("1.2M", formatCompactCount(1_234_567, Locale.UK))
    }
}
