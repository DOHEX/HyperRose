package com.dohex.hyperrose.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.ui.screen.BleDebugPage
import com.dohex.hyperrose.ui.screen.DeviceDetailPage
import com.dohex.hyperrose.ui.screen.DevicePickerPage
import com.dohex.hyperrose.ui.screen.EarphoneColorSettingsPage
import com.dohex.hyperrose.ui.screen.SettingsPage
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppDestination : NavKey {
    @Serializable
    data object DeviceList : AppDestination

    @Serializable
    data class DeviceDetail(val address: String, val name: String) : AppDestination

    @Serializable
    data object Settings : AppDestination

    @Serializable
    data object BleDebug : AppDestination

    @Serializable
    data class EarphoneColorSettings(val address: String, val deviceId: String) : AppDestination
}

@Composable
fun AppNavHost(deviceControlStore: DeviceControlStore) {
    val backStack = remember { mutableStateListOf<NavKey>(AppDestination.DeviceList) }

    val hasPermission by deviceControlStore.hasBluetoothPermission.collectAsState()
    val pairedDevices by deviceControlStore.pairedDevices.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val transport by deviceControlStore.transport.collectAsState()
    val deviceName by deviceControlStore.deviceName.collectAsState()
    val profile by deviceControlStore.profile.collectAsState()
    val battery by deviceControlStore.battery.collectAsState()
    val ancMode by deviceControlStore.ancMode.collectAsState()
    val ancDepth by deviceControlStore.ancDepth.collectAsState()
    val transLevel by deviceControlStore.transLevel.collectAsState()
    val eqMode by deviceControlStore.eqMode.collectAsState()
    val gameMode by deviceControlStore.gameMode.collectAsState()
    val lowLatency by deviceControlStore.lowLatency.collectAsState()
    val capabilities by deviceControlStore.capabilities.collectAsState()
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            deviceControlStore.refreshPermissionState()
            deviceControlStore.refreshBondedDevices()
        }

    LaunchedEffect(Unit) {
        deviceControlStore.refreshPermissionState()
        deviceControlStore.refreshBondedDevices()
        deviceControlStore.refreshStatus()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            deviceControlStore.refreshBondedDevices()
        }
    }

    val requestPermissions = {
        val permissions =
            buildList {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    val entryProvider = remember(
        hasPermission, pairedDevices, connectionState, transport, deviceName,
        battery, ancMode, ancDepth, transLevel, eqMode, gameMode, lowLatency, capabilities, profile,
    ) {
        entryProvider<NavKey> {
            entry<AppDestination.DeviceList> {
                DevicePickerPage(
                    hasPermission = hasPermission,
                    devices = pairedDevices,
                    connectionState = connectionState,
                    onRequestPermission = requestPermissions,
                    onRefresh = deviceControlStore::refreshBondedDevices,
                    onConnect = { address ->
                        val selected = pairedDevices.firstOrNull { it.address == address }
                        if (selected?.isSupported == true && connectionState != DeviceConnectionState.CONNECTED) {
                            deviceControlStore.connectDirect(address)
                        }
                        if (selected?.isSupported == true) {
                            backStack.add(
                                AppDestination.DeviceDetail(
                                    address = address,
                                    name = selected.name,
                                )
                            )
                        }
                    },
                    onOpenSettings = { backStack.add(AppDestination.Settings) },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            entry<AppDestination.DeviceDetail> {
                DeviceDetailPage(
                    address = it.address,
                    connectionState = connectionState,
                    transport = transport,
                    deviceName = deviceName ?: it.name,
                    deviceProfile = profile,
                    battery = battery,
                    ancMode = ancMode,
                    ancDepth = ancDepth,
                    transLevel = transLevel,
                    eqMode = eqMode,
                    gameMode = gameMode,
                    lowLatency = lowLatency,
                    capabilities = capabilities,
                    onAncModeChange = deviceControlStore::setAnc,
                    onAncDepthChange = deviceControlStore::setAncDepth,
                    onTransLevelChange = deviceControlStore::setTransLevel,
                    onEqModeChange = deviceControlStore::setEq,
                    onGameModeChange = deviceControlStore::setGameMode,
                    onLowLatencyChange = deviceControlStore::setLowLatency,
                    onFindLeft = deviceControlStore::findLeft,
                    onFindRight = deviceControlStore::findRight,
                    onStopFind = deviceControlStore::stopFind,
                    onRefreshStatus = deviceControlStore::refreshStatus,
                    onDisconnect = deviceControlStore::disconnect,
                    onConnect = { deviceControlStore.connectDirect(it.address) },
                    onOpenBleDebug = { backStack.add(AppDestination.BleDebug) },
                    onOpenColorSettings = {
                        profile?.let { currentProfile ->
                            if (DeviceCatalog.findById(currentProfile.id)?.visuals != null) {
                                backStack.add(
                                    AppDestination.EarphoneColorSettings(
                                        address = it.address,
                                        deviceId = currentProfile.id,
                                    )
                                )
                            }
                        }
                    },
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            entry<AppDestination.Settings> {
                SettingsPage(
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            entry<AppDestination.BleDebug> {
                profile?.let { debugProfile ->
                    BleDebugPage(
                        deviceControlStore = deviceControlStore,
                        profile = debugProfile,
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            entry<AppDestination.EarphoneColorSettings> {
                DeviceCatalog.findById(it.deviceId)?.visuals?.let { visuals ->
                    EarphoneColorSettingsPage(
                        address = it.address,
                        visuals = visuals,
                        onBack = { if (backStack.size > 1) backStack.removeLast() },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    val entries = rememberDecoratedNavEntries(backStack = backStack, entryProvider = entryProvider)

    NavDisplay(entries = entries, onBack = { if (backStack.size > 1) backStack.removeLast() })
}
