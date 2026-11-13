package com.malgra.reliquary.feature.player

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.media3.common.Player
import kotlinx.coroutines.flow.collectLatest

/**
 * "Skipped a sponsor segment · Undo" for each skip the playback service makes
 * while the full player is on screen ([active]). Undo goes back to the start of
 * the segment, which then plays (the service leaves a segment you go back to).
 */
@Composable
internal fun SponsorSkipSnackbar(player: Player, active: Boolean, modifier: Modifier = Modifier) {
    val host = remember { SnackbarHostState() }
    val isActive by rememberUpdatedState(active)
    val sponsorMessage = stringResource(R.string.player_skipped_sponsor)
    val segmentMessage = stringResource(R.string.player_skipped_segment)
    val undo = stringResource(R.string.player_skip_undo)

    LaunchedEffect(player) {
        SponsorSkips.events.collectLatest { segment ->
            if (!isActive) return@collectLatest
            val videoId = player.currentMediaItem?.mediaId
            val result = host.showSnackbar(
                message = if (segment.isSponsor) sponsorMessage else segmentMessage,
                actionLabel = undo,
                duration = SnackbarDuration.Short,
            )
            // Only while the same video is playing: a skip to the end may have moved the queue on.
            if (result == SnackbarResult.ActionPerformed && player.currentMediaItem?.mediaId == videoId) {
                player.seekTo(segment.start.inWholeMilliseconds)
            }
        }
    }
    LaunchedEffect(active) {
        if (!active) host.currentSnackbarData?.dismiss()
    }
    SnackbarHost(host, modifier)
}
