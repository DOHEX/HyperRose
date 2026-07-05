package com.dohex.hyperrose.data

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dohex.hyperrose.model.EarphoneColorTheme
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

class DeviceImageStore(context: Context) {
    private val appContext = context.applicationContext

    private fun colorKey(address: String) =
        stringPreferencesKey("earphone_color_$address")

    fun colorThemeFlow(address: String): Flow<EarphoneColorTheme> =
        appContext.deviceImageDataStore.data
            .catch { throwable ->
                if (throwable !is IOException) throw throwable
                emit(androidx.datastore.preferences.core.emptyPreferences())
            }
            .map { prefs ->
                val name = prefs[colorKey(address)]
                if (name != null) {
                    runCatching { EarphoneColorTheme.valueOf(name) }
                        .getOrDefault(EarphoneColorTheme.DEFAULT)
                } else {
                    EarphoneColorTheme.DEFAULT
                }
            }
            .distinctUntilChanged()

    suspend fun setColorTheme(address: String, theme: EarphoneColorTheme) {
        appContext.deviceImageDataStore.edit { prefs ->
            prefs[colorKey(address)] = theme.name
        }
    }
}
