package com.dohex.hyperrose.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.domain.audio.AncDepth
import com.dohex.hyperrose.domain.audio.AncMode
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.BatteryCard
import com.dohex.hyperrose.ui.component.EqSelector
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.ConnectionTransport
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
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
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
    onEqModeChange: (EqPreset) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onFindLeft: () -> Unit,
    onFindRight: () -> Unit,
    onStopFind: () -> Unit,
    onRefreshStatus: () -> Unit,
    onDisconnect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected = connectionState == DeviceConnectionState.CONNECTED
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    var showFindDialog by remember { mutableStateOf(false) }
    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = deviceName ?: "ROSE EARFREE",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                MiuixIcons.ChevronBackward,
                                contentDescription = "返回"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        }) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    start = 10.dp,
                    end = 10.dp
                )
            ) {
                item {
                    SectionCard(
                        title = deviceName ?: "ROSE EARFREE",
                        subtitle = connectionSummary(
                            connectionState,
                            transport
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ActionButton(
                                text = if (connected) "刷新状态" else "返回列表",
                                onClick = if (connected) onRefreshStatus else onBack,
                                modifier = Modifier.weight(1f)
                            )
                            ActionButton(
                                text = if (connected) "断开" else "返回",
                                onClick = if (connected) onDisconnect else onBack,
                                modifier = Modifier.weight(1f),
                                danger = connected
                            )
                        }
                    }
                }

                if (!connected) {
                    item {
                        SectionCard(
                            title = "未连接耳机",
                            subtitle = "可直接在 App 内连接，或等待 LSPosed 桥接状态同步",
                        ) {
                            Text(
                                text = "连接后可控制 ANC、EQ、游戏模式，并显示电量通知/超级岛。",
                                color = Color(0xFF5C6775)
                            )
                        }
                    }
                    return@LazyColumn
                }

                item {
                    BatteryCard(
                        battery = battery
                    )
                }

                item {
                    AncSelector(
                        ancMode = ancMode,
                        ancDepth = ancDepth,
                        transLevel = transLevel,
                        onAncModeChange = onAncModeChange,
                        onAncDepthChange = onAncDepthChange,
                        onTransLevelChange = onTransLevelChange,
                        enabled = true,
                    )
                }
                item {
                    EqSelector(
                        eqMode = eqMode,
                        onSelect = onEqModeChange,
                        enabled = true,
                    )
                }
                item {
                    Card {
                        SwitchPreference(
                            title = "游戏模式",
                            checked = gameMode,
                            onCheckedChange = onGameModeChange
                        )
                    }
                }
                item {
                    Card {
                        ArrowPreference(
                            title = "查找耳机",
                            onClick = { showFindDialog = true })
                    }
                }
            }
        }

        OverlayDialog(
            title = "查找耳机",
            summary = "请不要佩戴耳机",
            show = showFindDialog,
            onDismissRequest = { showFindDialog = false }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(
                    text = "左耳",
                    onClick = {
                        onFindLeft()
                        showFindDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
                ActionButton(
                    text = "停止",
                    onClick = {
                        onStopFind()
                        showFindDialog = false
                    },
                    modifier = Modifier.weight(1f),
                    danger = true
                )
                ActionButton(
                    text = "右耳",
                    onClick = {
                        onFindRight()
                        showFindDialog = false
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun connectionSummary(
    connectionState: DeviceConnectionState,
    transport: ConnectionTransport
): String {
    return when (connectionState) {
        DeviceConnectionState.CONNECTING -> "连接中"
        DeviceConnectionState.DISCONNECTED -> "未连接"
        DeviceConnectionState.CONNECTED -> when (transport) {
            ConnectionTransport.DIRECT_BLE -> "已连接 · 独立 BLE"
            ConnectionTransport.HOOK_BRIDGE -> "已连接 · LSPosed 桥接"
            ConnectionTransport.NONE -> "已连接"
        }
    }
}
