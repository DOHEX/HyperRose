package com.dohex.hyperrose.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

internal data class StoredSettings(
    val colorModeName: String?,
    val monetEnabled: Boolean?,
    val blurEnabled: Boolean?,
    val liquidGlassEnabled: Boolean?,
    val floatingBottomBarEnabled: Boolean?,
)

private const val SETTINGS_DATASTORE_NAME = "settings"

private val Context.settingsDataStore: DataStore<Preferences> by
preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

private object Keys {
    val COLOR_MODE = stringPreferencesKey("theme_color_mode")
    val MONET_ENABLED = booleanPreferencesKey("theme_monet_enabled")
    val BLUR_ENABLED = booleanPreferencesKey("theme_blur_enabled")
    val LIQUID_GLASS_ENABLED = booleanPreferencesKey("theme_liquid_glass_enabled")
    val FLOATING_BOTTOM_BAR_ENABLED = booleanPreferencesKey("theme_floating_bottom_bar_enabled")
}

class SettingsDataStore(
    context: Context,
) {
    private val appContext = context.applicationContext

    internal val settings: Flow<StoredSettings> =
        appContext.settingsDataStore.data
            .catch { throwable ->
                if (throwable is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw throwable
                }
            }.map { preferences ->
                preferences.toStoredSettings()
            }

    internal suspend fun update(transform: (StoredSettings) -> StoredSettings) {
        appContext.settingsDataStore.edit { preferences ->
            val current = preferences.toStoredSettings()
            val next = transform(current)
            preferences.write(next)
        }
    }
}

private fun Preferences.toStoredSettings(): StoredSettings =
    StoredSettings(
        colorModeName = this[Keys.COLOR_MODE],
        monetEnabled = this[Keys.MONET_ENABLED],
        blurEnabled = this[Keys.BLUR_ENABLED],
        liquidGlassEnabled = this[Keys.LIQUID_GLASS_ENABLED],
        floatingBottomBarEnabled = this[Keys.FLOATING_BOTTOM_BAR_ENABLED],
    )

private fun MutablePreferences.write(settings: StoredSettings) {
    if (settings.colorModeName == null) {
        remove(Keys.COLOR_MODE)
    } else {
        this[Keys.COLOR_MODE] = settings.colorModeName
    }

    if (settings.monetEnabled == null) {
        remove(Keys.MONET_ENABLED)
    } else {
        this[Keys.MONET_ENABLED] = settings.monetEnabled
    }

    if (settings.blurEnabled == null) {
        remove(Keys.BLUR_ENABLED)
    } else {
        this[Keys.BLUR_ENABLED] = settings.blurEnabled
    }

    if (settings.liquidGlassEnabled == null) {
        remove(Keys.LIQUID_GLASS_ENABLED)
    } else {
        this[Keys.LIQUID_GLASS_ENABLED] = settings.liquidGlassEnabled
    }

    if (settings.floatingBottomBarEnabled == null) {
        remove(Keys.FLOATING_BOTTOM_BAR_ENABLED)
    } else {
        this[Keys.FLOATING_BOTTOM_BAR_ENABLED] = settings.floatingBottomBarEnabled
    }
}
