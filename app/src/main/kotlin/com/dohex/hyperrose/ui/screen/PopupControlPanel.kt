package com.dohex.hyperrose.ui.screen

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.profile.DeviceCapabilities
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog

private data class PopupControlPanelState(
    val connectionState: DeviceConnectionState,
    val deviceName: String?,
    val ancMode: AncMode?,
    val ancDepth: AncDepth?,
    val transLevel: TransparencyLevel?,
    val eqMode: EqPreset?,
    val gameMode: Boolean,
    val lowLatency: Boolean,
    val capabilities: DeviceCapabilities,
    val profile: DeviceProfile?,
) {
    val connected: Boolean get() = connectionState == DeviceConnectionState.CONNECTED
}

/** 快速控制弹窗内容（WindowDialog）。 */
@Composable
fun PopupControlPanel(
    modifier: Modifier = Modifier,
    deviceControlStore: DeviceControlStore,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinish: () -> Unit,
) {
    val state = PopupControlPanelState(
        connectionState = deviceControlStore.connectionState.collectAsState().value,
        deviceName = deviceControlStore.deviceName.collectAsState().value,
        ancMode = deviceControlStore.ancMode.collectAsState().value,
        ancDepth = deviceControlStore.ancDepth.collectAsState().value,
        transLevel = deviceControlStore.transLevel.collectAsState().value,
        eqMode = deviceControlStore.eqMode.collectAsState().value,
        gameMode = deviceControlStore.gameMode.collectAsState().value,
        lowLatency = deviceControlStore.lowLatency.collectAsState().value,
        capabilities = deviceControlStore.capabilities.collectAsState().value,
        profile = deviceControlStore.profile.collectAsState().value,
    )

    PopupControlPanelContent(
        modifier = modifier,
        state = state,
        show = show,
        onDismissRequest = onDismissRequest,
        onDismissFinish = onDismissFinish,
        onAncModeChange = deviceControlStore::setAnc,
        onAncDepthChange = deviceControlStore::setAncDepth,
        onTransLevelChange = deviceControlStore::setTransLevel,
        onEqModeChange = deviceControlStore::setEq,
        onGameModeChange = deviceControlStore::setGameMode,
        onLowLatencyChange = deviceControlStore::setLowLatency,
        onRefreshStatus = deviceControlStore::refreshStatus,
    )
}

