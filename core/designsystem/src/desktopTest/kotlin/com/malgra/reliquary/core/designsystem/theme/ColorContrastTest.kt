package com.malgra.reliquary.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Test
import kotlin.test.assertTrue

/** Text-bearing colour pairs must meet WCAG AA (4.5:1) in both themes. */
class ColorContrastTest {

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return maxOf(la, lb) / minOf(la, lb)
    }

    private fun textPairs(s: ColorScheme) = mapOf(
        "onSurface/surface" to (s.onSurface to s.surface),
        "onSurfaceVariant/surface" to (s.onSurfaceVariant to s.surface),
        "onSurfaceVariant/surfaceContainerHigh" to (s.onSurfaceVariant to s.surfaceContainerHigh),
        "onSurface/surfaceContainer" to (s.onSurface to s.surfaceContainer),
        "primary/surface" to (s.primary to s.surface),
        "onPrimary/primary" to (s.onPrimary to s.primary),
        "onPrimaryContainer/primaryContainer" to (s.onPrimaryContainer to s.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (s.onSecondaryContainer to s.secondaryContainer),
        "onTertiaryContainer/tertiaryContainer" to (s.onTertiaryContainer to s.tertiaryContainer),
        "error/surface" to (s.error to s.surface),
        "onErrorContainer/errorContainer" to (s.onErrorContainer to s.errorContainer),
        "inverseOnSurface/inverseSurface" to (s.inverseOnSurface to s.inverseSurface),
        "inversePrimary/inverseSurface" to (s.inversePrimary to s.inverseSurface),
    )

    private fun assertReadable(name: String, scheme: ColorScheme) {
        val failures = textPairs(scheme)
            .mapValues { (_, pair) -> contrast(pair.first, pair.second) }
            .filterValues { it < 4.5f }
        assertTrue(failures.isEmpty(), "$name below 4.5:1: $failures")
    }

    @Test fun latteTextIsReadable() = assertReadable("Latte", LatteColorScheme)

    @Test fun mochaTextIsReadable() = assertReadable("Mocha", MochaColorScheme)
}
