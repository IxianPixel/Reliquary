package com.malgra.reliquary.feature.player

import android.text.format.Formatter
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.designsystem.theme.Catppuccin
import com.malgra.reliquary.core.designsystem.theme.OverlayColors
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.Download
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.ui.ChannelAvatar
import com.malgra.reliquary.core.ui.ServerImage
import com.malgra.reliquary.core.ui.VideoListItem
import com.malgra.reliquary.core.ui.compactCount
import com.malgra.reliquary.core.ui.formatDate
import com.malgra.reliquary.core.ui.formatDuration
import com.malgra.reliquary.core.ui.metaLine
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import androidx.annotation.OptIn as UnstableOptIn
import com.malgra.reliquary.core.ui.R as UiR

/** Hides the status and navigation bars (swipe to peek) while [hidden]. */
@Composable
internal fun SystemBarsHidden(hidden: Boolean) {
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

/** Control sizes: compact over the small portrait video, roomier full screen. */
private class ControlSizes(
    val play: Dp,
    val playIcon: Dp,
    val playCornerPaused: Dp,
    val playCornerPlaying: Dp,
    val skip: Dp,
    val skipIcon: Dp,
    val gap: Dp,
    val track: Dp,
    val knob: Dp,
)

private val CompactControls = ControlSizes(
    play = 56.dp, playIcon = 26.dp, playCornerPaused = 28.dp, playCornerPlaying = 16.dp,
    skip = 40.dp, skipIcon = 24.dp, gap = 20.dp, track = 3.dp, knob = 12.dp,
)

private val FullScreenControls = ControlSizes(
    play = 72.dp, playIcon = 32.dp, playCornerPaused = 36.dp, playCornerPlaying = 22.dp,
    skip = 48.dp, skipIcon = 30.dp, gap = 28.dp, track = 4.dp, knob = 14.dp,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@UnstableOptIn(UnstableApi::class) // ContentFrame, progress state
@Composable
/**
 * The video with its controls. While not [interactive] (the player sheet is
 * collapsed or moving) it's just the picture: no controls, and taps pass to
 * the sheet. [surfaceType] TEXTURE_VIEW resizes smoothly as the sheet moves.
 */
internal fun VideoArea(
    player: Player?,
    video: Video?,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onMinimise: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    surfaceType: Int = SURFACE_TYPE_SURFACE_VIEW,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (interactive) {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        controlsVisible = !controlsVisible
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        if (player == null) {
            ServerImage(url = video?.thumbnailUrl, modifier = Modifier.fillMaxSize())
            return@Box
        }
        val status by playbackStatusState(player)
        val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
        // Where a drag on the seek bar is, 0..1; the controls stay up while it's set.
        var scrub by remember { mutableStateOf<Float?>(null) }
        val sizes = if (fullScreen) FullScreenControls else CompactControls
        val view = LocalView.current
        DisposableEffect(status.isPlaying) {
            view.keepScreenOn = status.isPlaying
            onDispose { view.keepScreenOn = false }
        }
        LaunchedEffect(controlsVisible, status.isPlaying, scrub != null) {
            if (controlsVisible && status.isPlaying && scrub == null) {
                delay(CONTROLS_TIMEOUT_MS)
                controlsVisible = false
            }
        }

        ContentFrame(player, Modifier.fillMaxSize(), surfaceType)
        if (!interactive) return@Box

        if (status.state == Player.STATE_BUFFERING) {
            LoadingIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(sizes.play))
        }
        status.error?.let {
            Text(
                stringResource(R.string.player_error),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        val duration = progress.durationMs.coerceAtLeast(0)
        val played = if (duration > 0) (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val showControls = controlsVisible || !status.isPlaying || scrub != null

        // With the controls hidden, a hairline still shows how far in you are.
        if (!showControls && !fullScreen && duration > 0) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.3f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(played)
                        .height(2.dp)
                        .background(OverlayColors.Progress),
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                player = player,
                sizes = sizes,
                showCenter = status.state != Player.STATE_BUFFERING && status.error == null,
                fullScreen = fullScreen,
                positionMs = scrub?.let { (it * duration).toLong() } ?: progress.currentPositionMs,
                durationMs = duration,
                fraction = scrub ?: played,
                onScrub = { scrub = it.coerceIn(0f, 1f) },
                onScrubEnd = {
                    scrub?.let { player.seekTo((it * duration).toLong()) }
                    scrub = null
                },
                onToggleFullScreen = onToggleFullScreen,
                onMinimise = onMinimise,
            )
        }
    }
}

@Composable
private fun Controls(
    player: Player,
    sizes: ControlSizes,
    showCenter: Boolean,
    fullScreen: Boolean,
    positionMs: Long,
    durationMs: Long,
    fraction: Float,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleFullScreen: () -> Unit,
    onMinimise: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        if (!fullScreen) {
            IconButton(onClick = onMinimise, modifier = Modifier.padding(4.dp).size(sizes.skip)) {
                Icon(
                    painterResource(ReliquaryIcons.ChevronDown),
                    contentDescription = stringResource(R.string.player_minimise),
                    tint = Color.White,
                )
            }
        }
        if (showCenter) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(sizes.gap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                IconButton(onClick = player::seekBack, modifier = Modifier.size(sizes.skip)) {
                    Icon(
                        painterResource(ReliquaryIcons.Replay10),
                        contentDescription = stringResource(R.string.player_back_10),
                        tint = Color.White,
                        modifier = Modifier.size(sizes.skipIcon),
                    )
                }
                PlayPauseButton(player, sizes)
                IconButton(onClick = player::seekForward, modifier = Modifier.size(sizes.skip)) {
                    Icon(
                        painterResource(ReliquaryIcons.Forward10),
                        contentDescription = stringResource(R.string.player_forward_10),
                        tint = Color.White,
                        modifier = Modifier.size(sizes.skipIcon),
                    )
                }
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = if (fullScreen) 16.dp else 12.dp, end = 4.dp),
            ) {
                Text(
                    stringResource(
                        R.string.player_time,
                        formatDuration(positionMs.coerceAtLeast(0).milliseconds),
                        formatDuration(durationMs.milliseconds),
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFullScreen, modifier = Modifier.size(sizes.skip)) {
                    Icon(
                        painterResource(if (fullScreen) ReliquaryIcons.FullscreenExit else ReliquaryIcons.Fullscreen),
                        contentDescription = stringResource(
                            if (fullScreen) R.string.player_exit_full_screen else R.string.player_full_screen,
                        ),
                        tint = Color.White,
                    )
                }
            }
            SeekBar(
                fraction = fraction,
                enabled = durationMs > 0,
                track = sizes.track,
                knob = sizes.knob,
                onScrub = onScrub,
                onScrubEnd = onScrubEnd,
                // Full width along the video's edge in portrait; inset in full screen.
                modifier = Modifier.padding(horizontal = if (fullScreen) 16.dp else 0.dp),
            )
        }
    }
}

