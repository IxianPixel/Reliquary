package com.malgra.reliquary.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

// Desktop systems don't gate the local network per app.
@Composable
actual fun rememberLocalNetworkAccess(serverUrl: String?, onGranted: () -> Unit): LocalNetworkAccess =
    remember { LocalNetworkAccess(needed = false, settingsOnly = false) {} }
