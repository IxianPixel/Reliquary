package com.malgra.reliquary.feature.player

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn as UnstableOptIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberProgressStateWithTickInterval
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.formatDuration
import com.malgra.reliquary.core.ui.metaLine
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.flow.first

/** Where the player sheet rests. Dismissed is just below the screen; settling there stops playback. */
enum class SheetPosition { Expanded, Collapsed, Dismissed }

/** A video to open, optionally as part of a playlist's queue. */
data class PlayRequest(val videoId: String, val playlistId: String?)

/**
 * The player sheet: one surface that is the full player when expanded and the
 * mini-player above the navigation when collapsed. The video shrinks and grows
 * between the two as it moves, so leaving the player shrinks it into the
 * mini-player.
 */
@Stable
class PlayerSheetState {
    internal val draggable = AnchoredDraggableState(SheetPosition.Collapsed)

    /** The video the expanded player shows; null until something is opened. */
    var request by mutableStateOf<PlayRequest?>(null)
        internal set

    internal var expandRequests by mutableIntStateOf(0)
        private set
    internal var collapseRequests by mutableIntStateOf(0)
        private set

    /** True while the sheet is (or is heading to) fully open. */
    val isExpanded: Boolean
        get() = draggable.targetValue == SheetPosition.Expanded

    /** Opens [videoId] (in [playlistId]'s queue if given) and expands the player. */
    fun open(videoId: String, playlistId: String? = null) {
        request = PlayRequest(videoId, playlistId)
        expandRequests++
    }

    fun collapse() {
        collapseRequests++
    }

    /** Expands onto whatever is playing, e.g. from the mini-player. */
    internal fun expandOnto(item: MediaItem?) {
        if (request == null || request?.videoId != item?.mediaId) {
            request = item?.let { PlayRequest(it.mediaId, it.playlistId) } ?: request
        }
        expandRequests++
    }

    internal fun clear() {
        request = null
    }
}

@Composable
fun rememberPlayerSheetState(): PlayerSheetState = remember { PlayerSheetState() }

private val MiniHeight = 64.dp
private val MiniMargin = 8.dp
private val MiniVideoWidth = 85.dp
private val MiniVideoHeight = 48.dp

/**
 * Draws the sheet over the whole window, when something is playing or opened.
 * [bottomReserve] is the space the navigation takes at the bottom, so the
 * collapsed sheet sits just above it.
 */
