package com.dohex.hyperrose.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

@Composable
fun HyperRoseTheme(
    colorMode: Int = 0,
    smoothRounding: Boolean = true,
    content: @Composable () -> Unit
) {
    val controller = remember(colorMode) {
        ThemeController(
            when (colorMode) {
                1 -> ColorSchemeMode.Light
                2 -> ColorSchemeMode.Dark
                3 -> ColorSchemeMode.MonetSystem
                4 -> ColorSchemeMode.MonetLight
                5 -> ColorSchemeMode.MonetDark
                else -> ColorSchemeMode.System
            }
        )
    }
    MiuixTheme(
        controller = controller,
        smoothRounding = smoothRounding,
        content = content,
    )
}
