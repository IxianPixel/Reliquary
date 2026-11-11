package com.malgra.reliquary.feature.player

import android.content.ComponentName
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken

/**
 * A controller for [PlaybackService], connected while the caller is on screen.
 * Null until it connects. The shell holds one and hands it to the player and
 * the mini-player.
 */
@Composable
fun rememberMediaController(): MediaController? {
    val context = LocalContext.current.applicationContext
    var controller by remember { mutableStateOf<MediaController?>(null) }
    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
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

/** The item loaded in [player], updated as it changes. */
@Composable
internal fun currentMediaItemState(player: Player): State<MediaItem?> =
    produceState(player.currentMediaItem, player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = player.currentMediaItem
            }
        }
        player.addListener(listener)
        awaitDispose { player.removeListener(listener) }
    }

/** Playback state, playing flag and error, updated as they change. */
internal data class PlaybackStatus(val state: Int, val isPlaying: Boolean, val error: PlaybackException?)

@Composable
internal fun playbackStatusState(player: Player): State<PlaybackStatus> =
    produceState(PlaybackStatus(player.playbackState, player.isPlaying, player.playerError), player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                value = PlaybackStatus(player.playbackState, player.isPlaying, player.playerError)
            }
        }
        player.addListener(listener)
        awaitDispose { player.removeListener(listener) }
    }
