package com.malgra.reliquary

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.malgra.reliquary.core.data.settings.AppSettings
import com.malgra.reliquary.core.designsystem.theme.ReliquaryTheme
import com.malgra.reliquary.core.model.ThemeMode
import com.malgra.reliquary.core.ui.isDark
import com.malgra.reliquary.core.ui.viewmodel.LocalViewModelGraph
import com.malgra.reliquary.core.ui.viewmodel.ViewModelGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var viewModelGraph: ViewModelGraph
    @Inject lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by settings.themeMode.collectAsState(ThemeMode.System)
            val dynamicColor by settings.dynamicColor.collectAsState(false)
            val dark = themeMode.isDark()
            // The bar icons follow the app's theme, not the system's, when Settings fixes it to light or dark.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
                onDispose {}
            }
            CompositionLocalProvider(LocalViewModelGraph provides viewModelGraph) {
                ReliquaryTheme(darkTheme = dark, dynamicColor = dynamicColor) {
                    ReliquaryApp()
                }
            }
        }
    }

    private companion object {
        // enableEdgeToEdge's own scrims for three-button navigation, which it doesn't expose.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
