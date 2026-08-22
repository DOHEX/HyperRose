package com.dohex.hyperrose.ui.theme

import androidx.compose.runtime.compositionLocalOf
import com.dohex.hyperrose.model.ThemeSettings

@Suppress("CompositionLocalAllowlist")
val LocalThemeSettings = compositionLocalOf<ThemeSettings> {
    error("No ThemeSettings provided")
}