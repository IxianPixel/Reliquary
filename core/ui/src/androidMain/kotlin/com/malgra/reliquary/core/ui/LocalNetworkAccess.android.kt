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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.malgra.reliquary.core.network.LocalNetwork
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
actual fun rememberLocalNetworkAccess(serverUrl: String?, onGranted: () -> Unit): LocalNetworkAccess {
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

private const val ANDROID_17 = 37

private fun Context.hasLocalNetworkPermission(): Boolean =
    Build.VERSION.SDK_INT < ANDROID_17 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
