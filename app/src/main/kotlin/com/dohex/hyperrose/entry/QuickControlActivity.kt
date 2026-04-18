package com.dohex.hyperrose.entry

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.dohex.hyperrose.domain.battery.EarBatteryState
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction
import com.dohex.hyperrose.ui.screen.popup.PopupControlPanel
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalCanUpdateThemeMode
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.ThemeMode
import com.dohex.hyperrose.ui.theme.ThemeSettingsStore

/**
 * 控制中心弹出面板 Activity。
 * 由 DeviceCardHook 从 SystemUI 启动。
 */
class QuickControlActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEVICE_NAME = HyperRoseAction.EXTRA_DEVICE_NAME
        const val EXTRA_FORCE_CONNECTED = HyperRoseAction.EXTRA_FORCE_CONNECTED
        private const val DEFAULT_DEVICE_NAME = "ROSE EARFREE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching {
            setFinishOnTouchOutside(true)
        }

        val presetDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val presetLeftLevel = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
        val presetRightLevel = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
        val forceConnected = intent.getBooleanExtra(EXTRA_FORCE_CONNECTED, false)

        setContent {
            val context = LocalContext.current
            val deviceControlStore = remember(context) { DeviceControlStore(context) }
            val themeStore = remember(context) { ThemeSettingsStore(context) }
            val themeMode by themeStore.themeModeFlow.collectAsState(initial = ThemeMode())
            var showDialog by remember { mutableStateOf(true) }

            DisposableEffect(deviceControlStore) {
                deviceControlStore.refreshStatus()
                onDispose {
                    deviceControlStore.release()
                }
            }

            LaunchedEffect(deviceControlStore) {
                val currentName = deviceControlStore.deviceName.value
                val hasPresetBattery = presetLeftLevel >= 0 || presetRightLevel >= 0
                val shouldApplyFallback = forceConnected || !presetDeviceName.isNullOrBlank() || hasPresetBattery
                if (currentName.isNullOrBlank() && shouldApplyFallback) {
                    deviceControlStore.setTemporaryConnectionState(
                        name = presetDeviceName ?: DEFAULT_DEVICE_NAME,
                        battery = buildPresetBattery(presetLeftLevel, presetRightLevel)
                    )
                }
            }

            HyperRoseTheme(
                colorMode = themeMode.colorMode,
                smoothRounding = themeMode.smoothRounding,
            ) {
                CompositionLocalProvider(
                    LocalThemeMode provides themeMode,
                    LocalCanUpdateThemeMode provides false,
                ) {
                    PopupControlPanel(
                        deviceControlStore = deviceControlStore,
                        show = showDialog,
                        onDismissRequest = { showDialog = false },
                        onDismissFinished = { finish() }
                    )
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun buildPresetBattery(leftLevel: Int, rightLevel: Int): TwsBatteryState? {
        if (leftLevel < 0 && rightLevel < 0) return null
        return TwsBatteryState(
            left = leftLevel.takeIf { it >= 0 }?.let { EarBatteryState(it, false) },
            right = rightLevel.takeIf { it >= 0 }?.let { EarBatteryState(it, false) },
            caseBattery = null
        )
    }
}
