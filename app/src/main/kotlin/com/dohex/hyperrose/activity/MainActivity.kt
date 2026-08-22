package com.dohex.hyperrose.activity

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.dohex.hyperrose.data.DeviceImageStore
import com.dohex.hyperrose.data.LocalDeviceImageStore
import com.dohex.hyperrose.data.local.SettingsDataStore
import com.dohex.hyperrose.data.repository.ThemeSettingsRepository
import com.dohex.hyperrose.ipc.HookStatusProvider
import com.dohex.hyperrose.model.ColorMode
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.navigation.HyperRoseNavContainer
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HookStatusProvider.init()
        setContent {
            val context = LocalContext.current
            val deviceControlStore = remember(context) { DeviceControlStore(context) }
            val settingsDataStore = remember(context) { SettingsDataStore(context) }
            val themeRepository = remember(settingsDataStore) {
                ThemeSettingsRepository(settingsDataStore)
            }
            val themeSettings by themeRepository.themeSettings.collectAsState(
                initial = ThemeSettings(),
            )
            val deviceImageStore = remember(context) { DeviceImageStore(context) }
            val scope = rememberCoroutineScope()

            val isDarkMode =
                when (themeSettings.colorMode) {
                    ColorMode.DARK -> true
                    ColorMode.SYSTEM -> isSystemInDarkTheme()
                    ColorMode.LIGHT -> false
                }

            LaunchedEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkMode },
                    navigationBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkMode },
                )
            }

            val updateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit =
                remember(themeRepository) {
                    { transform ->
                        scope.launch {
                            themeRepository.updateThemeSettings(transform)
                        }
                    }
                }

            DisposableEffect(deviceControlStore) {
                onDispose { deviceControlStore.release() }
            }

            HyperRoseTheme(settings = themeSettings) {
                CompositionLocalProvider(
                    LocalDeviceImageStore provides deviceImageStore,
                ) {
                    HyperRoseNavContainer(
                        deviceControlStore = deviceControlStore,
                        onUpdateThemeSettings = updateThemeSettings,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { HookStatusProvider.refresh() }
    }
}