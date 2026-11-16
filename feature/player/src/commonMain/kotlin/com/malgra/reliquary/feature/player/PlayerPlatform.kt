package com.malgra.reliquary.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

// What the shared player screens need from each platform.

/**
 * The picture. [smoothResize] suits a surface that changes size as the sheet
 * moves (a TextureView on Android); otherwise the platform's cheapest surface.
 */
@Composable
internal expect fun VideoSurface(player: VideoPlayer, modifier: Modifier, smoothResize: Boolean)

/** The current subtitle cues. Android draws them in the phone's caption style. */
@Composable
internal expect fun SubtitleOverlay(player: VideoPlayer, modifier: Modifier)

/** Keeps the screen awake while [on]. */
@Composable
internal expect fun KeepScreenOn(on: Boolean)

/** Hides the system bars while [hidden] (Android's full screen). */
@Composable
internal expect fun SystemBarsHidden(hidden: Boolean)

/** [onBack] runs on Back (Android) or Escape (desktop) while [enabled]. */
@Composable
internal expect fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit)

/**
 * Full screen: landscape on a phone, the whole window on desktop. Leaving the
 * expanded player ([expanded] false) leaves it.
 */
internal interface FullScreen {
    val isFullScreen: Boolean
    fun toggle()
    fun exit()
}

@Composable
internal expect fun rememberFullScreen(expanded: Boolean): FullScreen

/** Whether the app is showing as a picture-in-picture window (Android only). */
@Composable
internal expect fun rememberIsInPictureInPicture(): Boolean

/**
 * Lets the app go picture-in-picture while [enabled] and playing, growing the
 * window from [sourceBounds] (the video, in window pixels). Nothing on desktop.
 */
@Composable
internal expect fun PictureInPictureSupport(player: VideoPlayer, enabled: Boolean, sourceBounds: Rect?)
