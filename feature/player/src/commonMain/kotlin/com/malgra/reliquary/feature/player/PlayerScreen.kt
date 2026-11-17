package com.malgra.reliquary.feature.player

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.designsystem.theme.Catppuccin
import com.malgra.reliquary.core.designsystem.theme.OverlayColors
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.Download
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.SponsorSegment
import com.malgra.reliquary.core.model.Subtitle
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.ui.ChannelAvatar
import com.malgra.reliquary.core.ui.ServerImage
import com.malgra.reliquary.core.ui.VideoListItem
import com.malgra.reliquary.core.ui.compactCount
import com.malgra.reliquary.core.ui.formatDate
import com.malgra.reliquary.core.ui.fileSizeText
import com.malgra.reliquary.core.ui.formatDuration
import com.malgra.reliquary.core.ui.metaLine
import com.malgra.reliquary.core.ui.resources.Res as UiRes
import com.malgra.reliquary.core.ui.resources.ui_subscribers
import com.malgra.reliquary.feature.player.resources.Res
import com.malgra.reliquary.feature.player.resources.player_back_10
import com.malgra.reliquary.feature.player.resources.player_download
import com.malgra.reliquary.feature.player.resources.player_download_retry
import com.malgra.reliquary.feature.player.resources.player_download_waiting
import com.malgra.reliquary.feature.player.resources.player_downloaded
import com.malgra.reliquary.feature.player.resources.player_downloading
import com.malgra.reliquary.feature.player.resources.player_downloading_unknown
import com.malgra.reliquary.feature.player.resources.player_error
import com.malgra.reliquary.feature.player.resources.player_exit_full_screen
import com.malgra.reliquary.feature.player.resources.player_forward_10
import com.malgra.reliquary.feature.player.resources.player_full_screen
import com.malgra.reliquary.feature.player.resources.player_keep
import com.malgra.reliquary.feature.player.resources.player_less
import com.malgra.reliquary.feature.player.resources.player_mark_watched
import com.malgra.reliquary.feature.player.resources.player_minimise
import com.malgra.reliquary.feature.player.resources.player_more
import com.malgra.reliquary.feature.player.resources.player_next_in
import com.malgra.reliquary.feature.player.resources.player_pause
import com.malgra.reliquary.feature.player.resources.player_pending_note
import com.malgra.reliquary.feature.player.resources.player_play
import com.malgra.reliquary.feature.player.resources.player_published
import com.malgra.reliquary.feature.player.resources.player_remove
import com.malgra.reliquary.feature.player.resources.player_remove_download_body
import com.malgra.reliquary.feature.player.resources.player_remove_download_title
import com.malgra.reliquary.feature.player.resources.player_seek
import com.malgra.reliquary.feature.player.resources.player_similar
import com.malgra.reliquary.feature.player.resources.player_subtitle_auto
import com.malgra.reliquary.feature.player.resources.player_subtitles
import com.malgra.reliquary.feature.player.resources.player_subtitles_off
import com.malgra.reliquary.feature.player.resources.player_synced_note
import com.malgra.reliquary.feature.player.resources.player_time
import com.malgra.reliquary.feature.player.resources.player_unavailable
import com.malgra.reliquary.feature.player.resources.player_watched
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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

/** Where [this] segments sit along a video [durationMs] long, as 0..1 fractions. */
private fun List<SponsorSegment>.toFractions(durationMs: Long): List<ClosedFloatingPointRange<Float>> {
    if (durationMs <= 0) return emptyList()
    return map { segment ->
        val from = (segment.start.inWholeMilliseconds.toFloat() / durationMs).coerceIn(0f, 1f)
        val to = (segment.end.inWholeMilliseconds.toFloat() / durationMs).coerceIn(from, 1f)
        from..to
    }
}

/** The seek bar's touch height. */
private val SeekBarHeight = 24.dp

/** Space between captions and the bottom edge (and its progress line) while the controls are hidden. */
private val CaptionGap = 16.dp

private val CompactControls = ControlSizes(
    play = 56.dp, playIcon = 26.dp, playCornerPaused = 28.dp, playCornerPlaying = 16.dp,
    skip = 40.dp, skipIcon = 24.dp, gap = 20.dp, track = 3.dp, knob = 12.dp,
)

private val FullScreenControls = ControlSizes(
    play = 72.dp, playIcon = 32.dp, playCornerPaused = 36.dp, playCornerPlaying = 22.dp,
    skip = 48.dp, skipIcon = 30.dp, gap = 28.dp, track = 4.dp, knob = 14.dp,
)

