package com.dohex.hyperrose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import com.dohex.hyperrose.model.ColorMode
import com.dohex.hyperrose.model.ThemeSettings
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun HyperRoseTheme(
    settings: ThemeSettings,
    content: @Composable () -> Unit,
) {
    val colorSchemeMode =
        when (settings.colorMode) {
            ColorMode.SYSTEM ->
                if (settings.monetEnabled) {
                    ColorSchemeMode.MonetSystem
                } else {
                    ColorSchemeMode.System
                }

            ColorMode.LIGHT ->
                if (settings.monetEnabled) {
                    ColorSchemeMode.MonetLight
                } else {
                    ColorSchemeMode.Light
                }

            ColorMode.DARK ->
                if (settings.monetEnabled) {
                    ColorSchemeMode.MonetDark
                } else {
                    ColorSchemeMode.Dark
                }
        }

    val controller = remember(colorSchemeMode) {
        ThemeController(colorSchemeMode)
    }

    CompositionLocalProvider(
        LocalThemeSettings provides settings,
    ) {
        MiuixTheme(
            controller = controller,
            content = content,
        )
    }
}