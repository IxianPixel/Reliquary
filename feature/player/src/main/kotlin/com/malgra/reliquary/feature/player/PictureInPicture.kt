package com.malgra.reliquary.feature.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons

private const val ACTION_PIP_CONTROL = "com.malgra.reliquary.PIP_CONTROL"
private const val EXTRA_CONTROL = "control"
private const val CONTROL_BACK = 1
private const val CONTROL_PLAY_PAUSE = 2
private const val CONTROL_FORWARD = 3

/** True while the activity is shown in picture-in-picture. */
@Composable
internal fun rememberIsInPictureInPicture(): Boolean {
    val activity = LocalActivity.current as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }
    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }
    return inPip
}

/**
 * Lets the video carry on in a picture-in-picture window when the user leaves
 * the app while it plays ([enabled]): automatically on Android 12 and later, on
 * the Home gesture or button before that. The window has back 10 s, play/pause
 * and forward 10 s buttons; closing it pauses. [sourceBounds] is where the
 * video is on screen, so the window grows out of it.
 */
@Composable
internal fun PictureInPictureSupport(player: Player, enabled: Boolean, sourceBounds: android.graphics.Rect?) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
    val status by playbackStatusState(player)
    val videoSize by videoSizeState(player)
    val canEnter = enabled && status.isPlaying

    val labels = PipLabels(
        back = activity.getString(R.string.player_back_10),
        play = activity.getString(R.string.player_play),
        pause = activity.getString(R.string.player_pause),
        forward = activity.getString(R.string.player_forward_10),
    )
    val params = remember(canEnter, status.isPlaying, videoSize, sourceBounds) {
        pictureInPictureParams(activity, canEnter, status.isPlaying, videoSize, sourceBounds, labels)
    }
    DisposableEffect(params) {
        activity.setPictureInPictureParams(params)
        onDispose { }
    }
    // Off when the player goes away, so leaving the app later doesn't open an empty window.
    DisposableEffect(activity) {
        onDispose {
            activity.setPictureInPictureParams(pictureInPictureParams(activity, false, false, VideoSize.UNKNOWN, null, labels))
        }
    }

    // Before Android 12 there's no auto-enter: go in when the user heads home.
    val currentParams by rememberUpdatedState(params)
    val currentCanEnter by rememberUpdatedState(canEnter)
    DisposableEffect(activity) {
        val onLeave = Runnable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && currentCanEnter) {
                runCatching { activity.enterPictureInPictureMode(currentParams) }
            }
        }
        activity.addOnUserLeaveHintListener(onLeave)
        onDispose { activity.removeOnUserLeaveHintListener(onLeave) }
    }

    // The window's buttons, and pausing when the window is closed.
    DisposableEffect(activity, player) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.getIntExtra(EXTRA_CONTROL, 0)) {
                    CONTROL_BACK -> player.seekBack()
                    CONTROL_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                    CONTROL_FORWARD -> player.seekForward()
                }
            }
        }
        ContextCompat.registerReceiver(
            activity,
            receiver,
            IntentFilter(ACTION_PIP_CONTROL),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        val closed = Consumer<PictureInPictureModeChangedInfo> { info ->
            // Leaving picture-in-picture without coming back to the app means its window was closed.
            if (!info.isInPictureInPictureMode && activity.lifecycle.currentState == Lifecycle.State.CREATED) {
                player.pause()
            }
        }
        activity.addOnPictureInPictureModeChangedListener(closed)
        onDispose {
            activity.unregisterReceiver(receiver)
            activity.removeOnPictureInPictureModeChangedListener(closed)
        }
    }
}

private class PipLabels(val back: String, val play: String, val pause: String, val forward: String)

private fun pictureInPictureParams(
    context: Context,
    canEnter: Boolean,
    playing: Boolean,
    videoSize: VideoSize,
    sourceBounds: android.graphics.Rect?,
    labels: PipLabels,
): PictureInPictureParams {
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(aspectRatio(videoSize))
        .setActions(
            listOf(
                remoteAction(context, CONTROL_BACK, ReliquaryIcons.Replay10, labels.back),
                if (playing) {
                    remoteAction(context, CONTROL_PLAY_PAUSE, ReliquaryIcons.Pause, labels.pause)
                } else {
                    remoteAction(context, CONTROL_PLAY_PAUSE, ReliquaryIcons.Play, labels.play)
                },
                remoteAction(context, CONTROL_FORWARD, ReliquaryIcons.Forward10, labels.forward),
            ),
        )
    sourceBounds?.let(builder::setSourceRectHint)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(canEnter)
        // A video looks better cross-faded than stretched while the window resizes.
        builder.setSeamlessResizeEnabled(false)
    }
    return builder.build()
}

private fun remoteAction(context: Context, control: Int, @DrawableRes icon: Int, label: String): RemoteAction {
    val intent = Intent(ACTION_PIP_CONTROL).setPackage(context.packageName).putExtra(EXTRA_CONTROL, control)
    val pending = PendingIntent.getBroadcast(context, control, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    return RemoteAction(Icon.createWithResource(context, icon), label, label, pending)
}

/** The video's shape, kept within what Android allows for a picture-in-picture window. */
private fun aspectRatio(size: VideoSize): Rational {
    if (size.width <= 0 || size.height <= 0) return Rational(16, 9)
    val ratio = size.width * size.pixelWidthHeightRatio / size.height
    return when {
        ratio > MAX_RATIO -> Rational(239, 100)
        ratio < 1 / MAX_RATIO -> Rational(100, 239)
        else -> Rational((size.width * size.pixelWidthHeightRatio).toInt(), size.height)
    }
}

private const val MAX_RATIO = 2.39f

/** The playing video's size, updated as it changes. */
@Composable
private fun videoSizeState(player: Player): State<VideoSize> =
    produceState(player.videoSize, player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                value = videoSize
            }
        }
        player.addListener(listener)
        awaitDispose { player.removeListener(listener) }
    }
