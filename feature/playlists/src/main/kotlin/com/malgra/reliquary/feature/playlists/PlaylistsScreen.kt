package com.malgra.reliquary.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.NumberedVideoItem
import com.malgra.reliquary.core.ui.PlaylistListItem
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.metaLine
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader
import com.malgra.reliquary.core.ui.videoCountText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val playlists = viewModel.playlists.collectAsLazyPagingItems()

    PullToRefreshBox(
        isRefreshing = playlists.isRefreshing,
        onRefresh = playlists::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = WindowInsets.statusBars.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
            item(key = "title", contentType = "title") {
                Text(
                    stringResource(R.string.playlists_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            pagingHeader(playlists) { EmptyPanel(stringResource(R.string.playlists_empty)) }
            items(
                count = playlists.itemCount,
                key = playlists.itemKey { it.id },
                contentType = playlists.itemContentType { "playlist" },
            ) { index ->
                playlists[index]?.let { playlist -> PlaylistListItem(playlist, onClick = { onOpenPlaylist(playlist.id) }) }
            }
            pagingFooter(playlists)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(
        key = playlistId,
        creationCallback = { factory -> factory.create(playlistId) },
    ),
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val videos = viewModel.videos.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp),
        ) {
            Icon(painterResource(ReliquaryIcons.Back), contentDescription = stringResource(R.string.playlist_back))
        }
        PullToRefreshBox(
            isRefreshing = videos.isRefreshing,
            onRefresh = {
                viewModel.refresh()
                videos.refresh()
            },
            modifier = Modifier.weight(1f),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "header", contentType = "header") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column {
                            Text(
                                playlist?.name.orEmpty(),
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 28.sp, lineHeight = 34.sp),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.semantics { heading() },
                            )
                            playlist?.let { p ->
                                Text(
                                    metaLine(
                                        p.channelName,
                                        if (p.archivedCount < p.entryCount) {
                                            pluralStringResource(R.plurals.playlist_archived_of, p.entryCount, p.archivedCount, p.entryCount)
                                        } else {
                                            videoCountText(p.entryCount)
                                        },
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        Row {
                            val first = videos.itemSnapshotList.firstOrNull()
                            Button(onClick = { first?.let { onOpenVideo(it.id) } }, enabled = first != null) {
                                Icon(
                                    painterResource(ReliquaryIcons.Play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Text(
                                    stringResource(R.string.playlist_play_all),
                                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                )
                            }
                        }
                    }
                }
                pagingHeader(videos) { EmptyPanel(stringResource(R.string.playlist_empty)) }
                items(
                    count = videos.itemCount,
                    key = videos.itemKey { it.id },
                    contentType = videos.itemContentType { "video" },
                ) { index ->
                    videos[index]?.let { video ->
                        NumberedVideoItem(index + 1, video, onClick = { onOpenVideo(video.id) })
                    }
                }
                pagingFooter(videos)
            }
        }
    }
}
