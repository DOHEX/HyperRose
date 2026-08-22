package com.dohex.hyperrose.data.repository

import com.dohex.hyperrose.data.local.SettingsDataStore
import com.dohex.hyperrose.data.local.StoredSettings
import com.dohex.hyperrose.model.ColorMode
import com.dohex.hyperrose.model.ThemeSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class ThemeSettingsRepository(
    private val local: SettingsDataStore,
) {
    val themeSettings: Flow<ThemeSettings> =
        local.settings.map { stored -> stored.toThemeSettings() }.distinctUntilChanged()

    suspend fun updateThemeSettings(
        transform: (ThemeSettings) -> ThemeSettings,
    ) {
        local.update { stored ->
            stored.toThemeSettings().let(transform).toStoredSettings()
        }
    }
}

private fun StoredSettings.toThemeSettings(): ThemeSettings = ThemeSettings(
    colorMode = colorModeName?.let { name ->
        runCatching { ColorMode.valueOf(name) }.getOrNull()
    } ?: ColorMode.SYSTEM,
    monetEnabled = monetEnabled ?: false,
    blurEnabled = blurEnabled ?: true,
    liquidGlassEnabled = liquidGlassEnabled ?: true,
    floatingBottomBarEnabled = floatingBottomBarEnabled ?: false,
)

private fun ThemeSettings.toStoredSettings(): StoredSettings = StoredSettings(
    colorModeName = colorMode.name,
    monetEnabled = monetEnabled,
    blurEnabled = blurEnabled,
    liquidGlassEnabled = liquidGlassEnabled,
    floatingBottomBarEnabled = floatingBottomBarEnabled,
)
