package app.reliquary.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.reliquary.core.designsystem.component.PlaceholderScreen

@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Player",
        body = "Video playback with background audio and picture-in-picture.",
        modifier = modifier,
    )
}
