package com.dohex.hyperrose.model

enum class ColorMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class ThemeSettings(
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val monetEnabled: Boolean = false,
    val blurEnabled: Boolean = true,
    val liquidGlassEnabled: Boolean = true,
    val floatingBottomBarEnabled: Boolean = false,
)