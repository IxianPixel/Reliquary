package com.malgra.reliquary.core.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.malgra.reliquary.core.network.LocalNetwork
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
fun rememberLocalNetworkAccess(serverUrl: String?, onGranted: () -> Unit = {}): LocalNetworkAccess {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < ANDROID_17) return remember { LocalNetworkAccess(false, false) {} }

    val host = serverUrl?.toHttpUrlOrNull()?.host
    val local by produceState(false, host) { value = host != null && LocalNetwork.needsLocalNetwork(host) }
    var granted by remember { mutableStateOf(context.hasLocalNetworkPermission()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    // Coming back from Settings, or after the prompt.
    LifecycleResumeEffect(Unit) {
        val now = context.hasLocalNetworkPermission()
        if (now && !granted) onGranted()
        granted = now
        onPauseOrDispose { }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        asked = true
        if (allowed && !granted) onGranted()
        granted = allowed
    }
    val activity = context.findActivity()
    val settingsOnly = asked && !granted && activity != null &&
        !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_LOCAL_NETWORK)
    return LocalNetworkAccess(needed = local && !granted, settingsOnly = settingsOnly) {
        if (settingsOnly) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } else {
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
}

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
            Text(stringResource(R.string.ui_local_network_title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.ui_local_network_body), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = access::request) {
                Text(
                    stringResource(
                        if (access.settingsOnly) R.string.ui_local_network_settings else R.string.ui_local_network_allow,
                    ),
                )
            }
        }
    }
}

private const val ANDROID_17 = 37

private fun Context.hasLocalNetworkPermission(): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
