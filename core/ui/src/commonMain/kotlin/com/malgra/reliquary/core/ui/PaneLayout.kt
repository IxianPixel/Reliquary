package com.malgra.reliquary.core.ui

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * How the window is split into panes. A phone in portrait gets one, as it always
 * has; tablets, foldables and desktop windows get two, side by side. Medium-width
 * windows count too, so a tablet in portrait and a folded foldable both get two.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberPaneDirective(): PaneScaffoldDirective {
    val info = currentWindowAdaptiveInfoV2()
    return remember(info) { calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth(info) }
}

/** Whether the window has room for two panes side by side. */
@Composable
fun rememberTwoPanes(): Boolean = rememberPaneDirective().maxHorizontalPartitions > 1
