package com.malgra.reliquary.feature.search

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons
import com.malgra.reliquary.core.model.SearchResults
import com.malgra.reliquary.core.ui.ChannelListItem
import com.malgra.reliquary.core.ui.EmptyPanel
import com.malgra.reliquary.core.ui.ErrorPanel
import com.malgra.reliquary.core.ui.LoadingPanel
import com.malgra.reliquary.core.ui.PlaylistListItem
import com.malgra.reliquary.core.ui.SectionHeader
import com.malgra.reliquary.core.ui.VideoListItem

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    LazyColumn(modifier = modifier.fillMaxSize().imePadding()) {
        item(key = "field", contentType = "field") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(ReliquaryIcons.Back), contentDescription = stringResource(R.string.search_back))
                }
                TextField(
                    value = query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    painterResource(ReliquaryIcons.Close),
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focus),
                )
            }
        }

        when (val current = state) {
            SearchState.Idle -> item(key = "tips") { EmptyPanel(stringResource(R.string.search_tips)) }
            SearchState.Searching -> item(key = "loading") { LoadingPanel() }
            is SearchState.Failed -> item(key = "error") { ErrorPanel(current.error, onRetry = viewModel::retry) }
            is SearchState.Results -> results(current.query, current.results, onOpenVideo, onOpenChannel, onOpenPlaylist)
        }
    }
}

private fun LazyListScope.results(
    query: String,
    results: SearchResults,
    onOpenVideo: (String) -> Unit,
    onOpenChannel: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
) {
    if (results.fromCache) {
        item(key = "offline") {
            Text(
                stringResource(R.string.search_offline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (results.isEmpty) {
        item(key = "none") { EmptyPanel(stringResource(R.string.search_no_results, query)) }
        return
    }
    if (results.channels.isNotEmpty()) {
        item(key = "channels-header") { SectionHeader(stringResource(R.string.search_channels)) }
        items(results.channels, key = { "channel-${it.id}" }) { channel ->
            ChannelListItem(channel, onClick = { onOpenChannel(channel.id) })
        }
    }
    if (results.playlists.isNotEmpty()) {
        item(key = "playlists-header") { SectionHeader(stringResource(R.string.search_playlists)) }
        items(results.playlists, key = { "playlist-${it.id}" }) { playlist ->
            PlaylistListItem(playlist, onClick = { onOpenPlaylist(playlist.id) })
        }
    }
    if (results.videos.isNotEmpty()) {
        item(key = "videos-header") { SectionHeader(stringResource(R.string.search_videos)) }
        items(results.videos, key = { "video-${it.id}" }) { video ->
            VideoListItem(video, onClick = { onOpenVideo(video.id) })
        }
    }
}
