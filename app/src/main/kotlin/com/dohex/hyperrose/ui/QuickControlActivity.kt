package com.dohex.hyperrose.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dohex.hyperrose.HyperRoseApp
import com.dohex.hyperrose.ipc.QuickControlLaunchValidator
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.ui.screen.PopupControlPanel
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalCanUpdateThemeMode
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.ThemeMode
import com.dohex.hyperrose.ui.theme.ThemeSettingsStore
import com.dohex.hyperrose.ipc.HyperRoseIpc as HyperRoseAction

/** 控制中心弹出面板 Activity。 由 Hook 或通知从外部启动。 */
class QuickControlActivity : ComponentActivity() {
    companion object {
        const val EXTRA_DEVICE_NAME = HyperRoseAction.EXTRA_DEVICE_NAME
        const val EXTRA_FORCE_CONNECTED = HyperRoseAction.EXTRA_FORCE_CONNECTED
        private val DEFAULT_DEVICE_NAME =
            com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.displayName
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!QuickControlLaunchValidator.isTrustedCaller(callingActivity, referrer)) {
            finish()
            return
        }

        runCatching { setFinishOnTouchOutside(true) }

        val presetDeviceName = intent.getStringExtra(EXTRA_DEVICE_NAME)
        val presetDeviceAddress = intent.getStringExtra(HyperRoseAction.EXTRA_DEVICE_ADDRESS)
        val presetLeftLevel = intent.getIntExtra(HyperRoseAction.EXTRA_LEFT_LEVEL, -1)
        val presetRightLevel = intent.getIntExtra(HyperRoseAction.EXTRA_RIGHT_LEVEL, -1)
        val presetCaseLevel = intent.getIntExtra(HyperRoseAction.EXTRA_CASE_LEVEL, -1)
        val presetProfileId = intent.getStringExtra(HyperRoseAction.EXTRA_PROFILE_ID)
        val forceConnected = intent.getBooleanExtra(EXTRA_FORCE_CONNECTED, false)

        setContent {
            val deviceControlStore = HyperRoseApp.deviceControlStore
            val themeStore = remember { ThemeSettingsStore(this) }
            val themeMode by themeStore.themeModeFlow.collectAsState(initial = ThemeMode())
            var showDialog by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                deviceControlStore.refreshStatus()
            }

            LaunchedEffect(Unit) {
                val currentName = deviceControlStore.deviceName.value
                val hasPresetBattery = presetLeftLevel >= 0 || presetRightLevel >= 0 || presetCaseLevel >= 0
                val shouldApplyFallback =
                    forceConnected || !presetDeviceName.isNullOrBlank() || hasPresetBattery
                if (currentName.isNullOrBlank() && shouldApplyFallback) {
                    deviceControlStore.setTemporaryConnectionState(
                        name = presetDeviceName ?: DEFAULT_DEVICE_NAME,
                        battery = buildPresetBattery(presetLeftLevel, presetRightLevel, presetCaseLevel),
                        profileId = presetProfileId,
                    )
                }
            }

            HyperRoseTheme(
                colorMode = themeMode.colorMode,
            ) {
                CompositionLocalProvider(
                    LocalThemeMode provides themeMode,
                    LocalCanUpdateThemeMode provides false,
                ) {
                    PopupControlPanel(
                        deviceControlStore = deviceControlStore,
                        show = showDialog,
                        onDismissRequest = { showDialog = false },
                        onDismissFinish = { finish() },
                    )
                }
            }
        }
    }


    private var hasResumed = false

    override fun onResume() {
        super.onResume()
        hasResumed = true
    }

    override fun onPause() {
        super.onPause()
        if (hasResumed && !isFinishing) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun buildPresetBattery(
        leftLevel: Int,
        rightLevel: Int,
        caseLevel: Int = -1,
    ): TwsBatteryState? {
        val normalizedLeftLevel = leftLevel.asBatteryLevelOrNull()
        val normalizedRightLevel = rightLevel.asBatteryLevelOrNull()
        val normalizedCaseLevel = caseLevel.asBatteryLevelOrNull()
        if (normalizedLeftLevel == null && normalizedRightLevel == null && normalizedCaseLevel == null) return null
        val allLevels = listOfNotNull(normalizedLeftLevel, normalizedRightLevel, normalizedCaseLevel)
        val nonZeroCount = allLevels.count { it > 0 }
        if (nonZeroCount == 1) {
            val realLevel = allLevels.first { it > 0 }
            return TwsBatteryState(
                left = EarBatteryState(realLevel, false),
                right = null, caseBattery = null,
            )
        }
        return TwsBatteryState(
            left = normalizedLeftLevel?.let { EarBatteryState(it, false) },
            right = normalizedRightLevel?.let { EarBatteryState(it, false) },
            caseBattery = if (normalizedLeftLevel != null) normalizedCaseLevel else null,
        )
    }
}
