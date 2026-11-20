package com.malgra.reliquary.feature.player

import android.content.ComponentName
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * The player, through a controller for [PlaybackService] connected while the
 * caller is on screen. Null until it connects. The shell holds one and hands it
 * to the player sheet.
 */
@Composable
fun rememberVideoPlayer(): VideoPlayer? {
    val controller = rememberMediaController() ?: return null
    return remember(controller) { Media3VideoPlayer(controller) }
}

@Composable
private fun rememberMediaController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(SkipEventsListener)
            .buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

/** Hands the service's SponsorBlock skips to the player screen. */
private object SkipEventsListener : MediaController.Listener {
    override fun onCustomCommand(
        controller: MediaController,
        command: SessionCommand,
        args: Bundle,
    ): ListenableFuture<SessionResult> {
        if (command.customAction == SponsorSkipCommand.ACTION) SponsorSkipCommand.received(args)
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }
}
