package com.malgra.reliquary.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.malgra.reliquary.core.ui.resources.Res
import com.malgra.reliquary.core.ui.resources.ui_local_network_allow
import com.malgra.reliquary.core.ui.resources.ui_local_network_body
import com.malgra.reliquary.core.ui.resources.ui_local_network_settings
import com.malgra.reliquary.core.ui.resources.ui_local_network_title
import org.jetbrains.compose.resources.stringResource

/** Whether the app may reach the local network, and a way to ask. */
@Stable
class LocalNetworkAccess internal constructor(
    /** True when the server is on the local network and the permission is missing. */
    val needed: Boolean,
    /** True after the user said no and Android won't show its prompt again: only Settings can grant it. */
    val settingsOnly: Boolean,
    private val onRequest: () -> Unit,
) {
    fun request() = onRequest()
}

/**
 * Android 17 blocks apps that target it from the local network unless the user
 * allows "Nearby devices". Without it, a server on the home network just times
 * out, so screens ask for it when [serverUrl] is a local address. [onGranted]
 * runs once it's allowed, e.g. to retry a check that timed out.
 */
@Composable
expect fun rememberLocalNetworkAccess(serverUrl: String?, onGranted: () -> Unit = {}): LocalNetworkAccess

/** Explains why the permission is needed, with a button to allow it. Shows nothing when it isn't needed. */
@Composable
fun LocalNetworkAccessCard(access: LocalNetworkAccess, modifier: Modifier = Modifier) {
    if (!access.needed) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(Res.string.ui_local_network_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(Res.string.ui_local_network_body), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = access::request) {
                Text(
                    stringResource(
                        if (access.settingsOnly) Res.string.ui_local_network_settings else Res.string.ui_local_network_allow,
                    ),
                )
            }
        }
    }
}
