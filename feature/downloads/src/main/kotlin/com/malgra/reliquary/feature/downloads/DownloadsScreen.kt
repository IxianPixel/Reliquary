package com.malgra.reliquary.feature.downloads

import android.text.format.Formatter
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.data.download.Storage
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.DownloadStatus
import com.malgra.reliquary.core.model.DownloadedVideo
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.VideoThumbnail
import kotlin.time.Duration

@Composable
fun DownloadsScreen(
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val wifiOnly by viewModel.wifiOnly.collectAsStateWithLifecycle()
    val storage by viewModel.storage.collectAsStateWithLifecycle()
    var removing by remember { mutableStateOf<DownloadedVideo?>(null) }

    LazyColumn(contentPadding = WindowInsets.statusBars.asPaddingValues(), modifier = modifier.fillMaxSize()) {
        item(key = "title") {
            Text(
                stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
                    .semantics { heading() },
            )
        }
        storage?.takeIf { (items?.isNotEmpty() == true) }?.let { space ->
            item(key = "storage") { StorageMeter(space) }
        }
        if (pendingSync > 0) {
            item(key = "sync") {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Icon(
                        painterResource(ReliquaryIcons.Sync),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        pluralStringResource(R.plurals.downloads_pending_sync, pendingSync, pendingSync),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        val list = items
        when {
            list == null -> Unit
            list.isEmpty() -> item(key = "empty") { EmptyPanel(stringResource(R.string.downloads_empty)) }
            else -> items(list, key = { it.download.videoId }) { item ->
                DownloadRow(
                    item = item,
                    wifiOnly = wifiOnly,
                    onOpen = { onOpenVideo(item.download.videoId) },
                    onRemove = {
                        if (item.download.status == DownloadStatus.Completed) removing = item else viewModel.remove(item.download.videoId)
                    },
                )
            }
        }
    }

    removing?.let { item ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text(stringResource(R.string.downloads_remove_title)) },
            text = { Text(stringResource(R.string.downloads_remove_body, item.video?.title.orEmpty())) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(item.download.videoId)
                    removing = null
                }) { Text(stringResource(R.string.downloads_remove_confirm)) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.downloads_keep)) } },
        )
    }
}

@Composable
private fun StorageMeter(storage: Storage) {
    val context = LocalContext.current
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        LinearProgressIndicator(
            progress = { storage.usedFraction.coerceAtLeast(0.01f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            stringResource(
                R.string.downloads_storage,
                Formatter.formatShortFileSize(context, storage.usedBytes),
                Formatter.formatShortFileSize(context, storage.freeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DownloadRow(item: DownloadedVideo, wifiOnly: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    val download = item.download
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        VideoThumbnail(
            url = item.video?.thumbnailUrl,
            duration = item.video?.duration ?: Duration.ZERO,
            progress = item.video?.progress,
            shape = RoundedCornerShape(10.dp),
            compact = true,
            modifier = Modifier.size(width = 112.dp, height = 63.dp),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.video?.title ?: stringResource(R.string.downloads_missing_video),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            when (download.status) {
                DownloadStatus.Downloading -> Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val fraction = download.fraction
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.weight(1f))
                        Text(
                            stringResource(R.string.downloads_percent, (fraction * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                    }
                }
                else -> Text(
                    when (download.status) {
                        DownloadStatus.Completed -> stringResource(
                            R.string.downloads_on_device,
                            Formatter.formatShortFileSize(context, download.totalBytes),
                        )
                        DownloadStatus.Failed -> stringResource(R.string.downloads_failed)
                        else -> stringResource(if (wifiOnly) R.string.downloads_waiting_wifi else R.string.downloads_waiting)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                painterResource(ReliquaryIcons.Close),
                contentDescription = stringResource(
                    if (download.status == DownloadStatus.Completed) R.string.downloads_remove else R.string.downloads_cancel,
                ),
            )
        }
    }
}
