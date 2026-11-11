package com.malgra.reliquary.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.SectionHeader
import com.malgra.reliquary.core.ui.VideoCard
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

            if (continueWatching.isNotEmpty()) {
                item(key = "continue-header", contentType = "header") {
                    SectionHeader(stringResource(R.string.home_continue_watching))
                }
                item(key = "continue-row", contentType = "carousel") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(continueWatching, key = { it.id }) { video ->
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
