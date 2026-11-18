package com.malgra.reliquary.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.coroutines.delay
import org.jetbrains.skia.Bitmap
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

/** The app window's state, which full screen changes. The desktop app provides it. */
val LocalPlayerWindowState = compositionLocalOf<WindowState?> { null }

private val VideoPlayer.desktop: DesktopVideoPlayer get() = this as DesktopVideoPlayer

/** libVLC's latest picture, copied into a bitmap once per display frame and drawn letterboxed. */
@Composable
internal actual fun VideoSurface(player: VideoPlayer, modifier: Modifier, smoothResize: Boolean) {
    val desktop = player.desktop
    val bitmap = remember { Bitmap() }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    var drawn by remember { mutableLongStateOf(0L) }
    LaunchedEffect(desktop) {
        var shown = -1L
        while (true) {
            val frames = desktop.frames
            if (frames == null || frames.version == shown) {
                // Nothing new (paused, or not started): don't keep the UI drawing.
                delay(FRAME_POLL_MS)
                continue
            }
            withFrameNanos { }
            val version = frames.version
            if (frames.copyInto(bitmap)) {
                shown = version
                image = bitmap.asComposeImageBitmap()
                drawn++
            }
        }
    }
    DisposableEffect(bitmap) { onDispose { bitmap.close() } }
    Canvas(modifier) {
        drawn // a new picture redraws
        val picture = image ?: return@Canvas
        val scale = minOf(size.width / picture.width, size.height / picture.height)
        val width = (picture.width * scale).roundToInt()
        val height = (picture.height * scale).roundToInt()
        drawImage(
            picture,
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** The cue showing, drawn over the bottom of the video. */
@Composable
internal actual fun SubtitleOverlay(player: VideoPlayer, modifier: Modifier) {
    val desktop = player.desktop
    val text by produceState<String?>(null, desktop) {
        while (true) {
            value = desktop.cues.textAt(desktop.positionMs)
            delay(CUE_TICK_MS)
        }
    }
    Box(modifier, contentAlignment = Alignment.BottomCenter) {
        text?.let {
            Text(
                it,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

// Not on desktop yet: it needs a power assertion per OS.
@Composable
internal actual fun KeepScreenOn(on: Boolean) = Unit

@Composable
internal actual fun SystemBarsHidden(hidden: Boolean) = Unit

/** Escape, the mouse's back button and the shell's other Back keys reach this. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) = BackHandler(enabled, onBack)

/** Full screen is the window's; leaving it restores the window as it was, as does leaving the expanded player. */
@Composable
internal actual fun rememberFullScreen(expanded: Boolean): FullScreen {
    val window = LocalPlayerWindowState.current
    var before by remember { mutableStateOf(WindowPlacement.Floating) }
    val isFullScreen = window?.placement == WindowPlacement.Fullscreen
    LaunchedEffect(expanded) {
        if (!expanded && window?.placement == WindowPlacement.Fullscreen) window.placement = before
    }
    return object : FullScreen {
        override val isFullScreen = isFullScreen
        override val canToggle = true

        override fun toggle() {
            if (isFullScreen) exit() else enter()
        }

        override fun exit() {
            window?.placement = before
        }

        private fun enter() {
            val window = window ?: return
            before = window.placement
            window.placement = WindowPlacement.Fullscreen
        }
    }
}

@Composable
internal actual fun rememberIsInPictureInPicture(): Boolean = false

@Composable
internal actual fun PictureInPictureSupport(player: VideoPlayer, enabled: Boolean, sourceBounds: Rect?) = Unit

private const val CUE_TICK_MS = 100L
private const val FRAME_POLL_MS = 5L

/** Moving the pointer over the video shows the controls; the cursor goes with them when [hideCursor]. */
internal actual fun Modifier.pointerActivity(hideCursor: Boolean, onMove: () -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) onMove()
            }
        }
    }.then(if (hideCursor) Modifier.pointerHoverIcon(HiddenCursor) else Modifier)

/** A blank cursor: AWT has none built in. */
private val HiddenCursor: PointerIcon by lazy {
    PointerIcon(
        Toolkit.getDefaultToolkit().createCustomCursor(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), Point(0, 0), "hidden"),
    )
}
