package com.malgra.reliquary.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * Reliquary's theme: Catppuccin Latte in light mode and Mocha in dark mode.
 * [dynamicColor] switches to the wallpaper-based Material You palette on
 * Android 12+; it is an opt-in setting and off by default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReliquaryTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = (if (dynamicColor) dynamicColorScheme(darkTheme) else null)
        ?: if (darkTheme) MochaColorScheme else LatteColorScheme

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** The wallpaper-based palette, or null where the platform has none. */
@Composable
internal expect fun dynamicColorScheme(darkTheme: Boolean): ColorScheme?
