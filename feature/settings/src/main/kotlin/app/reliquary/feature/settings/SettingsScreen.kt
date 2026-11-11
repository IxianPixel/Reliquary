package app.reliquary.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.reliquary.core.designsystem.component.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Settings",
        body = "Server, account, downloads and appearance.",
        modifier = modifier,
    )
}
