package com.dohex.hyperrose.ui

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
import com.dohex.hyperrose.ipc.HookStatusProvider
import com.dohex.hyperrose.ui.navigation.AppNavHost
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalCanUpdateThemeMode
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.LocalUpdateThemeMode
import com.dohex.hyperrose.ui.theme.ThemeMode
import com.dohex.hyperrose.ui.theme.ThemeSettingsStore
import kotlinx.coroutines.launch

class AppEntryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HookStatusProvider.init()
        setContent {
            val context = LocalContext.current
            val deviceControlStore = remember(context) { DeviceControlStore(context) }
            val themeStore = remember(context) { ThemeSettingsStore(context) }
            val deviceImageStore = remember(context) { DeviceImageStore(context) }
            val scope = rememberCoroutineScope()
            val themeMode by themeStore.themeModeFlow.collectAsState(initial = ThemeMode())

            val isDarkMode =
                when (themeMode.colorMode) {
                    2,
                    5,
                        -> true

                    0,
                    3,
                        -> isSystemInDarkTheme()

                    else -> false
                }

            LaunchedEffect(isDarkMode) {
                enableEdgeToEdge(
                    statusBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkMode },
                    navigationBarStyle =
                        SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { isDarkMode },
                )
            }

            val updateThemeMode: ((ThemeMode) -> ThemeMode) -> Unit =
                remember {
                    { transform -> scope.launch { themeStore.updateThemeMode(transform) } }
                }
            DisposableEffect(deviceControlStore) { onDispose { deviceControlStore.release() } }

            HyperRoseTheme(
                colorMode = themeMode.colorMode,
            ) {
                CompositionLocalProvider(
                    LocalDeviceImageStore provides deviceImageStore,
                    LocalThemeMode provides themeMode,
                    LocalUpdateThemeMode provides updateThemeMode,
                    LocalCanUpdateThemeMode provides true,
                ) {
                    AppNavHost(deviceControlStore = deviceControlStore)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch { HookStatusProvider.refresh() }
    }
}
