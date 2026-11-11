package com.malgra.reliquary.feature.player

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn as UnstableOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.ServerImage
import com.malgra.reliquary.core.ui.formatDuration
import com.malgra.reliquary.core.ui.metaLine
import kotlin.time.Duration.Companion.milliseconds

/**
 * The bar above the navigation while something is loaded and the player isn't
 * showing. Tapping it opens the player; the cross stops playback.
 */
@UnstableOptIn(UnstableApi::class) // media3 compose state holders
@Composable
fun MiniPlayer(player: Player, onOpen: (videoId: String) -> Unit, modifier: Modifier = Modifier) {
    val item by currentMediaItemState(player)
    val current = item ?: return
    val title = current.mediaMetadata.title?.toString().orEmpty()
    val openLabel = stringResource(R.string.mini_player_open, title)
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, 1_000L)
    val duration = progress.durationMs.coerceAtLeast(0)

    Surface(
        onClick = { onOpen(current.mediaId) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .height(64.dp)
            .semantics { contentDescription = openLabel },
    ) {
        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(start = 8.dp, end = 4.dp),
            ) {
                ServerImage(
                    url = current.mediaMetadata.artworkUri?.toString(),
                    modifier = Modifier
                        .size(width = 85.dp, height = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        metaLine(
                            current.mediaMetadata.artist?.toString(),
                            if (duration > 0) {
                                stringResource(
                                    R.string.player_time,
                                    formatDuration(progress.currentPositionMs.milliseconds),
                                    formatDuration(duration.milliseconds),
                                )
                            } else {
                                null
                            },
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = playPause::onClick, enabled = playPause.isEnabled) {
                    Icon(
                        painterResource(if (playPause.showPlay) ReliquaryIcons.Play else ReliquaryIcons.Pause),
                        contentDescription = stringResource(
                            if (playPause.showPlay) R.string.player_play else R.string.player_pause,
                        ),
                    )
                }
                IconButton(onClick = {
                    player.stop()
                    player.clearMediaItems()
                }) {
                    Icon(painterResource(ReliquaryIcons.Close), contentDescription = stringResource(R.string.mini_player_close))
                }
            }
            if (duration > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}
