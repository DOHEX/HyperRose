package com.dohex.hyperrose.ui.screen

import android.content.Intent
import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.ui.AppEntryActivity
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.BatteryCard
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/** 快速控制弹窗内容（WindowDialog）。 */
@Composable
fun PopupControlPanel(
    modifier: Modifier = Modifier,
    deviceControlStore: DeviceControlStore,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinish: () -> Unit,

    ) {
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val deviceName by deviceControlStore.deviceName.collectAsState()
    val battery by deviceControlStore.battery.collectAsState()
    val ancMode by deviceControlStore.ancMode.collectAsState()
    val ancDepth by deviceControlStore.ancDepth.collectAsState()
    val transLevel by deviceControlStore.transLevel.collectAsState()
    val eqMode by deviceControlStore.eqMode.collectAsState()
    val gameMode by deviceControlStore.gameMode.collectAsState()
    val lowLatency by deviceControlStore.lowLatency.collectAsState()
    val capabilities by deviceControlStore.capabilities.collectAsState()
    val connected = connectionState == DeviceConnectionState.CONNECTED

    WindowDialog(
        show = show,
        title = deviceName
            ?: com.dohex.hyperrose.profile.DeviceProfileRegistry.defaultProfile.displayName,
        summary = if (connected) null else "未连接",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinish,
    ) {
        val isLandscape =
            LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape && connected) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AncSelector(
                        ancMode = ancMode,
                        ancDepth = ancDepth,
                        transLevel = transLevel,
                        onAncModeChange = deviceControlStore::setAnc,
                        onAncDepthChange = deviceControlStore::setAncDepth,
                        onTransLevelChange = deviceControlStore::setTransLevel,
                        enabled = true,
                        showAncDepth = capabilities.supportedAncDepths.isNotEmpty(),
                        showTransLevel = capabilities.supportedTransLevels.isNotEmpty(),
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (capabilities.supportedEqPresets.isNotEmpty()) {
                        val eqOptions = capabilities.supportedEqPresets.toList()
                        val eqItems = eqOptions.map { it.label }
                        val eqSelectedIndex = eqOptions.indexOf(eqMode).coerceAtLeast(0)
                        Card {
                            WindowDropdownPreference(
                                title = "音色",
                                items = eqItems,
                                selectedIndex = eqSelectedIndex,
                                onSelectedIndexChange = { index ->
                                    eqOptions.getOrNull(index)
                                        ?.let(deviceControlStore::setEq)
                                },
                            )
                        }
                    }
                    if (capabilities.hasGameMode) {
                        Card {
                            SwitchPreference(
                                title = "游戏模式",
                                checked = gameMode,
                                onCheckedChange = deviceControlStore::setGameMode,
                            )
                        }
                    }
                    if (capabilities.hasLowLatency) {
                        Card {
                            SwitchPreference(
                                title = "低延迟",
                                checked = lowLatency,
                                onCheckedChange = deviceControlStore::setLowLatency,
                            )
                        }
                    }
                }
            }
        } else {
            val context = LocalContext.current
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (connected) {
                    BatteryCard(battery = battery)
                    AncSelector(
                        ancMode = ancMode,
                        ancDepth = ancDepth,
                        transLevel = transLevel,
                        onAncModeChange = deviceControlStore::setAnc,
                        onAncDepthChange = deviceControlStore::setAncDepth,
                        onTransLevelChange = deviceControlStore::setTransLevel,
                        enabled = true,
                        showAncDepth = capabilities.supportedAncDepths.isNotEmpty(),
                        showTransLevel = capabilities.supportedTransLevels.isNotEmpty(),
                    )
                    if (capabilities.supportedEqPresets.isNotEmpty()) {
                        val eqOptions = capabilities.supportedEqPresets.toList()
                        val eqItems = eqOptions.map { it.label }
                        val eqSelectedIndex = eqOptions.indexOf(eqMode).coerceAtLeast(0)
                        Card {
                            WindowDropdownPreference(
                                title = "音色",
                                items = eqItems,
                                selectedIndex = eqSelectedIndex,
                                onSelectedIndexChange = { index ->
                                    eqOptions.getOrNull(index)
                                        ?.let(deviceControlStore::setEq)
                                },
                            )
                        }
                    }
                    if (capabilities.hasGameMode) {
                        Card {
                            SwitchPreference(
                                title = "游戏模式",
                                checked = gameMode,
                                onCheckedChange = deviceControlStore::setGameMode,
                            )
                        }
                    }
                    if (capabilities.hasLowLatency) {
                        Card {
                            SwitchPreference(
                                title = "低延迟",
                                checked = lowLatency,
                                onCheckedChange = deviceControlStore::setLowLatency,
                            )
                        }
                    }
                    ActionButton(
                        text = "更多",
                        onClick = {
                            context.startActivity(
                                Intent(context, AppEntryActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    SectionCard(
                        title = "耳机未连接",
                        subtitle = "请先在 App 主页或系统蓝牙中连接耳机"
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ActionButton(
                                text = "刷新状态",
                                onClick = deviceControlStore::refreshStatus,
                                modifier = Modifier.weight(1f),
                            )
                            ActionButton(
                                text = "关闭",
                                onClick = onDismissRequest,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
