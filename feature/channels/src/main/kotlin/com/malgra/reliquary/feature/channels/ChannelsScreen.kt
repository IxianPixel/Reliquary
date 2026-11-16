package com.malgra.reliquary.feature.channels

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.ui.AddByUrlSheet
import com.malgra.reliquary.core.ui.ChannelListItem
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelsScreen(
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val channels = viewModel.channels.collectAsLazyPagingItems()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val addSheet by viewModel.addSheet.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val resources = LocalResources.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == ChannelsViewModel.AddEvent.Added) channels.refresh()
            val message = when (event) {
                ChannelsViewModel.AddEvent.Subscribing -> R.string.channels_subscribing
                ChannelsViewModel.AddEvent.Added -> R.string.channels_added
                ChannelsViewModel.AddEvent.StillAdding -> R.string.channels_still_adding
            }
            snackbars.showSnackbar(resources.getString(message))
        }
    }

    PullToRefreshBox(
        isRefreshing = channels.isRefreshing,
        onRefresh = channels::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = WindowInsets.statusBars.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
            item(key = "title", contentType = "title") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        stringResource(R.string.channels_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    if (isAdmin) {
                        IconButton(onClick = viewModel::openAddSheet) {
                            Icon(painterResource(ReliquaryIcons.Plus), contentDescription = stringResource(R.string.channels_add))
                        }
                    }
                }
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
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        SnackbarHost(
            snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    addSheet?.let { sheet ->
        // Losing admin (another account, or the server stops answering) closes it.
        if (!isAdmin) {
            LaunchedEffect(Unit) { viewModel.closeAddSheet() }
            return@let
        }
        AddByUrlSheet(
            title = stringResource(R.string.channels_add_title),
            label = stringResource(R.string.channels_add_label),
            supportingText = stringResource(R.string.channels_add_supporting),
            confirmLabel = stringResource(R.string.channels_add_confirm),
            busy = sheet.busy,
            error = sheet.error?.let {
                stringResource(
                    when (it) {
                        ChannelsViewModel.AddError.NotAChannel -> R.string.channels_add_error_not_channel
                        ChannelsViewModel.AddError.Offline -> R.string.channels_add_error_offline
                        ChannelsViewModel.AddError.ServerError -> R.string.channels_add_error_server
                    },
                )
            },
            onSubmit = viewModel::subscribe,
            onDismiss = viewModel::closeAddSheet,
        )
    }
}