@kotlin.OptIn(ExperimentalFoundationApi::class)
@UnstableOptIn(UnstableApi::class) // SURFACE_TYPE_TEXTURE_VIEW
// The full-screen button locks to landscape on purpose; collapsing the player unlocks it.
@SuppressLint("SourceLockedOrientationActivity")
@Composable
fun PlayerSheet(
    state: PlayerSheetState,
    controller: MediaController?,
    bottomReserve: Dp,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val player = controller ?: return
    val nowPlaying by currentMediaItemState(player)
    // The sheet stays composed even with nothing to show, so its positions are always known:
    // a first open that composed it and asked it to expand in the same frame didn't animate.
    val showing = nowPlaying != null || state.request != null
    val inPictureInPicture = rememberIsInPictureInPicture()
    // Where the video is on screen (when the sheet is at rest), for the picture-in-picture window to grow from.
    var videoBounds by remember { mutableStateOf<android.graphics.Rect?>(null) }
    PictureInPictureSupport(player, enabled = showing, sourceBounds = videoBounds)

    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val activity = LocalActivity.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeightPx = constraints.maxHeight.toFloat()
        val collapsedTopPx = with(density) { fullHeightPx - (bottomReserve + MiniHeight + MiniMargin).toPx() }
            .coerceAtLeast(1f)
        val draggable = state.draggable

        LaunchedEffect(fullHeightPx, collapsedTopPx) {
            draggable.updateAnchors(
                DraggableAnchors {
                    SheetPosition.Expanded at 0f
                    SheetPosition.Collapsed at collapsedTopPx
                    SheetPosition.Dismissed at fullHeightPx
                },
            )
        }
        // Expand and collapse only once the positions are known.
        LaunchedEffect(state.expandRequests) {
            if (state.expandRequests == 0) return@LaunchedEffect
            snapshotFlow { draggable.anchors.size }.first { it > 0 }
            draggable.animateTo(SheetPosition.Expanded)
        }
        LaunchedEffect(state.collapseRequests) {
            if (state.collapseRequests == 0) return@LaunchedEffect
            snapshotFlow { draggable.anchors.size }.first { it > 0 }
            draggable.animateTo(SheetPosition.Collapsed)
        }
        // Dragged or tapped open from the mini-player: show what's playing.
        LaunchedEffect(draggable.targetValue) {
            if (draggable.targetValue == SheetPosition.Expanded && state.request == null) state.expandOnto(nowPlaying)
        }
        // Swiped away (or closed): stop, and get ready for next time.
        LaunchedEffect(draggable.settledValue) {
            if (draggable.settledValue == SheetPosition.Dismissed) {
                player.stop()
                player.clearMediaItems()
                state.clear()
                draggable.snapTo(SheetPosition.Collapsed)
            }
        }

        val offsetPx = draggable.offset.takeUnless { it.isNaN() } ?: collapsedTopPx
        // 1 = expanded, 0 = collapsed (or below, while being swiped away).
        val f = (1f - offsetPx / collapsedTopPx).coerceIn(0f, 1f)
        val dismissAlpha = if (offsetPx > collapsedTopPx) {
            (1f - (offsetPx - collapsedTopPx) / (fullHeightPx - collapsedTopPx)).coerceIn(0f, 1f)
        } else {
            1f
        }
        val expanded = draggable.settledValue == SheetPosition.Expanded && f > 0.99f

        if (!showing) return@BoxWithConstraints

        val request = state.request
        val viewModel = request?.let { r ->
            hiltViewModel<PlayerViewModel, PlayerViewModel.Factory>(
                key = "player/${r.videoId}/${r.playlistId}",
                creationCallback = { factory -> factory.create(r.videoId, r.playlistId) },
            )
        }
        val load = viewModel?.let { rememberPlaybackLoad(it, request, player) }

        // In picture-in-picture the window is just the video.
        if (inPictureInPicture) {
            val video by viewModel?.video?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
            VideoArea(
                player = player,
                video = video,
                fullScreen = true,
                onToggleFullScreen = {},
                onMinimise = {},
                interactive = false,
                modifier = Modifier.fillMaxSize(),
            )
            return@BoxWithConstraints
        }

        BackHandler(enabled = expanded && !landscape) { state.collapse() }
        BackHandler(enabled = expanded && landscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        }
        SystemBarsHidden(hidden = expanded && landscape)
        val toggleFullScreen = {
            activity?.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
        // Collapsing the player hands the orientation back to the phone.
        LaunchedEffect(expanded) {
            if (!expanded) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }

        if (expanded && landscape) {
            val video by viewModel?.video?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
            VideoArea(
                player = player,
                video = video,
                fullScreen = true,
                onToggleFullScreen = toggleFullScreen,
                onMinimise = state::collapse,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { videoBounds = it.boundsInWindow().toAndroidRect() },
                onSubtitleChosen = { subtitle ->
                    if (subtitle == null) player.disableSubtitles() else player.selectSubtitle(subtitle.key, subtitle.language)
                    viewModel?.rememberSubtitleLanguage(subtitle?.language)
                },
            )
            SponsorSkipSnackbar(
                player,
                active = true,
                // Above the time row and seek bar.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 72.dp),
            )
            return@BoxWithConstraints
        }

        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullHeight = maxHeight
        val margin = lerp(MiniMargin, 0.dp, f)
        val containerWidth = maxWidth - margin * 2
        val fullVideoHeight = containerWidth * 9f / 16f
        val openLabel = stringResource(R.string.mini_player_open, nowPlaying?.mediaMetadata?.title?.toString().orEmpty())
        val closeLabel = stringResource(R.string.mini_player_close)

        Box(
            Modifier
                .offset { IntOffset(0, offsetPx.roundToInt()) }
                .padding(horizontal = margin)
                .fillMaxWidth()
                .height(lerp(MiniHeight, fullHeight, f))
                .alpha(dismissAlpha)
                .clip(RoundedCornerShape(lerp(16.dp, 0.dp, f)))
                .background(lerp(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.surface, f))
                .anchoredDraggable(draggable, Orientation.Vertical)
                .then(
                    if (f < 0.05f) {
                        Modifier
                            .clickable(onClickLabel = openLabel) { state.expandOnto(nowPlaying) }
                            .semantics {
                                contentDescription = openLabel
                                customActions = listOf(
                                    CustomAccessibilityAction(closeLabel) {
                                        state.dismiss(player)
                                        true
                                    },
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        ) {
            // Status bar backdrop above the video, as on the full player.
            if (f > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusTop * f)
                        .background(Color.Black.copy(alpha = f)),
                )
            }

            // The video: one surface that moves between the mini-player's corner and full width.
            val videoX = lerp(8.dp, 0.dp, f)
            val videoY = lerp(8.dp, statusTop, f)
            val video by viewModel?.video?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(null) }
            Box(
                Modifier
                    .offset(videoX, videoY)
                    .size(width = lerp(MiniVideoWidth, containerWidth, f), height = lerp(MiniVideoHeight, fullVideoHeight, f))
                    .clip(RoundedCornerShape(lerp(8.dp, 0.dp, f)))
                    .onGloballyPositioned { if (f == 0f || f == 1f) videoBounds = it.boundsInWindow().toAndroidRect() },
            ) {
                VideoArea(
                    player = player,
                    video = video,
                    fullScreen = false,
                    onToggleFullScreen = toggleFullScreen,
                    onMinimise = state::collapse,
                    interactive = expanded,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    modifier = Modifier.fillMaxSize(),
                    onSubtitleChosen = { subtitle ->
                        if (subtitle == null) player.disableSubtitles() else player.selectSubtitle(subtitle.key, subtitle.language)
                        viewModel?.rememberSubtitleLanguage(subtitle?.language)
                    },
                )
            }

            if (f < MINI_FADE) {
                MiniRow(
                    player = player,
                    item = nowPlaying,
                    onClose = { state.dismiss(player) },
                    modifier = Modifier
                        .alpha(1f - f / MINI_FADE)
                        .padding(start = MiniVideoWidth + 8.dp + 12.dp)
                        .height(MiniHeight),
                )
                MiniProgress(
                    player,
                    Modifier
                        .align(Alignment.TopStart)
                        .offset(y = MiniHeight - 3.dp)
                        .alpha(1f - f / MINI_FADE),
                )
            }

            if (f > DETAILS_FADE && viewModel != null && load != null) {
                Box(
                    Modifier
                        .offset(y = statusTop + fullVideoHeight)
                        .fillMaxWidth()
                        .height((fullHeight - statusTop - fullVideoHeight).coerceAtLeast(0.dp))
                        .alpha(((f - DETAILS_FADE) / (1f - DETAILS_FADE)).coerceIn(0f, 1f)),
                ) {
                    PlayerDetails(
                        viewModel = viewModel,
                        player = player,
                        unavailable = load.unavailable,
                        onOpenChannel = { channelId ->
                            state.collapse()
                            onOpenChannel(channelId)
                        },
                        onOpenVideo = { videoId -> state.open(videoId) },
                    )
                }
            }
        }

        SponsorSkipSnackbar(
            player,
            active = expanded,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

/** The mini-player's ✕ and the "Close" accessibility action: slide away and stop. */
private fun PlayerSheetState.dismiss(player: Player) {
    player.stop()
    player.clearMediaItems()
    clear()
}

private const val MINI_FADE = 0.3f
private const val DETAILS_FADE = 0.5f

/** Loads the request into the player once, then follows the queue as it moves on. */
private class PlaybackLoad {
    var unavailable by mutableStateOf(false)
}

@Composable
private fun rememberPlaybackLoad(viewModel: PlayerViewModel, request: PlayRequest, player: Player): PlaybackLoad {
    val load = remember(request) { PlaybackLoad() }
    var loaded by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request, player) {
        val current = player.currentMediaItem
        val alreadyPlaying = current?.mediaId == request.videoId &&
            (request.playlistId == null || current.playlistId == request.playlistId)
        if (!alreadyPlaying) {
            val playback = viewModel.playbackRequest()
            if (playback == null) {
                load.unavailable = true
                return@LaunchedEffect
            }
            player.setMediaItems(playback.items, playback.startIndex, playback.startPositionMs)
            // Start with the subtitles last chosen (off unless one was picked).
            val language = viewModel.subtitleLanguage()
            if (language == null) player.disableSubtitles() else player.selectSubtitle(null, language)
            player.prepare()
            player.play()
        }
        loaded = true
    }
    // Follow the queue, so the details match what's playing.
    val nowPlaying by currentMediaItemState(player)
    LaunchedEffect(nowPlaying?.mediaId, loaded) {
        val item = nowPlaying ?: return@LaunchedEffect
        if (loaded) viewModel.onNowPlaying(item.mediaId, item.playlistId)
    }
    return load
}

@Composable
private fun PlayerDetails(
    viewModel: PlayerViewModel,
    player: Player,
    unavailable: Boolean,
    onOpenChannel: (String) -> Unit,
    onOpenVideo: (String) -> Unit,
) {
    val video by viewModel.video.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val download by viewModel.download.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val comments by viewModel.comments.collectAsStateWithLifecycle()
    val similar by viewModel.similar.collectAsStateWithLifecycle()
    val current = video
    when {
        current != null -> Details(
            video = current,
            channel = channel,
            download = download,
            onDownload = viewModel::startDownload,
            onRemoveDownload = viewModel::removeDownload,
            onSetWatched = { watched ->
                if (watched) player.pause()
                viewModel.setWatched(watched)
            },
            onOpenChannel = onOpenChannel,
            queue = queue,
            onPlayQueued = { index ->
                player.seekToDefaultPosition(index)
                player.play()
            },
            modifier = Modifier.fillMaxSize(),
            comments = comments,
            similar = similar,
            onOpenVideo = onOpenVideo,
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

/** Title, time and buttons beside the small video when collapsed. */
@UnstableOptIn(UnstableApi::class) // media3 compose state holders
@Composable
private fun MiniRow(player: Player, item: MediaItem?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val playPause = rememberPlayPauseButtonState(player)
    val progress = rememberProgressStateWithTickInterval(player, 1_000L)
    val duration = progress.durationMs.coerceAtLeast(0)
    Box(modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .fillMaxHeight()
                .padding(end = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    item?.mediaMetadata?.title?.toString().orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    metaLine(
                        item?.mediaMetadata?.artist?.toString(),
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
                    contentDescription = stringResource(if (playPause.showPlay) R.string.player_play else R.string.player_pause),
                )
            }
            IconButton(onClick = onClose) {
                Icon(painterResource(ReliquaryIcons.Close), contentDescription = stringResource(R.string.mini_player_close))
            }
        }
    }
}

/** The mini-player's progress line along its bottom edge. */
@UnstableOptIn(UnstableApi::class)
@Composable
private fun MiniProgress(player: Player, modifier: Modifier = Modifier) {
    val progress = rememberProgressStateWithTickInterval(player, 1_000L)
    val duration = progress.durationMs.coerceAtLeast(0)
    if (duration <= 0) return
    Box(
        modifier
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

/** Whether the sheet is on screen (something is playing or opened), e.g. to leave room for it. */
@Composable
fun isPlayerSheetShowing(state: PlayerSheetState, controller: MediaController?): Boolean {
    val player = controller ?: return false
    val nowPlaying by currentMediaItemState(player)
    return nowPlaying != null || state.request != null
}
