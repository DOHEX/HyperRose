package com.dohex.hyperrose.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val THEME_SETTINGS_DATASTORE_NAME = "theme_settings"

private val Context.themeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = THEME_SETTINGS_DATASTORE_NAME
)

class ThemeSettingsStore(context: Context) {

    private val appContext = context.applicationContext

    private object Keys {
        val COLOR_MODE = intPreferencesKey("theme_color_mode")
        val ENABLE_BLUR = booleanPreferencesKey("theme_enable_blur")
        val SMOOTH_ROUNDING = booleanPreferencesKey("theme_smooth_rounding")
    }

    val themeModeFlow: Flow<ThemeMode> = appContext.themeSettingsDataStore.data
        .catch { throwable ->
            if (throwable !is IOException) throw throwable
            emit(androidx.datastore.preferences.core.emptyPreferences())
        }
        .map { prefs ->
            ThemeMode(
                colorMode = prefs[Keys.COLOR_MODE] ?: 0,
                enableBlur = prefs[Keys.ENABLE_BLUR] ?: true,
                smoothRounding = prefs[Keys.SMOOTH_ROUNDING] ?: true,
            )
        }

    suspend fun updateThemeMode(transform: (ThemeMode) -> ThemeMode) {
        appContext.themeSettingsDataStore.edit { prefs ->
            val current = ThemeMode(
                colorMode = prefs[Keys.COLOR_MODE] ?: 0,
                enableBlur = prefs[Keys.ENABLE_BLUR] ?: true,
                smoothRounding = prefs[Keys.SMOOTH_ROUNDING] ?: true,
            )
            val next = transform(current)
            prefs[Keys.COLOR_MODE] = next.colorMode.coerceIn(0, 5)
            prefs[Keys.ENABLE_BLUR] = next.enableBlur
            prefs[Keys.SMOOTH_ROUNDING] = next.smoothRounding
        }
    }
}
