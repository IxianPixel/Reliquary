package com.malgra.reliquary.feature.player

import androidx.media3.common.util.UnstableApi
import androidx.annotation.OptIn as UnstableOptIn
import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.ContentFrame
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
import com.malgra.reliquary.core.ui.R as UiR
import com.malgra.reliquary.core.ui.ServerImage
import com.malgra.reliquary.core.ui.compactCount
import com.malgra.reliquary.core.ui.formatDate
import com.malgra.reliquary.core.ui.formatDuration
import com.malgra.reliquary.core.ui.metaLine
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The player: video on top and details below in portrait; the video alone,
 * full screen, in landscape. Playback lives in [PlaybackService], so leaving
 * this screen keeps it going in the mini-player.
 */
// The full-screen button locks to landscape on purpose; leaving the player unlocks it.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PlayerScreen(
    videoId: String,
    controller: MediaController?,
    onBack: () -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel<PlayerViewModel, PlayerViewModel.Factory>(
        key = videoId,
        creationCallback = { factory -> factory.create(videoId) },
    ),
) {
    val video by viewModel.video.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    var unavailable by remember { mutableStateOf(false) }

    // Load this video unless it's already the one playing (e.g. opened from the mini-player).
    LaunchedEffect(controller, videoId) {
        val player = controller ?: return@LaunchedEffect
        if (player.currentMediaItem?.mediaId == videoId) return@LaunchedEffect
        val request = viewModel.playbackRequest()
        if (request == null) {
            unavailable = true
            return@LaunchedEffect
        }
        player.setMediaItem(request.item, request.startPositionMs)
        player.prepare()
        player.play()
    }

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current
    SystemBarsHidden(hidden = landscape)
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val toggleFullScreen = {
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    BackHandler(enabled = landscape) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
    }

    if (landscape) {
        VideoArea(
            player = controller,
            video = video,
            fullScreen = true,
            onToggleFullScreen = toggleFullScreen,
            onMinimise = onBack,
            modifier = modifier.fillMaxSize(),
        )
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(Modifier.background(Color.Black).statusBarsPadding()) {
            VideoArea(
                player = controller,
                video = video,
                fullScreen = false,
                onToggleFullScreen = toggleFullScreen,
                onMinimise = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
        }
        when {
            video != null -> Details(
                video = video!!,
                channel = channel,
                download = download,
                onDownload = viewModel::startDownload,
                onRemoveDownload = viewModel::removeDownload,
                onSetWatched = { watched ->
                    if (watched) controller?.pause()
                    viewModel.setWatched(watched)
                },
                onOpenChannel = onOpenChannel,
                modifier = Modifier.weight(1f),
            )
            unavailable -> Text(
                stringResource(R.string.player_not_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
            )
        }
    }
}

/** Hides the status and navigation bars (swipe to peek) while [hidden]. */
@Composable
private fun SystemBarsHidden(hidden: Boolean) {
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@UnstableOptIn(UnstableApi::class) // ContentFrame
@Composable
private fun VideoArea(
    player: Player?,
    video: Video?,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onMinimise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var controlsVisible by rememberSaveable { mutableStateOf(true) }
    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                controlsVisible = !controlsVisible
            },
    ) {
        if (player == null) {
            ServerImage(url = video?.thumbnailUrl, modifier = Modifier.fillMaxSize())
            return@Box
        }
        val status by playbackStatusState(player)
        val view = LocalView.current
        DisposableEffect(status.isPlaying) {
            view.keepScreenOn = status.isPlaying
            onDispose { view.keepScreenOn = false }
        }
        LaunchedEffect(controlsVisible, status.isPlaying) {
            if (controlsVisible && status.isPlaying) {
                delay(CONTROLS_TIMEOUT_MS)
                controlsVisible = false
            }
        }

        ContentFrame(player, Modifier.fillMaxSize())

        if (status.state == Player.STATE_BUFFERING) {
            LoadingIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(64.dp))
        }
        status.error?.let {
            Text(
                stringResource(R.string.player_error),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        AnimatedVisibility(
            visible = controlsVisible || !status.isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Controls(
                player = player,
                showCenter = status.state != Player.STATE_BUFFERING && status.error == null,
                fullScreen = fullScreen,
                onToggleFullScreen = onToggleFullScreen,
                onMinimise = onMinimise,
            )
        }
    }
}

@Composable
private fun Controls(
    player: Player,
    showCenter: Boolean,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    onMinimise: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f)),
    ) {
        if (!fullScreen) {
            IconButton(onClick = onMinimise, modifier = Modifier.padding(4.dp)) {
                Icon(
                    painterResource(ReliquaryIcons.ChevronDown),
                    contentDescription = stringResource(R.string.player_minimise),
                    tint = Color.White,
                )
            }
        }
        if (showCenter) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.Center),
            ) {
                IconButton(onClick = player::seekBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(ReliquaryIcons.Replay10),
                        contentDescription = stringResource(R.string.player_back_10),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
                PlayPauseButton(player)
                IconButton(onClick = player::seekForward, modifier = Modifier.size(48.dp)) {
                    Icon(
                        painterResource(ReliquaryIcons.Forward10),
                        contentDescription = stringResource(R.string.player_forward_10),
                        tint = Color.White,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        SeekBar(
            player = player,
            fullScreen = fullScreen,
            onToggleFullScreen = onToggleFullScreen,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
        )
    }
}

/** Round while paused, a squarer shape while playing, as in the mockup. */
@UnstableOptIn(UnstableApi::class)
@Composable
private fun PlayPauseButton(player: Player) {
    val state = rememberPlayPauseButtonState(player)
    val corner by animateDpAsState(if (state.showPlay) 36.dp else 22.dp, label = "play-shape")
    Surface(
        onClick = state::onClick,
        enabled = state.isEnabled,
        shape = RoundedCornerShape(corner),
        color = OverlayColors.Progress,
        contentColor = Catppuccin.Mocha.base,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painterResource(if (state.showPlay) ReliquaryIcons.Play else ReliquaryIcons.Pause),
                contentDescription = stringResource(if (state.showPlay) R.string.player_play else R.string.player_pause),
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

@UnstableOptIn(UnstableApi::class)
@Composable
private fun SeekBar(
    player: Player,
    fullScreen: Boolean,
    onToggleFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = rememberProgressStateWithTickInterval(player, PROGRESS_TICK_MS)
    val duration = progress.durationMs.coerceAtLeast(0)
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (duration > 0) (progress.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownPosition = dragging?.let { (it * duration).toLong() } ?: progress.currentPositionMs
    val seekLabel = stringResource(R.string.player_seek)

    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = fraction,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { player.seekTo((it * duration).toLong()) }
                dragging = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = OverlayColors.Progress,
                activeTrackColor = OverlayColors.Progress,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp)
                .semantics { contentDescription = seekLabel },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(
                    R.string.player_time,
                    formatDuration(shownPosition.coerceAtLeast(0).milliseconds),
                    formatDuration(duration.milliseconds),
                ),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleFullScreen) {
                Icon(
                    painterResource(if (fullScreen) ReliquaryIcons.FullscreenExit else ReliquaryIcons.Fullscreen),
                    contentDescription = stringResource(
                        if (fullScreen) R.string.player_exit_full_screen else R.string.player_full_screen,
                    ),
                    tint = Color.White,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Details(
    video: Video,
    channel: Channel?,
    download: Download?,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onSetWatched: (Boolean) -> Unit,
    onOpenChannel: (String) -> Unit,
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
    }
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
