package com.dohex.hyperrose.ui.theme

import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

@Stable
data class ThemeMode(
    val colorMode: Int = 0,
    val enableBlur: Boolean = true,
    val smoothRounding: Boolean = true,
)

val ColorModeOptions = listOf(
    "跟随系统",
    "浅色",
    "深色",
    "Monet 跟随系统",
    "Monet 浅色",
    "Monet 深色"
)

val LocalThemeMode = compositionLocalOf<ThemeMode> {
    error("No ThemeMode provided")
}

val LocalUpdateThemeMode = staticCompositionLocalOf<((ThemeMode) -> ThemeMode) -> Unit> {
    error("No ThemeMode updater provided")
}

val LocalCanUpdateThemeMode = compositionLocalOf { true }
