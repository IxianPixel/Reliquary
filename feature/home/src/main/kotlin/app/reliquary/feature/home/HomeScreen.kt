package app.reliquary.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.reliquary.core.designsystem.component.PlaceholderScreen

@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Home",
        body = "Continue watching, recently added and what is on this device.",
        modifier = modifier,
    )
}
