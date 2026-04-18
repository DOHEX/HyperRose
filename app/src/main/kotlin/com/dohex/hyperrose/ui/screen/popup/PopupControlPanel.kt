package com.dohex.hyperrose.ui.screen.popup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.AncSelector
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.DeviceControlStore
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 快速控制弹窗内容（WindowDialog）。
 */
@Composable
fun PopupControlPanel(
    deviceControlStore: DeviceControlStore,
    show: Boolean,
    onDismissRequest: () -> Unit,
    onDismissFinished: () -> Unit
) {
    val connectionState by deviceControlStore.connectionState.collectAsState()
    val deviceName by deviceControlStore.deviceName.collectAsState()
    val ancMode by deviceControlStore.ancMode.collectAsState()
    val ancDepth by deviceControlStore.ancDepth.collectAsState()
    val transLevel by deviceControlStore.transLevel.collectAsState()
    val eqMode by deviceControlStore.eqMode.collectAsState()
    val gameMode by deviceControlStore.gameMode.collectAsState()
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)

    val connected = connectionState == DeviceConnectionState.CONNECTED

    WindowDialog(
        show = show,
        title = deviceName ?: "ROSE EARFREE",
        summary = if (connected) null else "未连接",
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (connected) {
                AncSelector(
                    ancMode = ancMode,
                    ancDepth = ancDepth,
                    transLevel = transLevel,
                    onAncModeChange = deviceControlStore::setAnc,
                    onAncDepthChange = deviceControlStore::setAncDepth,
                    onTransLevelChange = deviceControlStore::setTransLevel,
                    enabled = true
                )
                val eqItems = EqPreset.entries.map { it.label }
                val eqSelectedIndex = EqPreset.entries.indexOf(eqMode).coerceAtLeast(0)
                WindowDropdownPreference(
                    title = "音色",
                    items = eqItems,
                    selectedIndex = eqSelectedIndex,
                    onSelectedIndexChange = { index ->
                        EqPreset.entries.getOrNull(index)?.let(deviceControlStore::setEq)
                    }
                )
                Card {
                    SwitchPreference(
                        title = "游戏模式",
                        checked = gameMode,
                        onCheckedChange = deviceControlStore::setGameMode
                    )
                }
            } else {
                SectionCard(
                    title = "耳机未连接", subtitle = "请先在 App 主页或系统蓝牙中连接耳机"
                ) {
                    Text(
                        text = "连接后可在这里直接调节降噪、通透、音色和游戏模式。",
                        color = Color(0xFF5C6775)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ActionButton(
                            text = "刷新状态",
                            onClick = deviceControlStore::refreshStatus,
                            modifier = Modifier.weight(1f)
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