@Composable
private fun PopupControlPanelContent(
    modifier: Modifier,
    state: PopupControlPanelState,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinish: () -> Unit,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
    onEqModeChange: (EqPreset) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onLowLatencyChange: (Boolean) -> Unit,
    onRefreshStatus: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = state.deviceName ?: "HyperRose",
        summary = if (state.connected) null else "未连接",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinish,
    ) {
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape && state.connected && state.profile != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PopupAncSelector(state, onAncModeChange, onAncDepthChange, onTransLevelChange)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PopupFeatureControls(
                        state = state,
                        onEqModeChange = onEqModeChange,
                        onGameModeChange = onGameModeChange,
                        onLowLatencyChange = onLowLatencyChange,
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(modifier)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (state.connected && state.profile != null) {
                    PopupAncSelector(state, onAncModeChange, onAncDepthChange, onTransLevelChange)
                    PopupFeatureControls(
                        state = state,
                        onEqModeChange = onEqModeChange,
                        onGameModeChange = onGameModeChange,
                        onLowLatencyChange = onLowLatencyChange,
                    )
                } else {
                    SectionCard(
                        title = "耳机未连接",
                        subtitle = "请先在 App 主页或系统蓝牙中连接耳机",
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                text = "刷新状态",
                                onClick = onRefreshStatus,
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                text = "关闭",
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PopupAncSelector(
    state: PopupControlPanelState,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
) {
    AncSelector(
        ancMode = state.ancMode,
        ancDepth = state.ancDepth,
        transLevel = state.transLevel,
        supportedAncModes = state.capabilities.supportedAncModes,
        supportedAncDepths = state.capabilities.supportedAncDepths,
        supportedTransLevels = state.capabilities.supportedTransLevels,
        onAncModeChange = onAncModeChange,
        onAncDepthChange = onAncDepthChange,
        onTransLevelChange = onTransLevelChange,
        enabled = true,
        showAncDepth = state.capabilities.supportedAncDepths.isNotEmpty(),
        showTransLevel = state.capabilities.supportedTransLevels.isNotEmpty(),
    )
}

@Composable
private fun PopupFeatureControls(
    state: PopupControlPanelState,
    onEqModeChange: (EqPreset) -> Unit,
    onGameModeChange: (Boolean) -> Unit,
    onLowLatencyChange: (Boolean) -> Unit,
) {
    if (state.capabilities.supportedEqPresets.isNotEmpty()) {
        val presets = EqPreset.entries.filter { it in state.capabilities.supportedEqPresets }
        Card {
            WindowDropdownPreference(
                title = "音色",
                items = presets.map { it.label },
                selectedIndex = presets.indexOf(state.eqMode).coerceAtLeast(0),
                onSelectedIndexChange = { index ->
                    presets.getOrNull(index)?.let(onEqModeChange)
                },
            )
        }
    }
    if (state.capabilities.hasGameMode) {
        Card {
            SwitchPreference(
                title = "游戏模式",
                checked = state.gameMode,
                onCheckedChange = onGameModeChange,
            )
        }
    }
    if (state.capabilities.hasLowLatency) {
        Card {
            SwitchPreference(
                title = "低延迟",
                checked = state.lowLatency,
                onCheckedChange = onLowLatencyChange,
            )
        }
    }
}

@Preview(showBackground = true, name = "Popup - i7")
@Composable
private fun PopupControlPanelPreview_I7() {
    val profile = requireNotNull(DeviceCatalog.findById("rose-earfeel-i7")?.profile)
    PopupControlPanelContent(
        modifier = Modifier,
        state = PopupControlPanelState(
            connectionState = DeviceConnectionState.CONNECTED,
            deviceName = profile.displayName,
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = true,
            lowLatency = false,
            capabilities = profile.capabilities,
            profile = profile,
        ),
        show = true,
        onDismissRequest = {},
        onDismissFinish = {},
        onAncModeChange = {},
        onAncDepthChange = {},
        onTransLevelChange = {},
        onEqModeChange = {},
        onGameModeChange = {},
        onLowLatencyChange = {},
        onRefreshStatus = {},
    )
}

@Preview(showBackground = true, widthDp = 840, heightDp = 480, name = "Popup - i5 Landscape")
@Composable
private fun PopupControlPanelPreview_I5() {
    val profile = requireNotNull(DeviceCatalog.findById("rose-earfeel-i5")?.profile)
    PopupControlPanelContent(
        modifier = Modifier,
        state = PopupControlPanelState(
            connectionState = DeviceConnectionState.CONNECTED,
            deviceName = profile.displayName,
            ancMode = AncMode.TRANSPARENT,
            ancDepth = AncDepth.MEDIUM,
            transLevel = TransparencyLevel.VOCAL,
            eqMode = EqPreset.CLASSIC,
            gameMode = false,
            lowLatency = false,
            capabilities = profile.capabilities,
            profile = profile,
        ),
        show = true,
        onDismissRequest = {},
        onDismissFinish = {},
        onAncModeChange = {},
        onAncDepthChange = {},
        onTransLevelChange = {},
        onEqModeChange = {},
        onGameModeChange = {},
        onLowLatencyChange = {},
        onRefreshStatus = {},
    )
}

@Preview(showBackground = true, name = "Popup - Disconnected")
@Composable
private fun PopupControlPanelPreview_Disconnected() {
    PopupControlPanelContent(
        modifier = Modifier,
        state = PopupControlPanelState(
            connectionState = DeviceConnectionState.DISCONNECTED,
            deviceName = null,
            ancMode = null,
            ancDepth = null,
            transLevel = null,
            eqMode = null,
            gameMode = false,
            lowLatency = false,
            capabilities = DeviceCapabilities.NONE,
            profile = null,
        ),
        show = true,
        onDismissRequest = {},
        onDismissFinish = {},
        onAncModeChange = {},
        onAncDepthChange = {},
        onTransLevelChange = {},
        onEqModeChange = {},
        onGameModeChange = {},
        onLowLatencyChange = {},
        onRefreshStatus = {},
    )
}
