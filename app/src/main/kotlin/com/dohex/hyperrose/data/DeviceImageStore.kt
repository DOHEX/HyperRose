package com.dohex.hyperrose.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dohex.hyperrose.model.DeviceColorProfile
import com.dohex.hyperrose.model.DeviceColorTheme
import com.dohex.hyperrose.model.EarphoneColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

val LocalDeviceImageStore = staticCompositionLocalOf<DeviceImageStore> {
    error("No DeviceImageStore provided")
}

private const val DEVICE_IMAGE_DATASTORE_NAME = "device_image_prefs"

private val Context.deviceImageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = DEVICE_IMAGE_DATASTORE_NAME,
)

/**
 * Migrates legacy [com.dohex.hyperrose.model.EarphoneColorTheme] enum names
 * (e.g. "I5_BLUE") to [EarphoneColor] names (e.g. "BLUE").
 */
private val legacyColorNameMap: Map<String, String> = mapOf(
    "I5_BLUE" to "BLUE",
    "I5_GOLD" to "GOLD",
    "I5_GRAY" to "GRAY",
    "MK2_BLUE" to "BLUE",
    "MK2_SILVER" to "SILVER",
    "MK2_BLACK" to "BLACK",
)

class DeviceImageStore(context: Context) {
    private val appContext = context.applicationContext

    private fun colorKey(address: String) =
        stringPreferencesKey("earphone_color_$address")

    /**
     * Emits the current [DeviceColorTheme] for [address], falling back to
     * [profile].[defaultTheme][DeviceColorProfile.defaultTheme] when no preference is stored.
     * [profile] is also used to resolve the stored [EarphoneColor] back to device images.
     */
    fun colorThemeFlow(
        address: String,
        profile: DeviceColorProfile,
    ): Flow<DeviceColorTheme> {
        return appContext.deviceImageDataStore.data
            .catch { throwable ->
                if (throwable !is IOException) throw throwable
                emit(androidx.datastore.preferences.core.emptyPreferences())
            }
            .map { prefs ->
                val name = prefs[colorKey(address)]
                if (name != null) {
                    val migratedName = legacyColorNameMap[name] ?: name
                    val color = runCatching { EarphoneColor.valueOf(migratedName) }.getOrNull()
                    if (color != null) {
                        profile.themeFor(color) ?: profile.defaultTheme()
                    } else {
                        profile.defaultTheme()
                    }
                } else {
                    profile.defaultTheme()
                }
            }
            .distinctUntilChanged()
    }

    suspend fun setColorTheme(address: String, theme: DeviceColorTheme) {
        appContext.deviceImageDataStore.edit { prefs ->
            prefs[colorKey(address)] = theme.color.name
        }
    }
}
