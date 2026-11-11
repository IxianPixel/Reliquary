package com.malgra.reliquary.feature.channels

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.ui.ChannelListItem
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()

    PullToRefreshBox(
        isRefreshing = channels.isRefreshing,
        onRefresh = channels::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = WindowInsets.statusBars.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
            item(key = "title", contentType = "title") {
                Text(
                    stringResource(R.string.channels_title),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                        .semantics { heading() },
                )
            }
            pagingHeader(channels) { EmptyPanel(stringResource(R.string.channels_empty)) }
            items(
                count = channels.itemCount,
                key = channels.itemKey { it.id },
                contentType = channels.itemContentType { "channel" },
            ) { index ->
                channels[index]?.let { channel -> ChannelListItem(channel, onClick = { onOpenChannel(channel.id) }) }
            }
            pagingFooter(channels)
        }
    }
}
