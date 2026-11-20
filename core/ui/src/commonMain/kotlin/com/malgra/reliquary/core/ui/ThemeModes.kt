package com.malgra.reliquary.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.malgra.reliquary.core.model.ThemeMode

/** Whether this mode draws dark: fixed for Light and Dark, the system's choice for System. */
@Composable
fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