/** Round while paused, a squarer shape while playing, as in the mockup. */
@UnstableOptIn(UnstableApi::class)
@Composable
private fun PlayPauseButton(player: Player, sizes: ControlSizes) {
    val state = rememberPlayPauseButtonState(player)
    val corner by animateDpAsState(
        if (state.showPlay) sizes.playCornerPaused else sizes.playCornerPlaying,
        label = "play-shape",
    )
    Surface(
        onClick = state::onClick,
        enabled = state.isEnabled,
        shape = RoundedCornerShape(corner),
        color = OverlayColors.Progress,
        contentColor = Catppuccin.Mocha.base,
        modifier = Modifier.size(sizes.play),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (state.showPlay) ReliquaryIcons.Play else ReliquaryIcons.Pause),
                contentDescription = stringResource(if (state.showPlay) R.string.player_play else R.string.player_pause),
                modifier = Modifier.size(sizes.playIcon),
            )
        }
    }
}

/**
 * A slim seek bar: a thin track with a small knob, in a 24dp-tall touch area.
 * Tap to jump or drag to scrub; [onScrubEnd] commits the seek. TalkBack can
 * read and set the position.
 */
@Composable
private fun SeekBar(
    fraction: Float,
    enabled: Boolean,
    track: Dp,
    knob: Dp,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(R.string.player_seek)
    var width by remember { mutableIntStateOf(1) }
    val knobInset = knob / 2
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .onSizeChanged { width = it.width.coerceAtLeast(1) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onScrub(offset.x / width)
                    onScrubEnd()
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onScrub(offset.x / width) },
                    onDragEnd = onScrubEnd,
                    onDragCancel = onScrubEnd,
                ) { change, _ ->
                    change.consume()
                    onScrub(change.position.x / width)
                }
            }
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                if (enabled) {
                    setProgress { target ->
                        onScrub(target)
                        onScrubEnd()
                        true
                    }
                }
            },
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(knob),
        ) {
            val inset = knobInset.toPx()
            val start = inset
            val end = size.width - inset
            val x = start + (end - start) * fraction.coerceIn(0f, 1f)
            val y = size.height / 2
            val stroke = track.toPx()
            drawLine(Color.White.copy(alpha = 0.3f), Offset(start, y), Offset(end, y), stroke, StrokeCap.Round)
            drawLine(OverlayColors.Progress, Offset(start, y), Offset(x, y), stroke, StrokeCap.Round)
            if (enabled) drawCircle(OverlayColors.Progress, radius = knob.toPx() / 2, center = Offset(x, y))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Details(
    video: Video,
    channel: Channel?,
    download: Download?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onSetWatched: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
    queue: PlayQueue?,
    onPlayQueued: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "title") {
            Column {
                Text(
                    video.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    metaLine(
                        video.published?.let { stringResource(R.string.player_published, formatDate(it)) },
                        video.mediaSizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DownloadButton(download, onDownload, onRemoveDownload)
                ToggleButton(checked = video.watched, onCheckedChange = onSetWatched) {
                    Icon(
                        painterResource(ReliquaryIcons.CheckCircle),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(if (video.watched) R.string.player_watched else R.string.player_mark_watched),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
        if (video.channelId.isNotEmpty()) {
            item(key = "channel") {
                ChannelRow(video, channel, onClick = { onOpenChannel(video.channelId) })
            }
        }
        if (!video.description.isNullOrBlank()) {
            item(key = "description") { Description(video.description!!) }
        }
        item(key = "sync") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(ReliquaryIcons.Sync),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    stringResource(if (video.syncPending) R.string.player_pending_note else R.string.player_synced_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // "Next in" the playlist, as in the mockup: the next few, tap to jump.
        val current = queue?.videos?.indexOfFirst { it.id == video.id } ?: -1
        val upNext = if (queue != null && current >= 0) {
            queue.videos.withIndex().drop(current + 1).take(UP_NEXT_COUNT)
        } else {
            emptyList()
        }
        if (upNext.isNotEmpty()) {
            item(key = "next-header") {
                Text(
                    stringResource(R.string.player_next_in, queue!!.name),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(upNext, key = { "next-${it.value.id}" }) { (index, next) ->
                VideoListItem(
                    next,
                    onClick = { onPlayQueued(index) },
                    // Edge to edge like other lists, past the column's padding.
                    modifier = Modifier.bleedHorizontally(16.dp),
                )
            }
        }
    }
}

private const val UP_NEXT_COUNT = 5

/** Lets an item extend [amount] past its parent's horizontal padding on both sides. */
private fun Modifier.bleedHorizontally(amount: Dp) = layout { measurable, constraints ->
    val extra = (amount * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra,
            maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth + extra else constraints.maxWidth,
        ),
    )
    layout(placeable.width - extra, placeable.height) { placeable.place(-amount.roundToPx(), 0) }
}

/** Download, progress (tap to cancel), or Downloaded (tap to remove, after asking). */
@Composable
private fun DownloadButton(download: Download?, onDownload: () -> Unit, onRemove: () -> Unit) {
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val iconModifier = Modifier.size(18.dp)
    when (download?.status) {
        DownloadStatus.Completed -> FilledTonalButton(onClick = { confirmRemove = true }) {
            Icon(painterResource(ReliquaryIcons.Check), contentDescription = null, modifier = iconModifier)
            Text(stringResource(R.string.player_downloaded), modifier = Modifier.padding(start = 8.dp))
        }
        DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Paused -> {
            // The cross and the label say it: tapping cancels.
            OutlinedButton(onClick = onRemove) {
                Icon(painterResource(ReliquaryIcons.Close), contentDescription = null, modifier = iconModifier)
                val percent = download.fraction?.let { (it * 100).toInt() }
                Text(
                    when {
                        download.status != DownloadStatus.Downloading -> stringResource(R.string.player_download_waiting)
                        percent != null -> stringResource(R.string.player_downloading, percent)
                        else -> stringResource(R.string.player_downloading_unknown)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        DownloadStatus.Failed, null -> OutlinedButton(onClick = onDownload) {
            Icon(painterResource(ReliquaryIcons.Downloads), contentDescription = null, modifier = iconModifier)
            Text(
                stringResource(if (download == null) R.string.player_download else R.string.player_download_retry),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(R.string.player_remove_download_title)) },
            text = { Text(stringResource(R.string.player_remove_download_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(R.string.player_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.player_keep)) }
            },
        )
    }
}

@Composable
private fun ChannelRow(video: Video, channel: Channel?, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    ) {
        ChannelAvatar(url = channel?.thumbnailUrl, name = video.channelName, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(video.channelName, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp))
            channel?.subscriberCount?.takeIf { it > 0 }?.let {
                Text(
                    stringResource(UiR.string.ui_subscribers, compactCount(it)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            painterResource(ReliquaryIcons.ChevronRight),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(
        onClick = { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(if (expanded) R.string.player_less else R.string.player_more),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val PROGRESS_TICK_MS = 250L
