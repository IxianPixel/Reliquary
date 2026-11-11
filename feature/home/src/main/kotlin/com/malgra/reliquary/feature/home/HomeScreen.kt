package com.malgra.reliquary.feature.home

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.malgra.reliquary.core.designsystem.component.PlaceholderScreen
import com.malgra.reliquary.core.designsystem.icon.ReliquaryIcons

@Composable
fun HomeScreen(onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = "Home",
        body = "Continue watching, recently added and what is on this device.",
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(ReliquaryIcons.Settings), contentDescription = "Settings")
            }
        },
    )
}
