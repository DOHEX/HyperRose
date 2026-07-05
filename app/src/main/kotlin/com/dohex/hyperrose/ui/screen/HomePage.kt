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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.R
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.BatteryCard
import com.dohex.hyperrose.ui.component.EqSelector
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.ConnectionTransport
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.ThemeMode
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
fun HomePage(
    connectionState: DeviceConnectionState,
    transport: ConnectionTransport,
    deviceName: String?,
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
    onOpenBleDebug: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = connectionState == DeviceConnectionState.CONNECTED
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    val listState = rememberLazyListState()
    var showFindDialog by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = deviceName
                        ?: com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.displayName,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenBleDebug) {
                            Icon(MiuixIcons.Info, contentDescription = "BLE 调试")
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
                item {
                    Image(
                        painter = painterResource(R.drawable.earphone_blue_case),
                        contentDescription = "ROSESELSA EARFREE i5",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .graphicsLayer {
                                if (listState.firstVisibleItemIndex == 0) {
                                    val scrollOffset =
                                        listState.firstVisibleItemScrollOffset.toFloat()
                                    translationY = scrollOffset * 0.5f
                                    alpha = 1f - (scrollOffset / 600f).coerceIn(0f, 1f)
                                }
                            })
                }

                item {
                    SectionCard(
                        title = deviceName
                            ?: com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.displayName,
                        subtitle = connectionSummary(connectionState, transport),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                text = if (connected) "刷新状态" else "返回列表",
                                onClick = if (connected) onRefreshStatus else onBack,
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                text = if (connected) "断开" else "返回",
                                onClick = if (connected) onDisconnect else onBack,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                if (!connected) {
                    item {
                        SectionCard(
                            title = "未连接耳机",
                            subtitle = "可直接在 App 内连接，或等待 LSPosed 桥接状态同步",
                        ) {}
                    }
                    return@LazyColumn
                }

                item { BatteryCard(battery = battery) }

                item {
                    AncSelector(
                        ancMode = ancMode,
                        ancDepth = ancDepth,
                        transLevel = transLevel,
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

private fun connectionSummary(
    connectionState: DeviceConnectionState,
    transport: ConnectionTransport,
): String = when (connectionState) {
    DeviceConnectionState.CONNECTING -> "连接中"

    DeviceConnectionState.DISCONNECTED -> "未连接"

    DeviceConnectionState.CONNECTED -> when (transport) {
        ConnectionTransport.DIRECT_BLE -> "已连接 · 独立 BLE"
        ConnectionTransport.HOOK_BRIDGE -> "已连接 · LSPosed 桥接"
        ConnectionTransport.NONE -> "已连接"
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePagePreview_Connected() {
    HyperRoseTheme {
        CompositionLocalProvider(LocalThemeMode provides ThemeMode()) {
            HomePage(
                connectionState = DeviceConnectionState.CONNECTED,
                transport = ConnectionTransport.DIRECT_BLE,
                deviceName = "ROSESELSA EARFREE i5",
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
                capabilities = com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities,
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

@Preview(showBackground = true)
@Composable
private fun HomePagePreview_ConnectedGameMode() {
    HyperRoseTheme {
        CompositionLocalProvider(LocalThemeMode provides ThemeMode()) {
            HomePage(
                connectionState = DeviceConnectionState.CONNECTED,
                transport = ConnectionTransport.HOOK_BRIDGE,
                deviceName = "ROSESELSA EARFREE i5",
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
                capabilities = com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities,
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

@Preview(showBackground = true)
@Composable
private fun HomePagePreview_Disconnected() {
    HyperRoseTheme {
        CompositionLocalProvider(LocalThemeMode provides ThemeMode()) {
            HomePage(
                connectionState = DeviceConnectionState.DISCONNECTED,
                transport = ConnectionTransport.NONE,
                deviceName = "ROSESELSA EARFREE i5",
                battery = null,
                ancMode = null,
                ancDepth = null,
                transLevel = null,
                eqMode = null,
                gameMode = false,
                lowLatency = false,
                capabilities = com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.capabilities,
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
