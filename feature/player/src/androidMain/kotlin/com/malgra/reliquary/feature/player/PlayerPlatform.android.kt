package com.malgra.reliquary.feature.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.malgra.reliquary.core.ui.rememberTwoPanes

private val VideoPlayer.media3: Player get() = (this as Media3VideoPlayer).player

@OptIn(UnstableApi::class) // ContentFrame
@Composable
internal actual fun VideoSurface(player: VideoPlayer, modifier: Modifier, smoothResize: Boolean) {
    ContentFrame(player.media3, modifier, if (smoothResize) SURFACE_TYPE_TEXTURE_VIEW else SURFACE_TYPE_SURFACE_VIEW)
}

/**
 * The current subtitle cues, drawn by media3's SubtitleView so they follow the
 * phone's caption settings (Settings › Accessibility › Captions).
 */
@OptIn(UnstableApi::class)
@Composable
internal actual fun SubtitleOverlay(player: VideoPlayer, modifier: Modifier) {
    val media3 = player.media3
    val cues by produceState(media3.currentCues.cues, media3) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                value = cueGroup.cues
            }
        }
        media3.addListener(listener)
        awaitDispose { media3.removeListener(listener) }
    }
    AndroidView(
        factory = { context ->
            SubtitleView(context).apply {
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        },
        update = { view -> view.setCues(cues) },
        modifier = modifier,
    )
}

@Composable
internal actual fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides the status and navigation bars (swipe to peek) while [hidden]. */
@Composable
internal actual fun SystemBarsHidden(hidden: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(hidden, window) {
        val insets = WindowCompat.getInsetsController(window, view)
        if (hidden) {
            insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insets.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { insets.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
internal actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/**
 * Full screen is landscape. The full-screen button locks to landscape on
 * purpose; collapsing the player hands the orientation back to the phone.
 *
 * A tablet doesn't go full screen: it keeps the details beside the video, in
 * either orientation, so there's no full-screen button there.
 */
@SuppressLint("SourceLockedOrientationActivity")
@Composable
internal actual fun rememberFullScreen(expanded: Boolean): FullScreen {
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val tablet = LocalConfiguration.current.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP
    val twoPanes = rememberTwoPanes()
    val tabletPanes = tablet && twoPanes
    val activity = LocalActivity.current
    LaunchedEffect(expanded) {
        if (!expanded) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    return object : FullScreen {
        override val isFullScreen = landscape && !tabletPanes
        override val canToggle = !tabletPanes

        override fun toggle() {
            activity?.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        override fun exit() {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
    }
}

/** The narrowest a tablet's screen gets (sw600dp); a phone is narrower in both orientations. */
private const val TABLET_MIN_WIDTH_DP = 600

// A tap toggles the controls on a phone; there's no pointer to follow.
internal actual fun Modifier.pointerActivity(hideCursor: Boolean, onMove: () -> Unit): Modifier = this
