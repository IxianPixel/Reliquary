package com.malgra.reliquary.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/*
 * Catppuccin mapped onto Material 3 colour roles, with Mauve as the accent.
 *
 * Catppuccin has no "container" colours, so containers are Mauve (or Green, Red)
 * mixed into Base. Two choices keep text at 4.5:1 or better on Latte:
 *  - onSurfaceVariant is Subtext1, not Subtext0 (Subtext0 is 4.4:1 on Base).
 *  - tertiary is Latte Green, which is about 3:1 on Base: use it for icons and
 *    indicators, never for small text.
 * Surface containers step from Base through Mantle and Crust to Surface0 (light),
 * and from Crust through Mantle to Surface0 and Surface1 (dark).
 */

private fun tint(accent: Color, base: Color, amount: Float): Color = lerp(base, accent, amount)

internal val LatteColorScheme: ColorScheme = with(Catppuccin.Latte) {
    lightColorScheme(
        primary = mauve,
        onPrimary = base,
        primaryContainer = tint(mauve, base, 0.18f),
        onPrimaryContainer = text,
        inversePrimary = Catppuccin.Mocha.mauve,
        secondary = subtext1,
        onSecondary = base,
        secondaryContainer = tint(mauve, base, 0.18f),
        onSecondaryContainer = text,
        tertiary = green,
        onTertiary = base,
        tertiaryContainer = tint(green, base, 0.18f),
        onTertiaryContainer = text,
        error = red,
        onError = base,
        errorContainer = tint(red, base, 0.18f),
        onErrorContainer = text,
        background = base,
        onBackground = text,
        surface = base,
        onSurface = text,
        surfaceVariant = surface0,
        onSurfaceVariant = subtext1,
        surfaceTint = mauve,
        inverseSurface = Catppuccin.Mocha.surface0,
        inverseOnSurface = Catppuccin.Mocha.text,
        outline = overlay1,
        outlineVariant = surface0,
        scrim = Color.Black,
        surfaceBright = base,
        surfaceDim = crust,
        surfaceContainerLowest = base,
        surfaceContainerLow = mantle,
        surfaceContainer = mantle,
        surfaceContainerHigh = crust,
        surfaceContainerHighest = surface0,
    )
}

internal val MochaColorScheme: ColorScheme = with(Catppuccin.Mocha) {
    darkColorScheme(
        primary = mauve,
        onPrimary = base,
        primaryContainer = tint(mauve, base, 0.25f),
        onPrimaryContainer = text,
        inversePrimary = Catppuccin.Latte.mauve,
        secondary = subtext1,
        onSecondary = base,
        secondaryContainer = tint(mauve, base, 0.25f),
        onSecondaryContainer = text,
        tertiary = green,
        onTertiary = base,
        tertiaryContainer = tint(green, base, 0.2f),
        onTertiaryContainer = text,
        error = red,
        onError = base,
        errorContainer = tint(red, base, 0.25f),
        onErrorContainer = text,
        background = base,
        onBackground = text,
        surface = base,
        onSurface = text,
        surfaceVariant = surface1,
        onSurfaceVariant = subtext1,
        surfaceTint = mauve,
        inverseSurface = Catppuccin.Latte.base,
        inverseOnSurface = Catppuccin.Latte.text,
        outline = overlay1,
        outlineVariant = surface1,
        scrim = Color.Black,
        surfaceBright = surface0,
        surfaceDim = mantle,
        surfaceContainerLowest = crust,
        surfaceContainerLow = mantle,
        surfaceContainer = mantle,
        surfaceContainerHigh = surface0,
        surfaceContainerHighest = surface1,
    )
}

/**
 * Colours drawn over thumbnails and video, which stay the same in both themes:
 * Mocha's brighter accents read better on photographic content.
 */
object OverlayColors {
    val Progress = Catppuccin.Mocha.mauve
    val SponsorSegment = Catppuccin.Mocha.green
    val ProgressTrack = Color.White.copy(alpha = 0.4f)
}
