package com.malgra.reliquary.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * A backdrop behind the status bar, so a list scrolling up under it doesn't
 * show through behind the clock. Put it over the list, at the top. It matches
 * the screen's background, so at rest it can't be seen. [visible] fades it,
 * e.g. in once a header image that sits under the status bar has scrolled away.
 */
@Composable
fun StatusBarScrim(modifier: Modifier = Modifier, visible: Boolean = true) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "statusBarScrim")
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.background),
    )
}
