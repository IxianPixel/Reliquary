package com.malgra.reliquary.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.malgra.reliquary.core.data.AppDirectories
import com.malgra.reliquary.core.designsystem.theme.ReliquaryTheme
import com.malgra.reliquary.core.ui.viewmodel.LocalViewModelGraph
import okio.Path.Companion.toOkioPath
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Frame
import java.io.File
import javax.swing.SwingUtilities

fun main() {
    val directories = AppDirectories()
    val instance = SingleInstance.acquire(directories.root.toPath()) ?: return
    // Set by `./gradlew :desktop:run` only, never in packaged builds.
    val app = AppGraph(directories, logHttp = System.getProperty("reliquary.debug") == "true")
    app.start()

    application {
        val state = rememberWindowState(size = DpSize(1280.dp, 800.dp))
        val viewModels = remember { DesktopViewModelGraph(app) }
        val viewModelStore = remember { WindowViewModelStore() }
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                // Thumbnails need the same credentials as the API, and go nowhere else.
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { app.okHttpClient })) }
                .diskCache { DiskCache.Builder().directory(File(directories.root, "image_cache").toOkioPath()).build() }
                .crossfade(true)
                .build()
        }

        Window(
            onCloseRequest = {
                viewModelStore.viewModelStore.clear()
                app.close()
                instance.close()
                exitApplication()
            },
            state = state,
            title = "Reliquary",
            icon = painterResource("icon.png"),
        ) {
            DisposableEffect(window) {
                window.minimumSize = Dimension(720, 480)
                instance.onShowRequest = { SwingUtilities.invokeLater { bringToFront(window, state) } }
                onDispose { instance.onShowRequest = {} }
            }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides viewModelStore,
                LocalViewModelGraph provides viewModels,
            ) {
                ReliquaryTheme {
                    DesktopApp()
                }
            }
        }
    }
}

/** Holds the window-wide ViewModels (the shell's), as the Activity does on Android; screens get their own per entry. */
private class WindowViewModelStore : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** Shows the window over other apps' windows, as when its Dock or taskbar icon is clicked. */
private fun bringToFront(window: Frame, state: WindowState) {
    state.isMinimized = false
    window.isVisible = true
    // macOS only raises a window within the active app, so activate the app first.
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
        Desktop.getDesktop().requestForeground(true)
    }
    window.toFront()
    window.requestFocus()
}
