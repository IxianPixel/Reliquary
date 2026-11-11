package com.malgra.reliquary.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.Channel
import com.malgra.reliquary.core.model.Playlist
import com.malgra.reliquary.core.model.Video
import com.malgra.reliquary.core.model.WatchFilter
import com.malgra.reliquary.core.ui.ChannelAvatar
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.PlaylistListItem
import com.malgra.reliquary.core.ui.R as UiR
import com.malgra.reliquary.core.ui.ServerImage
import com.malgra.reliquary.core.ui.VideoListItem
import com.malgra.reliquary.core.ui.compactCount
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.metaLine
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader
import com.malgra.reliquary.core.ui.videoCountText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel<ChannelViewModel, ChannelViewModel.Factory>(
        key = channelId,
        creationCallback = { factory -> factory.create(channelId) },
    ),
) {
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val videoCount by viewModel.videoCount.collectAsStateWithLifecycle()
    val videos = viewModel.videos.collectAsLazyPagingItems()
    val playlists = viewModel.playlists.collectAsLazyPagingItems()

    val tabs = channel?.tabs.available()
    LaunchedEffect(tabs, selection.tab) {
        if (selection.tab !in tabs) viewModel.selectTab(tabs.first())
    }
    val showingPlaylists = selection.tab == ChannelTab.Playlists

    PullToRefreshBox(
        isRefreshing = if (showingPlaylists) playlists.isRefreshing else videos.isRefreshing,
        onRefresh = {
            viewModel.refresh()
            if (showingPlaylists) playlists.refresh() else videos.refresh()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header", contentType = "header") {
                ChannelHeader(channel, videoCount, onBack)
            }
            if (tabs.size > 1) {
                item(key = "tabs", contentType = "tabs") {
                    ChannelTabRow(tabs, selection.tab, viewModel::selectTab)
                }
            }
            if (showingPlaylists) {
                playlistItems(playlists, onOpenPlaylist)
            } else {
                item(key = "filter", contentType = "filter") {
                    FilterRow(selection.filter, viewModel::selectFilter)
                }
                videoItems(videos, selection.filter, onOpenVideo)
            }
        }
    }
}

private fun LazyListScope.videoItems(
    videos: LazyPagingItems<Video>,
    filter: WatchFilter,
    onOpenVideo: (String) -> Unit,
) {
    pagingHeader(videos) {
        EmptyPanel(
            stringResource(
                if (filter == WatchFilter.Unwatched) R.string.channel_empty_unwatched else R.string.channel_empty_videos,
            ),
        )
    }
    items(
        count = videos.itemCount,
        key = videos.itemKey { "video-${it.id}" },
        contentType = videos.itemContentType { "video" },
    ) { index ->
        videos[index]?.let { video -> VideoListItem(video, onClick = { onOpenVideo(video.id) }, showChannel = false) }
    }
    pagingFooter(videos)
}

private fun LazyListScope.playlistItems(playlists: LazyPagingItems<Playlist>, onOpenPlaylist: (String) -> Unit) {
    pagingHeader(playlists) { EmptyPanel(stringResource(R.string.channel_empty_playlists)) }
    items(
        count = playlists.itemCount,
        key = playlists.itemKey { "playlist-${it.id}" },
        contentType = playlists.itemContentType { "playlist" },
    ) { index ->
        playlists[index]?.let { playlist ->
            PlaylistListItem(playlist, onClick = { onOpenPlaylist(playlist.id) }, showChannel = false)
        }
    }
    pagingFooter(playlists)
}

/** Banner across the top (behind the status bar), with the avatar overlapping its lower edge. */
@Composable
private fun ChannelHeader(channel: Channel?, videoCount: Int?, onBack: () -> Unit) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bannerHeight = 132.dp + statusBar
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bannerHeight)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        ) {
            ServerImage(url = channel?.bannerUrl, modifier = Modifier.fillMaxSize())
        }
        FilledIconButton(
            onClick = onBack,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.35f),
                contentColor = Color.White,
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .size(40.dp),
        ) {
            Icon(painterResource(ReliquaryIcons.Back), contentDescription = stringResource(R.string.channel_back))
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = bannerHeight - 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelAvatar(
                url = channel?.thumbnailUrl,
                name = channel?.name.orEmpty(),
                modifier = Modifier
                    .size(72.dp)
                    .border(4.dp, MaterialTheme.colorScheme.surface, CircleShape),
            )
            Column {
                Text(
                    text = channel?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 26.sp, lineHeight = 32.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )
                val meta = metaLine(
                    videoCount?.let { videoCountText(it) },
                    channel?.subscriberCount?.takeIf { it > 0 }
                        ?.let { stringResource(UiR.string.ui_subscribers, compactCount(it)) },
                    if (channel?.subscribed == true) stringResource(UiR.string.ui_subscribed) else null,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelTabRow(tabs: List<ChannelTab>, selected: ChannelTab, onSelect: (ChannelTab) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(selected).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = {
                    Text(
                        stringResource(
                            when (tab) {
                                ChannelTab.Videos -> R.string.channel_tab_videos
                                ChannelTab.Streams -> R.string.channel_tab_streams
                                ChannelTab.Shorts -> R.string.channel_tab_shorts
                                ChannelTab.Playlists -> R.string.channel_tab_playlists
                            },
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilterRow(selected: WatchFilter, onSelect: (WatchFilter) -> Unit) {
    val label = stringResource(R.string.channel_filter)
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
            .semantics { contentDescription = label },
    ) {
        WatchFilter.entries.forEachIndexed { index, filter ->
            ToggleButton(
                checked = filter == selected,
                onCheckedChange = { onSelect(filter) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    WatchFilter.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                Text(
                    stringResource(
                        when (filter) {
                            WatchFilter.All -> R.string.channel_filter_all
                            WatchFilter.Unwatched -> R.string.channel_filter_unwatched
                        },
                    ),
                )
            }
        }
    }
}
