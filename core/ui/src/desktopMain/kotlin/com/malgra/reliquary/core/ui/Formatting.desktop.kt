package com.malgra.reliquary.core.ui

import androidx.compose.runtime.Composable
import java.text.NumberFormat
import java.util.Locale

@Composable
actual fun compactCount(count: Long): String = formatCompactCount(count, Locale.getDefault())

@Composable
actual fun fileSizeText(bytes: Long): String = formatFileSize(bytes, Locale.getDefault())

/** Like Android's ICU short form: two significant digits below ten ("1.2K"), none above ("12K"). */
internal fun formatCompactCount(count: Long, locale: Locale): String {
    val format = NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT)
    val digits = count.toString().trimStart('-').length
    format.maximumFractionDigits = if (digits > 3 && (digits - 1) % 3 == 0) 1 else 0
    return format.format(count)
}

private val units = listOf("B", "kB", "MB", "GB", "TB", "PB")

/** Android's `Formatter.formatShortFileSize`: SI units, one decimal below 10, none above. */
internal fun formatFileSize(bytes: Long, locale: Locale): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value > 900 && unit < units.lastIndex) {
        value /= 1000
        unit++
    }
    val pattern = when {
        unit == 0 || value >= 10 -> "%.0f"
        value < 1 -> "%.2f"
        else -> "%.1f"
    }
    return "${pattern.format(locale, value)} ${units[unit]}"
}
