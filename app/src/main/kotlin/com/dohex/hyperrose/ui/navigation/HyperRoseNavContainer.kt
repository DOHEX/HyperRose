package com.dohex.hyperrose.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.core.rememberNavSystemCornerRadius
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class SettingsAction {
    RESTART_SCOPES,
}

@Composable
fun HyperRoseNavContainer(
    deviceControlStore: DeviceControlStore,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val backStack = rememberNavBackStack<Route>(Route.Main)
    val navigator = remember(backStack) { Navigator(backStack) }
    val hasPermission by deviceControlStore.hasBluetoothPermission.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val activeAddress by deviceControlStore.activeAddress.collectAsState()
    val selectedTab = rememberSaveable { mutableStateOf(TopLevelTab.MODULE) }
    var pendingSettingsAction by remember { mutableStateOf<SettingsAction?>(null) }
    val pendingRestart = remember {
        derivedStateOf { pendingSettingsAction == SettingsAction.RESTART_SCOPES }
    }
    var connectionDismissed by remember { mutableStateOf(false) }
    var observedConnectionState by remember { mutableStateOf(DeviceConnectionState.DISCONNECTED) }
    var observedAddress by remember { mutableStateOf<String?>(null) }
    val currentRoute = navigator.currentRoute

    fun handleBack() {
        if (currentRoute is Route.DeviceDetail && connectionState == DeviceConnectionState.CONNECTING) {
            connectionDismissed = true
        }
        navigator.pop()
    }

    LaunchedEffect(deviceControlStore) {
        deviceControlStore.refreshPermissionState()
        deviceControlStore.refreshBondedDevices()
        deviceControlStore.refreshStatus()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) deviceControlStore.refreshBondedDevices()
    }

    LaunchedEffect(connectionState, activeAddress, currentRoute, selectedTab.value) {
        val addressChanged = !sameAddress(activeAddress, observedAddress)
        val activeAddressChanged = activeAddress != null && addressChanged
        val connectionStarted =
            connectionState == DeviceConnectionState.CONNECTING &&
                (observedConnectionState != DeviceConnectionState.CONNECTING || addressChanged)
        val connectedSessionStarted =
            connectionState == DeviceConnectionState.CONNECTED &&
                activeAddress != null &&
                (observedConnectionState != DeviceConnectionState.CONNECTED || addressChanged)

        if (connectionStarted || (connectedSessionStarted && addressChanged)) {
            connectionDismissed = false
        }

        if (connectionState == DeviceConnectionState.DISCONNECTED) {
            when (currentRoute) {
                is Route.BleDebug -> navigator.pop()
                is Route.EarphoneColorSettings -> {
                    if (DeviceCatalog.findById(currentRoute.deviceId)?.visuals == null) {
                        navigator.pop()
                    }
                }
                else -> Unit
            }
        }

        fun normalizeEarphonesRoute(address: String) {
            val routeAddress = when (val route = navigator.currentRoute) {
                is Route.DeviceDetail -> route.address
                is Route.BleDebug -> route.address
                is Route.EarphoneColorSettings -> route.address
                else -> null
            }
            when {
                routeAddress == null -> navigator.push(Route.DeviceDetail(address))
                !sameAddress(routeAddress, address) -> {
                    navigator.replaceAll(
                        Route.Main,
                        Route.DeviceDetail(address),
                    )
                }
            }
        }

        val connectedAddress = activeAddress
        if (connectedAddress != null && !connectionDismissed) {
            val address = connectedAddress
            when {
                activeAddressChanged && selectedTab.value == TopLevelTab.EARPHONES -> {
                    normalizeEarphonesRoute(address)
                }

                connectedSessionStarted -> {
                    when (selectedTab.value) {
                        TopLevelTab.MODULE -> {
                            selectedTab.value = TopLevelTab.EARPHONES
                            navigator.replaceAll(
                                Route.Main,
                                Route.DeviceDetail(address),
                            )
                        }

                        TopLevelTab.EARPHONES -> normalizeEarphonesRoute(address)
                        TopLevelTab.SETTINGS -> Unit
                    }
                }
            }
        }

        observedConnectionState = connectionState
        observedAddress = activeAddress
    }

    val cornerRadius = rememberNavSystemCornerRadius()
    val navEffects =
        NavDisplayEffects(
            enableCornerClip = true,
            cornerClipRadius = cornerRadius,
            cornerClipMode = NavCornerClipMode.Leading,
            dimAmount = 0.5f,
            blockInputDuringTransition = true,
            backdropColor = MiuixTheme.colorScheme.surface,
        )

    CompositionLocalProvider(LocalNavigator provides navigator) {
        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = ::handleBack,
            transition = NavTransitions.MiuixDefault,
            effects = navEffects,
        ) {
            registerNavigationEntries(
                selectedTab = selectedTab,
                deviceControlStore = deviceControlStore,
                onUpdateThemeSettings = onUpdateThemeSettings,
                pendingRestart = pendingRestart,
                onConsumeRestart = { pendingSettingsAction = null },
                onRequestRestart = {
                    pendingSettingsAction = SettingsAction.RESTART_SCOPES
                    selectedTab.value = TopLevelTab.SETTINGS
                },
                onBack = ::handleBack,
            )
        }
    }
}

private fun sameAddress(first: String?, second: String?): Boolean =
    first != null && second != null && first.equals(second, ignoreCase = true)
