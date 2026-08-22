package com.dohex.hyperrose.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.data.DeviceImageStore
import com.dohex.hyperrose.data.LocalDeviceImageStore
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.BatteryCard
import com.dohex.hyperrose.ui.component.EqSelector
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.ConnectionTransport
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun DeviceDetailPage(
    modifier: Modifier = Modifier,
    address: String,
    connectionState: DeviceConnectionState,
    transport: ConnectionTransport,
    deviceName: String?,
    deviceProfile: DeviceProfile? = null,
    battery: TwsBatteryState?,
    ancMode: AncMode?,
    ancDepth: AncDepth?,
    transLevel: TransparencyLevel?,
    eqMode: EqPreset?,
    gameMode: Boolean,
    lowLatency: Boolean,
    capabilities: com.dohex.hyperrose.profile.DeviceCapabilities,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
    onEqModeChange: (EqPreset) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onLowLatencyChange: (Boolean) -> Unit,
    onFindLeft: () -> Unit,
    onFindRight: () -> Unit,
    onStopFind: () -> Unit,
    onRefreshStatus: () -> Unit,
    onDisconnect: () -> Unit,
    onConnect: () -> Unit = {},
    onOpenBleDebug: () -> Unit = {},
    onOpenColorSettings: () -> Unit = {},
    onBack: () -> Unit,

    ) {
    val connected = connectionState == DeviceConnectionState.CONNECTED
    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.blurEnabled)
    val blurActive = themeSettings.blurEnabled && backdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var showFindDialog by remember { mutableStateOf(false) }
    val deviceImageStore = LocalDeviceImageStore.current
    val visuals = deviceProfile?.let { DeviceCatalog.findById(it.id)?.visuals }

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = deviceName ?: deviceProfile?.displayName ?: "HyperRose",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (deviceProfile != null && connected) {
                            IconButton(onClick = onOpenBleDebug) {
                                Icon(MiuixIcons.Info, contentDescription = "BLE 调试")
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 10.dp,
                    end = 10.dp,
                ),
            ) {
                if (visuals != null) {
                    item {
                        val colorTheme by deviceImageStore.colorThemeFlow(address, visuals)
                            .collectAsState(initial = visuals.defaultTheme())
                        Image(
                            painter = painterResource(colorTheme.caseRes),
                            contentDescription = deviceName ?: deviceProfile.displayName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .graphicsLayer {
                                    if (listState.firstVisibleItemIndex == 0) {
                                        val scrollOffset = listState.firstVisibleItemScrollOffset.toFloat()
                                        translationY = scrollOffset * 0.5f
                                        alpha = 1f - (scrollOffset / 600f).coerceIn(0f, 1f)
                                    }
                                },
                        )
                    }
                }

                item {
                    SectionCard(
                        title = stateTitle(connectionState),
                        subtitle = stateSubtitle(connectionState, transport),
                    ) {
                        if (!connected) {
                            ActionButton(
                                text = "连接",
                                onClick = onConnect,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionButton(
                                    text = "刷新状态",
                                    onClick = onRefreshStatus,
                                    modifier = Modifier.weight(1f),
                                )
                                ActionButton(
                                    text = "断开",
                                    onClick = onDisconnect,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }

                if (!connected || deviceProfile == null) return@LazyColumn

                item { BatteryCard(battery = battery) }

                item {
                    AncSelector(
                        ancMode = ancMode,
                        ancDepth = ancDepth,
                        transLevel = transLevel,
                        supportedAncModes = capabilities.supportedAncModes,
                        supportedAncDepths = capabilities.supportedAncDepths,
                        supportedTransLevels = capabilities.supportedTransLevels,
                        onAncModeChange = onAncModeChange,
                        onAncDepthChange = onAncDepthChange,
                        onTransLevelChange = onTransLevelChange,
                        enabled = true,
                        showAncDepth = capabilities.supportedAncDepths.isNotEmpty(),
                        showTransLevel = capabilities.supportedTransLevels.isNotEmpty(),
                    )
                }
                if (capabilities.supportedEqPresets.isNotEmpty()) {
                    item {
                        EqSelector(
                            eqMode = eqMode,
                            onSelect = onEqModeChange,
                            enabled = true,
                            supportedEqPresets = capabilities.supportedEqPresets,
                        )
                    }
                }
                if (capabilities.hasGameMode) {
                    item {
                        Card {
                            SwitchPreference(
                                title = "游戏模式",
                                checked = gameMode,
                                onCheckedChange = onGameModeChange
                            )
                        }
                    }
                }
                if (capabilities.hasLowLatency) {
                    item {
                        Card {
                            SwitchPreference(
                                title = "低延迟",
                                checked = lowLatency,
                                onCheckedChange = onLowLatencyChange
                            )
                        }
                    }
                }
                if (capabilities.hasFindEarphone) {
                    item {
                        Card {
                            ArrowPreference(
                                title = "查找耳机", onClick = { showFindDialog = true })
                        }
                    }
                }

                if (visuals != null) {
                    item {
                        val colorTheme by deviceImageStore.colorThemeFlow(address, visuals)
                            .collectAsState(initial = visuals.defaultTheme())
                        Card {
                            ArrowPreference(
                                title = "耳机颜色",
                                summary = colorTheme.label,
                                onClick = onOpenColorSettings,
                            )
                        }
                    }
                }
            }
        }

        OverlayDialog(
            title = "查找耳机",
            summary = "请不要佩戴耳机",
            show = showFindDialog,
            onDismissRequest = { showFindDialog = false },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "左耳",
                    onClick = {
                        onFindLeft()
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "停止",
                    onClick = {
                        onStopFind()
                    },
                    modifier = Modifier.weight(1f),
                )
                ActionButton(
                    text = "右耳",
                    onClick = {
                        onFindRight()
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private fun stateTitle(state: DeviceConnectionState): String = when (state) {
    DeviceConnectionState.CONNECTED -> "已连接"
    DeviceConnectionState.CONNECTING -> "连接中"
    DeviceConnectionState.DISCONNECTED -> "未连接"
}

private fun stateSubtitle(
    state: DeviceConnectionState,
    transport: ConnectionTransport,
): String = when (state) {
    DeviceConnectionState.CONNECTED -> when (transport) {
        ConnectionTransport.DIRECT_BLE -> "独立 BLE 连接"
        ConnectionTransport.DIRECT_RFCOMM -> "独立 RFCOMM 连接"
        ConnectionTransport.HOOK_BRIDGE -> "LSPosed 桥接模式"
        ConnectionTransport.NONE -> ""
    }

    DeviceConnectionState.CONNECTING -> "正在建立连接…"
    DeviceConnectionState.DISCONNECTED -> "可直接在 App 内连接，或等待 LSPosed 桥接状态同步"
}


private data class DeviceDetailPreviewState(
    val profile: DeviceProfile,
    val connectionState: DeviceConnectionState,
    val transport: ConnectionTransport,
    val battery: TwsBatteryState?,
    val ancMode: AncMode?,
    val ancDepth: AncDepth?,
    val transLevel: TransparencyLevel?,
    val eqMode: EqPreset?,
    val gameMode: Boolean,
    val lowLatency: Boolean,
)

private fun previewProfile(id: String): DeviceProfile =
    requireNotNull(DeviceCatalog.findById(id)?.profile)

@Composable
private fun DeviceDetailPagePreview(state: DeviceDetailPreviewState) {
    val context = LocalContext.current
    val imageStore = remember { DeviceImageStore(context) }
    HyperRoseTheme(settings = ThemeSettings()) {
        CompositionLocalProvider(
            LocalDeviceImageStore provides imageStore,
        ) {
            DeviceDetailPage(
                address = "00:00:00:00:00:00",
                connectionState = state.connectionState,
                transport = state.transport,
                deviceName = state.profile.displayName,
                deviceProfile = state.profile,
                battery = state.battery,
                ancMode = state.ancMode,
                ancDepth = state.ancDepth,
                transLevel = state.transLevel,
                eqMode = state.eqMode,
                gameMode = state.gameMode,
                lowLatency = state.lowLatency,
                capabilities = state.profile.capabilities,
                onAncModeChange = {},
                onAncDepthChange = {},
                onTransLevelChange = {},
                onEqModeChange = {},
                onGameModeChange = {},
                onLowLatencyChange = {},
                onFindLeft = {},
                onFindRight = {},
                onStopFind = {},
                onRefreshStatus = {},
                onDisconnect = {},
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "i5 - Connected")
@Composable
private fun DeviceDetailPagePreview_I5_Connected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i5"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.DIRECT_BLE,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 85, isCharging = false),
                right = EarBatteryState(level = 72, isCharging = true),
                caseBattery = 90,
            ),
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = AncDepth.MEDIUM,
            transLevel = TransparencyLevel.STANDARD,
            eqMode = EqPreset.CLASSIC,
            gameMode = false,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "i5 - Game Mode + Hook Bridge")
@Composable
private fun DeviceDetailPagePreview_I5_GameMode() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i5"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.HOOK_BRIDGE,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 100, isCharging = false),
                right = EarBatteryState(level = 100, isCharging = false),
                caseBattery = 50,
            ),
            ancMode = AncMode.TRANSPARENT,
            ancDepth = null,
            transLevel = TransparencyLevel.VOCAL,
            eqMode = EqPreset.JAPANESE,
            gameMode = true,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "i5 - Disconnected")
@Composable
private fun DeviceDetailPagePreview_I5_Disconnected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i5"),
            connectionState = DeviceConnectionState.DISCONNECTED,
            transport = ConnectionTransport.NONE,
            battery = null,
            ancMode = null,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "MK2 - Connected")
@Composable
private fun DeviceDetailPagePreview_Mk2_Connected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-budsfeel-mk2"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.HOOK_BRIDGE,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 64, isCharging = false),
                right = EarBatteryState(level = 58, isCharging = false),
                caseBattery = 75,
            ),
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = true,
            lowLatency = true,
        ),
    )
}

