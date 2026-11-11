package app.reliquary.feature.downloads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.reliquary.core.designsystem.component.PlaceholderScreen

@Composable
fun DownloadsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Downloads",
        body = "Download queue, keep-offline rules, storage and sync status.",
        modifier = modifier,
    )
}