/**
 * The video with its controls. While not [interactive] (the player sheet is
 * collapsed or moving) it's just the picture: no controls, and taps pass to
 * the sheet. [smoothResize] is for a video that changes size as the sheet moves.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoArea(
    player: VideoPlayer?,
    video: Video?,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onMinimise: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    smoothResize: Boolean = false,
    onSubtitleChosen: (Subtitle?) -> Unit = {},
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    // Pointer movement over the video (desktop): each restarts the controls' timer.
    var pointerMoves by remember { mutableIntStateOf(0) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (interactive) {
                    Modifier
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            controlsVisible = !controlsVisible
                        }
                        .pointerActivity(hideCursor = fullScreen && !controlsVisible) {
                            controlsVisible = true
                            pointerMoves++
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
        val status by playerStatusState(player)
        val progress by progressState(player, PROGRESS_TICK_MS)
        // Where a drag on the seek bar is, 0..1; the controls stay up while it's set.
        var scrub by remember { mutableStateOf<Float?>(null) }
        val sizes = if (fullScreen) FullScreenControls else CompactControls
        KeepScreenOn(status.isPlaying)
        LaunchedEffect(controlsVisible, status.isPlaying, scrub != null, pointerMoves) {
            if (controlsVisible && status.isPlaying && scrub == null) {
                delay(CONTROLS_TIMEOUT_MS)
                controlsVisible = false
            }
        }

        VideoSurface(player, Modifier.fillMaxSize(), smoothResize)
        if (!interactive) return@Box

        if (status.state == PlaybackState.Buffering) {
            LoadingIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(sizes.play))
        }
        status.error?.let { error ->
            Text(
                stringResource(if (error == PlayerError.Unavailable) Res.string.player_unavailable else Res.string.player_error),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            )
        }

        val duration = progress.durationMs.coerceAtLeast(0)
        val played = if (duration > 0) (progress.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val showControls = controlsVisible || !status.isPlaying || scrub != null

        // Captions keep clear of the progress line, and move up past the time row and seek bar while they show.
        val captionLift by animateDpAsState(
            if (showControls) sizes.skip + SeekBarHeight else CaptionGap,
            label = "captionLift",
        )
        SubtitleOverlay(player, Modifier.fillMaxSize().padding(bottom = captionLift))

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
                showCenter = status.state != PlaybackState.Buffering && status.error == null,
                fullScreen = fullScreen,
                positionMs = scrub?.let { (it * duration).toLong() } ?: progress.positionMs,
                durationMs = duration,
                fraction = scrub ?: played,
                onScrub = { scrub = it.coerceIn(0f, 1f) },
                onScrubEnd = {
                    scrub?.let { player.seekTo((it * duration).toLong()) }
                    scrub = null
                },
                onToggleFullScreen = onToggleFullScreen,
                onMinimise = onMinimise,
                subtitles = video?.subtitles.orEmpty(),
                segments = video?.sponsorSegments.orEmpty().toFractions(duration),
                onSubtitleChosen = onSubtitleChosen,
            )
        }
    }
}

/** The subtitles button and its menu: Off, then each track, with the one showing ticked. */
@Composable
private fun SubtitleButton(
    player: VideoPlayer,
    subtitles: List<Subtitle>,
    size: Dp,
    onChosen: (Subtitle?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val selected by selectedSubtitleState(player)
    Box(modifier) {
        IconButton(onClick = { open = true }, modifier = Modifier.size(size)) {
            Icon(
                painterResource(ReliquaryIcons.Subtitles),
                contentDescription = stringResource(Res.string.player_subtitles),
                tint = Color.White,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SubtitleMenuItem(stringResource(Res.string.player_subtitles_off), selected == null) {
                open = false
                onChosen(null)
            }
            subtitles.forEach { subtitle ->
                val label = if (subtitle.autoGenerated) {
                    stringResource(Res.string.player_subtitle_auto, subtitle.name)
                } else {
                    subtitle.name
                }
                SubtitleMenuItem(label, selected == subtitle.key) {
                    open = false
                    onChosen(subtitle)
                }
            }
        }
    }
}

@Composable
private fun SubtitleMenuItem(label: String, checked: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = {
            if (checked) {
                Icon(painterResource(ReliquaryIcons.Check), contentDescription = null)
            } else {
                Spacer(Modifier.size(24.dp))
            }
        },
        modifier = Modifier.semantics { selected = checked },
    )
}

@Composable
private fun Controls(
    player: VideoPlayer,
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
    subtitles: List<Subtitle>,
    onSubtitleChosen: (Subtitle?) -> Unit,
    segments: List<ClosedFloatingPointRange<Float>>,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        if (subtitles.isNotEmpty()) {
            SubtitleButton(
                player = player,
                subtitles = subtitles,
                size = sizes.skip,
                onChosen = onSubtitleChosen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
        if (!fullScreen) {
            IconButton(onClick = onMinimise, modifier = Modifier.padding(4.dp).size(sizes.skip)) {
                Icon(
                    painterResource(ReliquaryIcons.ChevronDown),
                    contentDescription = stringResource(Res.string.player_minimise),
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
                        contentDescription = stringResource(Res.string.player_back_10),
                        tint = Color.White,
                        modifier = Modifier.size(sizes.skipIcon),
                    )
                }
                PlayPauseButton(player, sizes)
                IconButton(onClick = player::seekForward, modifier = Modifier.size(sizes.skip)) {
                    Icon(
                        painterResource(ReliquaryIcons.Forward10),
                        contentDescription = stringResource(Res.string.player_forward_10),
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
                        Res.string.player_time,
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
                            if (fullScreen) Res.string.player_exit_full_screen else Res.string.player_full_screen,
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
                segments = segments,
                // Full width along the video's edge in portrait; inset in full screen.
                modifier = Modifier.padding(horizontal = if (fullScreen) 16.dp else 0.dp),
            )
        }
    }
}

/** Round while paused, a squarer shape while playing, as in the mockup. */
@Composable
private fun PlayPauseButton(player: VideoPlayer, sizes: ControlSizes) {
    val showPlay by showPlayState(player)
    val corner by animateDpAsState(
        if (showPlay) sizes.playCornerPaused else sizes.playCornerPlaying,
        label = "play-shape",
    )
    Surface(
        onClick = player::togglePlay,
        shape = RoundedCornerShape(corner),
        color = OverlayColors.Progress,
        contentColor = Catppuccin.Mocha.base,
        modifier = Modifier.size(sizes.play),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (showPlay) ReliquaryIcons.Play else ReliquaryIcons.Pause),
                contentDescription = stringResource(if (showPlay) Res.string.player_play else Res.string.player_pause),
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
    segments: List<ClosedFloatingPointRange<Float>> = emptyList(),
) {
    val label = stringResource(Res.string.player_seek)
    var width by remember { mutableIntStateOf(1) }
    val knobInset = knob / 2
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(SeekBarHeight)
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
            // SponsorBlock segments, drawn over the progress as in the mockup.
            for (segment in segments) {
                val from = start + (end - start) * segment.start
                val to = start + (end - start) * segment.endInclusive
                drawLine(OverlayColors.SponsorSegment, Offset(from, y), Offset(to, y), stroke, StrokeCap.Butt)
            }
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
    comments: CommentsState = CommentsState.Loading,
    similar: List<Video> = emptyList(),
    onOpenVideo: (String) -> Unit = {},
) {
    var showingComments by rememberSaveable(video.id) { mutableStateOf(false) }
    val loadedComments = (comments as? CommentsState.Loaded)?.takeIf { it.comments.isNotEmpty() }
    if (showingComments && loadedComments != null) {
        CommentsSheet(loadedComments, onDismiss = { showingComments = false })
    }
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
                        video.published?.let { stringResource(Res.string.player_published, formatDate(it)) },
                        video.mediaSizeBytes.takeIf { it > 0 }?.let { fileSizeText(it) },
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
                        stringResource(if (video.watched) Res.string.player_watched else Res.string.player_mark_watched),
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
                    stringResource(if (video.syncPending) Res.string.player_pending_note else Res.string.player_synced_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // Only when the server saved some; offline or none, there's nothing to show.
        if (loadedComments != null) {
            item(key = "comments") { CommentsRow(loadedComments, onOpen = { showingComments = true }) }
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
                    stringResource(Res.string.player_next_in, queue!!.name),
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
        if (similar.isNotEmpty()) {
            item(key = "similar-header") {
                Text(
                    stringResource(Res.string.player_similar),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
            }
            items(similar, key = { "similar-${it.id}" }) { other ->
                VideoListItem(other, onClick = { onOpenVideo(other.id) }, modifier = Modifier.bleedHorizontally(16.dp))
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
            Text(stringResource(Res.string.player_downloaded), modifier = Modifier.padding(start = 8.dp))
        }
        DownloadStatus.Queued, DownloadStatus.Downloading, DownloadStatus.Paused -> {
            // The cross and the label say it: tapping cancels.
            OutlinedButton(onClick = onRemove) {
                Icon(painterResource(ReliquaryIcons.Close), contentDescription = null, modifier = iconModifier)
                val percent = download.fraction?.let { (it * 100).toInt() }
                Text(
                    when {
                        download.status != DownloadStatus.Downloading -> stringResource(Res.string.player_download_waiting)
                        percent != null -> stringResource(Res.string.player_downloading, percent)
                        else -> stringResource(Res.string.player_downloading_unknown)
                    },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        DownloadStatus.Failed, null -> OutlinedButton(onClick = onDownload) {
            Icon(painterResource(ReliquaryIcons.Downloads), contentDescription = null, modifier = iconModifier)
            Text(
                stringResource(if (download == null) Res.string.player_download else Res.string.player_download_retry),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text(stringResource(Res.string.player_remove_download_title)) },
            text = { Text(stringResource(Res.string.player_remove_download_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    onRemove()
                }) { Text(stringResource(Res.string.player_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(Res.string.player_keep)) }
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
                    stringResource(UiRes.string.ui_subscribers, compactCount(it)),
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
                stringResource(if (expanded) Res.string.player_less else Res.string.player_more),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val CONTROLS_TIMEOUT_MS = 3_000L
private const val PROGRESS_TICK_MS = 250L