@Preview(showBackground = true, name = "MK2 - Disconnected")
@Composable
private fun DeviceDetailPagePreview_Mk2_Disconnected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-budsfeel-mk2"),
            connectionState = DeviceConnectionState.DISCONNECTED,
            transport = ConnectionTransport.NONE,
            battery = null,
            ancMode = null,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "Ceramics U - Connected")
@Composable
private fun DeviceDetailPagePreview_CeramicsU_Connected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-ceramics-u"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.DIRECT_RFCOMM,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 78, isCharging = false),
                right = EarBatteryState(level = 81, isCharging = false),
                caseBattery = 66,
            ),
            ancMode = AncMode.ADAPTIVE_NOISE_CANCEL,
            ancDepth = AncDepth.DEEP,
            transLevel = TransparencyLevel.DEEP,
            eqMode = EqPreset.HIFI,
            gameMode = true,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "Ceramics U - Disconnected")
@Composable
private fun DeviceDetailPagePreview_CeramicsU_Disconnected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-ceramics-u"),
            connectionState = DeviceConnectionState.DISCONNECTED,
            transport = ConnectionTransport.NONE,
            battery = null,
            ancMode = null,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "i7 - Connected")
@Composable
private fun DeviceDetailPagePreview_I7_Connected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i7"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.DIRECT_RFCOMM,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 92, isCharging = false),
                right = EarBatteryState(level = 88, isCharging = false),
                caseBattery = 80,
            ),
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "i7 - Game Mode")
@Composable
private fun DeviceDetailPagePreview_I7_GameMode() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i7"),
            connectionState = DeviceConnectionState.CONNECTED,
            transport = ConnectionTransport.HOOK_BRIDGE,
            battery = TwsBatteryState(
                left = EarBatteryState(level = 70, isCharging = false),
                right = EarBatteryState(level = 68, isCharging = false),
                caseBattery = 55,
            ),
            ancMode = AncMode.WIND_NOISE,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = true,
            lowLatency = false,
        ),
    )
}

@Preview(showBackground = true, name = "i7 - Disconnected")
@Composable
private fun DeviceDetailPagePreview_I7_Disconnected() {
    DeviceDetailPagePreview(
        DeviceDetailPreviewState(
            profile = previewProfile("rose-earfeel-i7"),
            connectionState = DeviceConnectionState.DISCONNECTED,
            transport = ConnectionTransport.NONE,
            battery = null,
            ancMode = null,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
        ),
    )
}
