package com.malgra.reliquary.feature.playlists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.KeepOfflineRule
import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.ui.AddByUrlSheet
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.KeepOfflineButton
import com.malgra.reliquary.core.ui.KeepOfflineSheet
import com.malgra.reliquary.core.ui.NumberedVideoItem
import com.malgra.reliquary.core.ui.PlaylistListItem
import com.malgra.reliquary.core.ui.StatusBarScrim
import com.malgra.reliquary.core.ui.isRefreshing
import com.malgra.reliquary.core.ui.metaLine
import com.malgra.reliquary.core.ui.pagingFooter
import com.malgra.reliquary.core.ui.pagingHeader
import com.malgra.reliquary.core.ui.videoCountText
import com.malgra.reliquary.core.ui.viewmodel.injectedViewModel
import com.malgra.reliquary.feature.playlists.resources.Res
import com.malgra.reliquary.feature.playlists.resources.playlist_archived_of
import com.malgra.reliquary.feature.playlists.resources.playlist_back
import com.malgra.reliquary.feature.playlists.resources.playlist_empty
import com.malgra.reliquary.feature.playlists.resources.playlist_play_all
import com.malgra.reliquary.feature.playlists.resources.playlist_subscribe
import com.malgra.reliquary.feature.playlists.resources.playlist_subscription_failed
import com.malgra.reliquary.feature.playlists.resources.playlist_subscription_offline
import com.malgra.reliquary.feature.playlists.resources.playlist_unsubscribe
import com.malgra.reliquary.feature.playlists.resources.playlists_add
import com.malgra.reliquary.feature.playlists.resources.playlists_add_confirm
import com.malgra.reliquary.feature.playlists.resources.playlists_add_error_not_playlist
import com.malgra.reliquary.feature.playlists.resources.playlists_add_error_offline
import com.malgra.reliquary.feature.playlists.resources.playlists_add_error_server
import com.malgra.reliquary.feature.playlists.resources.playlists_add_label
import com.malgra.reliquary.feature.playlists.resources.playlists_add_supporting
import com.malgra.reliquary.feature.playlists.resources.playlists_add_title
import com.malgra.reliquary.feature.playlists.resources.playlists_added
import com.malgra.reliquary.feature.playlists.resources.playlists_empty
import com.malgra.reliquary.feature.playlists.resources.playlists_still_adding
import com.malgra.reliquary.feature.playlists.resources.playlists_subscribing
import com.malgra.reliquary.feature.playlists.resources.playlists_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = injectedViewModel(),
) {
    val playlists = viewModel.playlists.collectAsLazyPagingItems()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val addSheet by viewModel.addSheet.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val subscribingMessage = stringResource(Res.string.playlists_subscribing)
    val addedMessage = stringResource(Res.string.playlists_added)
    val stillAddingMessage = stringResource(Res.string.playlists_still_adding)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (event == PlaylistsViewModel.AddEvent.Added) playlists.refresh()
            snackbars.showSnackbar(
                when (event) {
                    PlaylistsViewModel.AddEvent.Subscribing -> subscribingMessage
                    PlaylistsViewModel.AddEvent.Added -> addedMessage
                    PlaylistsViewModel.AddEvent.StillAdding -> stillAddingMessage
                },
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = playlists.isRefreshing,
        onRefresh = playlists::refresh,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyColumn(contentPadding = WindowInsets.statusBars.asPaddingValues(), modifier = Modifier.fillMaxSize()) {
            item(key = "title", contentType = "title") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 24.dp, bottom = 8.dp),
                ) {
                    Text(
                        stringResource(Res.string.playlists_title),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { heading() },
                    )
                    if (isAdmin) {
                        IconButton(onClick = viewModel::openAddSheet) {
                            Icon(painterResource(ReliquaryIcons.Plus), contentDescription = stringResource(Res.string.playlists_add))
                        }
                    }
                }
            }
            pagingHeader(playlists) { EmptyPanel(stringResource(Res.string.playlists_empty)) }
            items(
                count = playlists.itemCount,
                key = playlists.itemKey { it.id },
                contentType = playlists.itemContentType { "playlist" },
            ) { index ->
                playlists[index]?.let { playlist -> PlaylistListItem(playlist, onClick = { onOpenPlaylist(playlist.id) }) }
            }
            pagingFooter(playlists)
        }
        StatusBarScrim(Modifier.align(Alignment.TopCenter))
        SnackbarHost(
            snackbars,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding(),
        )
    }

    addSheet?.takeIf { isAdmin }?.let { sheet ->
        AddByUrlSheet(
            title = stringResource(Res.string.playlists_add_title),
            label = stringResource(Res.string.playlists_add_label),
            supportingText = stringResource(Res.string.playlists_add_supporting),
            confirmLabel = stringResource(Res.string.playlists_add_confirm),
            busy = sheet.busy,
            error = sheet.error?.let {
                stringResource(
                    when (it) {
                        PlaylistsViewModel.AddError.NotAPlaylist -> Res.string.playlists_add_error_not_playlist
                        PlaylistsViewModel.AddError.Offline -> Res.string.playlists_add_error_offline
                        PlaylistsViewModel.AddError.ServerError -> Res.string.playlists_add_error_server
                    },
                )
            },
            onSubmit = viewModel::subscribe,
            onDismiss = viewModel::closeAddSheet,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = injectedViewModel<PlaylistViewModel, PlaylistViewModel.Factory>(key = playlistId) {
        it.create(playlistId)
    },
) {
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val videos = viewModel.videos.collectAsLazyPagingItems()
    val rule by viewModel.keepOfflineRule.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val changingSubscription by viewModel.changingSubscription.collectAsStateWithLifecycle()
    var editingRule by rememberSaveable { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }
    val offlineMessage = stringResource(Res.string.playlist_subscription_offline)
    val failedMessage = stringResource(Res.string.playlist_subscription_failed)

    LaunchedEffect(viewModel) {
        viewModel.subscriptionErrors.collect { error ->
            snackbars.showSnackbar(if (error == LoadError.Offline) offlineMessage else failedMessage)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 4.dp, top = 8.dp),
        ) {
            Icon(painterResource(ReliquaryIcons.Back), contentDescription = stringResource(Res.string.playlist_back))
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
                                            pluralStringResource(Res.plurals.playlist_archived_of, p.entryCount, p.archivedCount, p.entryCount)
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val first = videos.itemSnapshotList.firstOrNull()
                            Button(onClick = { first?.let { onOpenVideo(it.id) } }, enabled = first != null) {
                                Icon(
                                    painterResource(ReliquaryIcons.Play),
                                    contentDescription = null,
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                )
                                Text(
                                    stringResource(Res.string.playlist_play_all),
                                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                                )
                            }
                            if (playlist != null) KeepOfflineButton(rule != null, onClick = { editingRule = true })
                        }
                        // TA's own (custom) playlists have nothing on YouTube to subscribe to.
                        playlist?.takeIf { isAdmin && !it.id.startsWith(CUSTOM_PLAYLIST_PREFIX) }?.let { p ->
                            SubscribeButton(
                                subscribed = p.subscribed,
                                enabled = !changingSubscription,
                                onClick = { viewModel.setSubscribed(!p.subscribed) },
                            )
                        }
                    }
                }
                pagingHeader(videos) { EmptyPanel(stringResource(Res.string.playlist_empty)) }
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
            SnackbarHost(
                snackbars,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }
    }

    val current = playlist
    if (editingRule && current != null) {
        val bytes by produceState<Long?>(null) { value = viewModel.typicalVideoBytes() }
        KeepOfflineSheet(
            initial = rule ?: KeepOfflineRule.defaultFor(KeepOfflineRule.Kind.Playlist, current.id, current.name, current.thumbnailUrl),
            isExisting = rule != null,
            typicalVideoBytes = bytes,
            playlistSize = current.archivedCount,
            onSave = {
                viewModel.saveRule(it)
                editingRule = false
            },
            onDelete = {
                viewModel.deleteRule()
                editingRule = false
            },
            onDismiss = { editingRule = false },
        )
    }
}

private const val CUSTOM_PLAYLIST_PREFIX = "TA_playlist_"

@Composable
private fun SubscribeButton(subscribed: Boolean, enabled: Boolean, onClick: () -> Unit) {
    if (subscribed) {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Text(stringResource(Res.string.playlist_unsubscribe))
        }
    } else {
        FilledTonalButton(onClick = onClick, enabled = enabled) {
            Icon(
                painterResource(ReliquaryIcons.Plus),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Text(
                stringResource(Res.string.playlist_subscribe),
                modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            )
        }
    }
}
