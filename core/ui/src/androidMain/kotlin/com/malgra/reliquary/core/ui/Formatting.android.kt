package com.malgra.reliquary.core.ui

import android.icu.text.CompactDecimalFormat
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun compactCount(count: Long): String {
    val locale = LocalConfiguration.current.locales[0]
    return CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
}

@Composable
actual fun fileSizeText(bytes: Long): String = Formatter.formatShortFileSize(LocalContext.current, bytes)
