package com.malgra.reliquary.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.QueueItem
import com.malgra.reliquary.core.model.ServerTask
import com.malgra.reliquary.core.ui.AddByUrlSheet
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.ErrorPanel
import com.malgra.reliquary.core.ui.LoadingPanel
import com.malgra.reliquary.core.ui.RetryNotice
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.VideoThumbnail
import com.malgra.reliquary.core.ui.formatDate
import com.malgra.reliquary.core.ui.metaLine
import org.jetbrains.compose.resources.painterResource

/** The server's download queue (#23): what TA will fetch next, for admins. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = hiltViewModel(),
) {
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    if (!isAdmin) {
        Column(modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Title()
            EmptyPanel(stringResource(R.string.queue_admin_only))
        }
        return
    }

    val list by viewModel.list.collectAsStateWithLifecycle()
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                QueueMessage.Added -> R.string.queue_added
                QueueMessage.RescanStarted -> R.string.queue_rescan_started
                QueueMessage.DownloadStarted -> R.string.queue_download_started
                QueueMessage.Failed -> R.string.queue_failed
            }
            snackbar.showSnackbar(resources.getString(text))
        }
    }
    // Next page when the end of the list comes into view.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to listState.layoutInfo.totalItemsCount }
            .collect { (last, total) -> if (last != null && last >= total - 5) viewModel.loadMore() }
    }

    Box(modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = list.refreshing && list.items != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 88.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                item(key = "title") {
                    Title {
                        ClearMenu(tab = list.tab, onClear = { confirmClear = true })
                    }
                }
                item(key = "actions") {
                    Actions(
                        canDownload = list.tab == QueueTab.Pending && !list.items.isNullOrEmpty(),
                        onRescan = viewModel::rescan,
                        onDownloadAll = viewModel::downloadAll,
                    )
                }
                items(tasks, key = { "task-${it.title}" }) { TaskCard(it) }
                item(key = "tabs") { Tabs(list.tab, list.total, viewModel::selectTab) }

                val items = list.items
                val error = list.error
                when {
                    items == null && error != null -> item(key = "error") { ErrorPanel(error, onRetry = viewModel::refresh) }
                    items == null -> item(key = "loading") { LoadingPanel() }
                    else -> {
                        if (error != null) {
                            item(key = "notice") {
                                RetryNotice(
                                    stringResource(R.string.queue_failed),
                                    onRetry = viewModel::refresh,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                        if (items.isEmpty()) {
                            item(key = "empty") {
                                EmptyPanel(
                                    stringResource(
                                        if (list.tab == QueueTab.Pending) R.string.queue_empty_pending else R.string.queue_empty_ignored,
                                    ),
                                )
                            }
                        }
                        items(items, key = { it.videoId }) { item ->
                            QueueRow(
                                item = item,
                                onDownloadNext = { viewModel.downloadNext(item) },
                                onIgnore = { viewModel.ignore(item) },
                                onRestore = { viewModel.restore(item) },
                                onRemove = { viewModel.remove(item) },
                            )
                        }
                        if (list.appending) {
                            item(key = "appending") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        ExtendedFloatingActionButton(
            onClick = { showAdd = true },
            icon = { Icon(painterResource(ReliquaryIcons.Plus), contentDescription = null) },
            text = { Text(stringResource(R.string.queue_add)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp),
        )
    }

    if (showAdd) {
        LaunchedEffect(adding) {
            if (adding == AddState.Done) {
                showAdd = false
                viewModel.resetAdd()
            }
        }
        AddByUrlSheet(
            title = stringResource(R.string.queue_add_title),
            label = stringResource(R.string.queue_add_label),
            supportingText = stringResource(R.string.queue_add_supporting),
            confirmLabel = stringResource(R.string.queue_add),
            busy = adding == AddState.Busy,
            error = if (adding == AddState.Failed) stringResource(R.string.queue_add_failed) else null,
            onSubmit = viewModel::add,
            onDismiss = {
                showAdd = false
                viewModel.resetAdd()
            },
        )
    }

    if (confirmClear) {
        val pending = list.tab == QueueTab.Pending
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(if (pending) R.string.queue_clear_pending_title else R.string.queue_clear_ignored_title)) },
            text = { Text(stringResource(if (pending) R.string.queue_clear_pending_body else R.string.queue_clear_ignored_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.queue_clear)) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.queue_cancel)) } },
        )
    }
}

@Composable
private fun Title(action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
    ) {
        Text(
            stringResource(R.string.queue_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        action()
    }
}

@Composable
private fun ClearMenu(tab: QueueTab, onClear: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(painterResource(ReliquaryIcons.More), contentDescription = stringResource(R.string.queue_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (tab == QueueTab.Pending) R.string.queue_clear_pending else R.string.queue_clear_ignored)) },
                onClick = {
                    open = false
                    onClear()
                },
            )
        }
    }
}

@Composable
private fun Actions(canDownload: Boolean, onRescan: () -> Unit, onDownloadAll: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        FilledTonalButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
            Icon(painterResource(ReliquaryIcons.Sync), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.queue_rescan), modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button(onClick = onDownloadAll, enabled = canDownload, modifier = Modifier.weight(1f)) {
            Icon(painterResource(ReliquaryIcons.Downloads), contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.queue_download_all), modifier = Modifier.padding(start = 8.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A rescan or download running on the server, with its latest message. */
@Composable
private fun TaskCard(task: ServerTask) {
    Surface(
        color = if (task.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(task.title.ifBlank { stringResource(R.string.queue_task_working) }, style = MaterialTheme.typography.titleSmall)
            task.messages.lastOrNull()?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!task.failed) {
                val progress = task.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun Tabs(selected: QueueTab, total: Int?, onSelect: (QueueTab) -> Unit) {
    SingleChoiceSegmentedButtonRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        QueueTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index, QueueTab.entries.size),
            ) {
                Text(
                    when {
                        tab == QueueTab.Pending && tab == selected && total != null ->
                            pluralStringResource(R.plurals.queue_pending_count, total, total)
                        tab == QueueTab.Pending -> stringResource(R.string.queue_pending)
                        else -> stringResource(R.string.queue_ignored)
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    onDownloadNext: () -> Unit,
    onIgnore: () -> Unit,
    onRestore: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 6.dp, bottom = 10.dp),
    ) {
        VideoThumbnail(
            url = item.thumbnailUrl,
            duration = item.duration,
            modifier = Modifier.size(width = 152.dp, height = 86.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                metaLine(item.channelName, item.published?.let(::formatDate)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    painterResource(ReliquaryIcons.More),
                    contentDescription = stringResource(R.string.queue_item_options, item.title),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                val choose: (() -> Unit) -> () -> Unit = { action -> { menuOpen = false; action() } }
                if (item.ignored) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.queue_restore)) }, onClick = choose(onRestore))
                } else {
                    DropdownMenuItem(text = { Text(stringResource(R.string.queue_download_next)) }, onClick = choose(onDownloadNext))
                    DropdownMenuItem(text = { Text(stringResource(R.string.queue_ignore)) }, onClick = choose(onIgnore))
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.queue_remove)) }, onClick = choose(onRemove))
            }
        }
    }
}
