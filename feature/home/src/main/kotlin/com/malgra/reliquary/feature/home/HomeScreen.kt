package com.malgra.reliquary.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.SectionHeader
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.VideoCard
import com.malgra.reliquary.core.ui.VideoGridItem
import com.malgra.reliquary.core.ui.VideoListItem
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSearch: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val continueWatching by viewModel.continueWatching.collectAsStateWithLifecycle()
    val onDevice by viewModel.onDevice.collectAsStateWithLifecycle()
    val offline by viewModel.isOffline.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val recent = viewModel.recentlyAdded.collectAsLazyPagingItems()

    PullToRefreshBox(
        isRefreshing = recent.isRefreshing,
        onRefresh = {
            recent.refresh()
            viewModel.refreshContinueWatching()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(
            contentPadding = WindowInsets.statusBars.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "top-bar", contentType = "top-bar") {
                HomeTopBar(onOpenSearch = onOpenSearch, onOpenSettings = onOpenSettings)
            }
            if (offline) {
                item(key = "offline", contentType = "banner") {
                    OfflineBanner(downloaded = onDevice.size, pendingSync = pendingSync)
                }
            }

            // Offline, only what can play: videos that are downloaded.
            val resumable = if (offline) continueWatching.filter { it.isDownloaded } else continueWatching
            if (resumable.isNotEmpty()) {
                item(key = "continue-header", contentType = "header") {
                    SectionHeader(stringResource(R.string.home_continue_watching))
                }
                item(key = "continue-row", contentType = "carousel") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(resumable, key = { it.id }) { video ->
                            VideoCard(video, onClick = { onOpenVideo(video.id) })
                        }
                    }
                }
            }

            if (offline) {
                // Only what can play: everything on the device, two to a row.
                if (onDevice.isNotEmpty()) {
                    item(key = "device-header", contentType = "header") {
                        SectionHeader(stringResource(R.string.home_on_device))
                    }
                    items(onDevice.chunked(2), key = { row -> "device-${row.first().id}" }, contentType = { "grid-row" }) { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        ) {
                            row.forEach { video ->
                                VideoGridItem(video, onClick = { onOpenVideo(video.id) }, modifier = Modifier.weight(1f))
                            }
                            if (row.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                return@LazyColumn
            }

            if (onDevice.isNotEmpty()) {
                item(key = "device-header", contentType = "header") {
                    SectionHeader(stringResource(R.string.home_on_device))
                }
                item(key = "device-row", contentType = "carousel") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(onDevice, key = { it.id }) { video ->
                            VideoCard(video, onClick = { onOpenVideo(video.id) })
                        }
                    }
                }
            }

            item(key = "recent-header", contentType = "header") {
                SectionHeader(stringResource(R.string.home_recently_added))
            }
            pagingHeader(recent) { EmptyPanel(stringResource(R.string.home_empty)) }
            items(
                count = recent.itemCount,
                key = recent.itemKey { "recent-${it.id}" },
                contentType = recent.itemContentType { "video" },
            ) { index ->
                recent[index]?.let { video -> VideoListItem(video, onClick = { onOpenVideo(video.id) }) }
            }
            pagingFooter(recent)
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
    }
}

/** The offline card from the mockup: what can still play, and what is waiting to sync. */
@Composable
private fun OfflineBanner(downloaded: Int, pendingSync: Int) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                painterResource(ReliquaryIcons.Offline),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.home_offline_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp),
                    modifier = Modifier.semantics { heading() },
                )
                val videos = if (downloaded > 0) {
                    pluralStringResource(R.plurals.home_offline_videos, downloaded, downloaded)
                } else {
                    stringResource(R.string.home_offline_no_videos)
                }
                val pending = if (pendingSync > 0) {
                    " " + pluralStringResource(R.plurals.home_offline_pending, pendingSync, pendingSync)
                } else {
                    ""
                }
                Text(
                    videos + pending,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** The search pill and the account button, as in the mockups. */
@Composable
private fun HomeTopBar(onOpenSearch: () -> Unit, onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .height(56.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(painterResource(ReliquaryIcons.Search), contentDescription = null)
                Text(stringResource(R.string.home_search), style = MaterialTheme.typography.bodyLarge)
            }
        }
        FilledTonalIconButton(
            onClick = onOpenSettings,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(painterResource(ReliquaryIcons.Person), contentDescription = stringResource(R.string.home_account))
        }
    }
}
