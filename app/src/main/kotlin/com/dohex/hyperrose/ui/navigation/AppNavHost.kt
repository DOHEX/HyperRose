package com.dohex.hyperrose.ui.navigation

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.ui.NavDisplay
import com.dohex.hyperrose.ui.page.DevicePickerPage
import com.dohex.hyperrose.ui.page.HomePage
import com.dohex.hyperrose.ui.page.SettingsPage
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
}

@Composable
fun AppNavHost(deviceControlStore: DeviceControlStore) {
    val backStack = remember { mutableStateListOf<NavKey>(AppDestination.DeviceList) }

    val hasPermission by deviceControlStore.hasBluetoothPermission.collectAsState()
    val pairedDevices by deviceControlStore.pairedDevices.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val transport by deviceControlStore.transport.collectAsState()
    val deviceName by deviceControlStore.deviceName.collectAsState()
    val battery by deviceControlStore.battery.collectAsState()
    val ancMode by deviceControlStore.ancMode.collectAsState()
    val ancDepth by deviceControlStore.ancDepth.collectAsState()
    val transLevel by deviceControlStore.transLevel.collectAsState()
    val eqMode by deviceControlStore.eqMode.collectAsState()
    val gameMode by deviceControlStore.gameMode.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
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
        val permissions = buildList {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }.toTypedArray()
        permissionLauncher.launch(permissions)
    }

    val entryProvider = remember(
        hasPermission,
        pairedDevices,
        connectionState,
        transport,
        deviceName,
        battery,
        ancMode,
        ancDepth,
        transLevel,
        eqMode,
        gameMode
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
                        deviceControlStore.connectDirect(address)
                        backStack.add(
                            AppDestination.DeviceDetail(
                                address = address,
                                name = selected?.name ?: address
                            )
                        )
                    },
                    onOpenSettings = { backStack.add(AppDestination.Settings) },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<AppDestination.DeviceDetail> {
                HomePage(
                    connectionState = connectionState,
                    transport = transport,
                    deviceName = deviceName ?: it.name,
                    battery = battery,
                    ancMode = ancMode,
                    ancDepth = ancDepth,
                    transLevel = transLevel,
                    eqMode = eqMode,
                    gameMode = gameMode,
                    onAncModeChange = deviceControlStore::setAnc,
                    onAncDepthChange = deviceControlStore::setAncDepth,
                    onTransLevelChange = deviceControlStore::setTransLevel,
                    onEqModeChange = deviceControlStore::setEq,
                    onGameModeChange = deviceControlStore::setGameMode,
                    onFindLeft = deviceControlStore::findLeft,
                    onFindRight = deviceControlStore::findRight,
                    onStopFind = deviceControlStore::stopFind,
                    onRefreshStatus = deviceControlStore::refreshStatus,
                    onDisconnect = deviceControlStore::disconnect,
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                    modifier = Modifier.fillMaxSize()
                )
            }

            entry<AppDestination.Settings> {
                SettingsPage(
                    onBack = { if (backStack.size > 1) backStack.removeLast() },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    val entries = rememberDecoratedNavEntries(
        backStack = backStack,
        entryProvider = entryProvider
    )

    NavDisplay(
        entries = entries,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            }
        }
    )

}
