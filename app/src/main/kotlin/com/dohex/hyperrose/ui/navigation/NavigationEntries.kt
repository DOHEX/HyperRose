package com.dohex.hyperrose.ui.navigation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.screen.BleDebugPage
import com.dohex.hyperrose.ui.screen.DeviceDetailPage
import com.dohex.hyperrose.ui.screen.DevicePickerPage
import com.dohex.hyperrose.ui.screen.EarphoneColorSettingsPage
import com.dohex.hyperrose.ui.screen.SettingsPage
import com.dohex.hyperrose.ui.screen.StatusPage
import com.dohex.hyperrose.ui.screen.ThemeSettingsPage
import com.dohex.hyperrose.ui.state.ConnectionTransport
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.state.RoseDeviceItem
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import androidx.compose.ui.res.stringResource
import com.dohex.hyperrose.R
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.nav.core.NavEntryBuilder
import top.yukonga.miuix.kmp.nav.transition.NavSwipeDirection

internal fun NavEntryBuilder.registerNavigationEntries(
    selectedTab: MutableState<TopLevelTab>,
    deviceControlStore: DeviceControlStore,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
    pendingRestart: State<Boolean>,
    onConsumeRestart: () -> Unit,
    onRequestRestart: () -> Unit,
    onBack: () -> Unit,
) {
    entry<Route.Main>(swipeDismiss = NavSwipeDirection.None) {
        MainRoute(
            selectedTab = selectedTab,
            deviceControlStore = deviceControlStore,
            onUpdateThemeSettings = onUpdateThemeSettings,
            pendingRestart = pendingRestart,
            onConsumeRestart = onConsumeRestart,
            onRequestRestart = onRequestRestart,
        )
    }
    entry<Route.DeviceDetail>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
        DeviceDetailRoute(
            route = route,
            deviceControlStore = deviceControlStore,
            onBack = onBack,
        )
    }
    entry<Route.BleDebug>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
        BleDebugRoute(
            route = route,
            deviceControlStore = deviceControlStore,
            onBack = onBack,
        )
    }
    entry<Route.ThemeSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) {
        ThemeSettingsRoute(
            onBack = onBack,
            onUpdateThemeSettings = onUpdateThemeSettings,
        )
    }
    entry<Route.EarphoneColorSettings>(swipeDismiss = NavSwipeDirection.LeftToRight) { route ->
        EarphoneColorSettingsRoute(route = route, onBack = onBack)
    }
}

@Composable
private fun MainRoute(
    selectedTab: MutableState<TopLevelTab>,
    deviceControlStore: DeviceControlStore,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
    pendingRestart: State<Boolean>,
    onConsumeRestart: () -> Unit,
    onRequestRestart: () -> Unit,
) {
    val currentTab by selectedTab
    val pagerState =
        rememberPagerState(
            initialPage = currentTab.pageIndex,
            pageCount = { TopLevelTab.entries.size },
        )
    val mainPagerState = rememberMainPagerState(pagerState)
    val currentPage = mainPagerState.pagerState.currentPage
    LaunchedEffect(currentPage) {
        mainPagerState.syncPage()
        val settled = TopLevelTab.entries[currentPage.coerceIn(0, TopLevelTab.entries.lastIndex)]
        if (selectedTab.value != settled && !mainPagerState.isNavigating) {
            selectedTab.value = settled
        }
    }
    LaunchedEffect(currentTab) {
        mainPagerState.animateToPage(currentTab.pageIndex)
    }

    MiuixNavWrapper(
        pagerState = pagerState,
        mainPagerState = mainPagerState,
        deviceControlStore = deviceControlStore,
        onTabChanged = { selectedTab.value = it },
        onUpdateThemeSettings = onUpdateThemeSettings,
        pendingRestart = pendingRestart,
        onConsumeRestart = onConsumeRestart,
        onRequestRestart = onRequestRestart,
        tabs = navigationTabs(),
    )
}

@Composable
private fun navigationTabs(): List<NavigationTab> {
    val moduleLabel = stringResource(R.string.tab_module)
    val earphonesLabel = stringResource(R.string.tab_earphones)
    val settingsLabel = stringResource(R.string.tab_settings)
    return remember(moduleLabel, earphonesLabel, settingsLabel) {
        listOf(
            NavigationTab(TopLevelTab.MODULE, moduleLabel, MiuixIcons.Home),
            NavigationTab(TopLevelTab.EARPHONES, earphonesLabel, MiuixIcons.SearchDevice),
            NavigationTab(TopLevelTab.SETTINGS, settingsLabel, MiuixIcons.Settings),
        )
    }
}


