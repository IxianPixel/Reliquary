package com.malgra.reliquary.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.malgra.reliquary.core.designsystem.component.PlaceholderScreen

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Downloads",
        body = "Download queue, keep-offline rules, storage and sync status.",
        modifier = modifier,
    )
}
