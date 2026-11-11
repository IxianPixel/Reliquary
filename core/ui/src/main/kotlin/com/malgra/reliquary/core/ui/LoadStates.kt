package com.malgra.reliquary.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.malgra.reliquary.core.data.library.toLoadError
import com.malgra.reliquary.core.model.LoadError

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingPanel(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.ui_loading)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        LoadingIndicator(modifier = Modifier.size(48.dp))
    }
}

@Composable
fun ErrorPanel(error: LoadError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val (title, body) = when (error) {
        LoadError.Offline -> R.string.ui_error_offline to R.string.ui_error_offline_body
        LoadError.ServerError -> R.string.ui_error_server to R.string.ui_error_server_body
        LoadError.Unexpected -> R.string.ui_error_unexpected to R.string.ui_error_unexpected_body
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onRetry, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.ui_retry))
        }
    }
}

@Composable
fun EmptyPanel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
    )
}

/** A slim notice with a retry action, e.g. when a refresh failed but cached rows are showing. */
@Composable
fun RetryNotice(text: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text(stringResource(R.string.ui_retry)) }
        }
    }
}

/** The mediator's refresh state when there is one; it's the one that talks to the server. */
private val LazyPagingItems<*>.refreshState: LoadState
    get() = loadState.mediator?.refresh?.takeIf { it !is LoadState.NotLoading } ?: loadState.refresh

private val LazyPagingItems<*>.appendState: LoadState
    get() = loadState.mediator?.append?.takeIf { it !is LoadState.NotLoading } ?: loadState.append

/** True while a pull-to-refresh (or any refresh) is running over rows already shown. */
val LazyPagingItems<*>.isRefreshing: Boolean
    get() = refreshState is LoadState.Loading && itemCount > 0

/**
 * The states shown above a paged list: a loader or error in place of an empty
 * list, a notice when refresh failed over cached rows, or [empty] once the
 * server says there is nothing.
 */
fun LazyListScope.pagingHeader(items: LazyPagingItems<*>, empty: @Composable () -> Unit) {
    val refresh = items.refreshState
    when {
        items.itemCount > 0 && refresh is LoadState.Error -> item(key = "paging-notice", contentType = "notice") {
            RetryNotice(stringResource(R.string.ui_refresh_failed), onRetry = items::retry)
        }
        items.itemCount > 0 -> Unit
        refresh is LoadState.Loading -> item(key = "paging-loading", contentType = "status") { LoadingPanel() }
        refresh is LoadState.Error -> item(key = "paging-error", contentType = "status") {
            ErrorPanel(refresh.error.toLoadError(), onRetry = items::retry)
        }
        items.loadState.append.endOfPaginationReached -> item(key = "paging-empty", contentType = "status") { empty() }
    }
}

/** A loader or a retry row below a paged list while the next page loads or after it failed. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun LazyListScope.pagingFooter(items: LazyPagingItems<*>) {
    if (items.itemCount == 0) return
    when (items.appendState) {
        is LoadState.Loading -> item(key = "paging-append", contentType = "status") {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator(Modifier.size(40.dp))
            }
        }
        is LoadState.Error -> item(key = "paging-append-error", contentType = "notice") {
            RetryNotice(stringResource(R.string.ui_load_more_failed), onRetry = items::retry)
        }
        is LoadState.NotLoading -> Unit
    }
}