@Composable
internal fun MainPager(
    pagerState: PagerState,
    deviceControlStore: DeviceControlStore,
    onTabChanged: (TopLevelTab) -> Unit,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
    pendingRestart: State<Boolean>,
    onConsumeRestart: () -> Unit,
    onRequestRestart: () -> Unit,
    outerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    backdrop: LayerBackdrop? = null,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.then(
            if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier,
        ),
        userScrollEnabled = true,
        beyondViewportPageCount = 1,
    ) { page ->
        when (TopLevelTab.entries[page]) {
            TopLevelTab.MODULE -> {
                ModuleRoute(
                    deviceControlStore = deviceControlStore,
                    onRequestRestart = onRequestRestart,
                    onSelectTab = onTabChanged,
                    outerPadding = outerPadding,
                )
            }

            TopLevelTab.EARPHONES -> {
                EarphonesRoute(
                    deviceControlStore = deviceControlStore,
                    outerPadding = outerPadding,
                )
            }

            TopLevelTab.SETTINGS -> {
                SettingsRoute(
                    pendingRestart = pendingRestart,
                    onConsumeRestart = onConsumeRestart,
                    outerPadding = outerPadding,
                )
            }
        }
    }
}

@Composable
private fun ModuleRoute(
    deviceControlStore: DeviceControlStore,
    onRequestRestart: () -> Unit,
    onSelectTab: (TopLevelTab) -> Unit,
    outerPadding: PaddingValues,
) {

    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val bluetoothEnabled by deviceControlStore.bluetoothEnabled.collectAsState()
    val pairedDevices by deviceControlStore.pairedDevices.collectAsState()
    val activeAddress by deviceControlStore.activeAddress.collectAsState()

    StatusPage(
        bluetoothEnabled = bluetoothEnabled,
        bondedDeviceCount = pairedDevices.size,
        onBluetoothStatusClick = {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            }
        },
        onPairedDeviceClick = {
            onSelectTab(TopLevelTab.EARPHONES)
        },
        onOpenDebug = {
            val address = activeAddress
            if (address == null) {
                onSelectTab(TopLevelTab.EARPHONES)
            } else {
                onSelectTab(TopLevelTab.EARPHONES)
                navigator.replaceAll(
                    Route.Main,
                    Route.BleDebug(address),
                )
            }
        },
        onOpenRestart = onRequestRestart,
        outerPadding = outerPadding,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun EarphonesRoute(
    deviceControlStore: DeviceControlStore,
    outerPadding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val hasPermission by deviceControlStore.hasBluetoothPermission.collectAsState()
    val pairedDevices by deviceControlStore.pairedDevices.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val activeAddress by deviceControlStore.activeAddress.collectAsState()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
        ) {
            deviceControlStore.refreshPermissionState()
            deviceControlStore.refreshBondedDevices()
        }

    DevicePickerPage(
        hasPermission = hasPermission,
        devices = pairedDevices,
        connectionState = connectionState,
        activeAddress = activeAddress,
        onRequestPermission = {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN,
                ),
            )
        },
        onRefresh = {
            deviceControlStore.refreshPermissionState()
            deviceControlStore.refreshBondedDevices()
        },
        onConnect = { address ->
            navigator.push(Route.DeviceDetail(address))
            if (!sameAddress(address, activeAddress) ||
                connectionState == DeviceConnectionState.DISCONNECTED
            ) {
                deviceControlStore.connectDirect(address)
            }
        },
        onDisconnect = deviceControlStore::disconnect,
        outerPadding = outerPadding,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun SettingsRoute(
    pendingRestart: State<Boolean>,
    onConsumeRestart: () -> Unit,
    outerPadding: PaddingValues,
) {
    val navigator = LocalNavigator.current
    val shouldOpenRestart by pendingRestart
    var restartRequest by remember { mutableIntStateOf(0) }

    LaunchedEffect(shouldOpenRestart) {
        if (shouldOpenRestart) {
            restartRequest++
            onConsumeRestart()
        }
    }

    SettingsPage(
        restartRequest = restartRequest,
        onOpenThemeSettings = { navigator.push(Route.ThemeSettings) },
        outerPadding = outerPadding,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ThemeSettingsRoute(
    onBack: () -> Unit,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    ThemeSettingsPage(
        onBack = onBack,
        onUpdateThemeSettings = onUpdateThemeSettings,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DeviceDetailRoute(
    route: Route.DeviceDetail,
    deviceControlStore: DeviceControlStore,
    onBack: () -> Unit,
) {
    val navigator = LocalNavigator.current
    val pairedDevices by deviceControlStore.pairedDevices.collectAsState()
    val activeAddress by deviceControlStore.activeAddress.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val transport by deviceControlStore.transport.collectAsState()
    val storeDeviceName by deviceControlStore.deviceName.collectAsState()
    val storeProfile by deviceControlStore.profile.collectAsState()
    val storeBattery by deviceControlStore.battery.collectAsState()
    val storeAncMode by deviceControlStore.ancMode.collectAsState()
    val storeAncDepth by deviceControlStore.ancDepth.collectAsState()
    val storeTransLevel by deviceControlStore.transLevel.collectAsState()
    val storeEqMode by deviceControlStore.eqMode.collectAsState()
    val storeGameMode by deviceControlStore.gameMode.collectAsState()
    val storeLowLatency by deviceControlStore.lowLatency.collectAsState()
    val storeCapabilities by deviceControlStore.capabilities.collectAsState()

    val pairedDevice = pairedDevices.firstOrNull { sameAddress(it.address, route.address) }
    val catalogProfile = pairedDevice?.let(::profileForDevice)
    val isActiveDevice = sameAddress(activeAddress, route.address)
    val deviceProfile = if (isActiveDevice) storeProfile ?: catalogProfile else catalogProfile
    val deviceName = if (isActiveDevice) storeDeviceName ?: pairedDevice?.name else pairedDevice?.name

    if (deviceProfile == null) {
        DeviceUnavailablePage(onBack = onBack)
        return
    }

    val routeConnectionState =
        if (isActiveDevice) connectionState else DeviceConnectionState.DISCONNECTED
    val routeTransport = if (isActiveDevice) transport else ConnectionTransport.NONE
    val routeCapabilities =
        if (isActiveDevice) storeCapabilities else deviceProfile.capabilities

    DeviceDetailPage(
        address = route.address,
        connectionState = routeConnectionState,
        transport = routeTransport,
        deviceName = deviceName,
        deviceProfile = deviceProfile,
        battery = if (isActiveDevice) storeBattery else null,
        ancMode = if (isActiveDevice) storeAncMode else null,
        ancDepth = if (isActiveDevice) storeAncDepth else null,
        transLevel = if (isActiveDevice) storeTransLevel else null,
        eqMode = if (isActiveDevice) storeEqMode else null,
        gameMode = if (isActiveDevice) storeGameMode else false,
        lowLatency = if (isActiveDevice) storeLowLatency else false,
        capabilities = routeCapabilities,
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
        onConnect = { deviceControlStore.connectDirect(route.address) },
        onOpenBleDebug = {
            if (isActiveDevice && routeConnectionState == DeviceConnectionState.CONNECTED) {
                navigator.push(Route.BleDebug(route.address))
            }
        },
        onOpenColorSettings = {
            val visuals = DeviceCatalog.findById(deviceProfile.id)?.visuals
            if (visuals != null) {
                navigator.push(
                    Route.EarphoneColorSettings(
                        address = route.address,
                        deviceId = deviceProfile.id,
                    ),
                )
            }
        },
        onBack = onBack,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun BleDebugRoute(
    route: Route.BleDebug,
    deviceControlStore: DeviceControlStore,
    onBack: () -> Unit,
) {
    val activeAddress by deviceControlStore.activeAddress.collectAsState()
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val profile by deviceControlStore.profile.collectAsState()

    if (
        profile == null ||
        !sameAddress(activeAddress, route.address) ||
        connectionState != DeviceConnectionState.CONNECTED
    ) {
        DeviceUnavailablePage(onBack = onBack)
    } else {
        BleDebugPage(
            deviceControlStore = deviceControlStore,
            profile = profile!!,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EarphoneColorSettingsRoute(
    route: Route.EarphoneColorSettings,
    onBack: () -> Unit,
) {
    val visuals = DeviceCatalog.findById(route.deviceId)?.visuals

    if (visuals == null) {
        DeviceUnavailablePage(onBack = onBack)
    } else {
        EarphoneColorSettingsPage(
            address = route.address,
            visuals = visuals,
            onBack = onBack,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DeviceUnavailablePage(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = "设备不可用",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("该设备可能已断开、取消配对，或相关信息暂时不可用。")
            ActionButton(
                text = "返回",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun profileForDevice(device: RoseDeviceItem): DeviceProfile? =
    device.profileId?.let { DeviceCatalog.findById(it)?.profile }
        ?: DeviceCatalog.findByName(device.name)?.profile

private fun sameAddress(first: String?, second: String?): Boolean =
    first != null && second != null && first.equals(second, ignoreCase = true)
