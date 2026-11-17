package com.malgra.reliquary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.malgra.reliquary.core.designsystem.theme.ReliquaryTheme
import com.malgra.reliquary.core.ui.viewmodel.LocalViewModelGraph
import com.malgra.reliquary.core.ui.viewmodel.ViewModelGraph
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var viewModelGraph: ViewModelGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalViewModelGraph provides viewModelGraph) {
                ReliquaryTheme {
                    ReliquaryApp()
                }
            }
        }
    }
}
