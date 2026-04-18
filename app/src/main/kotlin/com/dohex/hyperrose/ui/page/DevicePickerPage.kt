package com.dohex.hyperrose.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.ui.RoseDevice
import com.dohex.hyperrose.ui.UiConnectionState
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings

@Composable
fun DevicePickerPage(
    hasPermission: Boolean,
    devices: List<RoseDevice>,
    connectionState: UiConnectionState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null
    val topAppBarScrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "设备", subtitle = "ROSE EARFREE", actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(MiuixIcons.Settings, contentDescription = "设置")
                        }
                    },
                    scrollBehavior = topAppBarScrollBehavior,
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }

        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onRefresh
            ) {
                Icon(MiuixIcons.Refresh, contentDescription = "刷新", tint = Color.White)
            }
        },
    ) { paddingValues ->
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .nestedScroll(topAppBarScrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(), start = 10.dp, end = 10.dp
            ),
        ) {
            if (!hasPermission) {
                item {
                    SectionCard(
                        title = "需要蓝牙权限", subtitle = "请授予连接和扫描权限后再选择设备"
                    ) {
                        ActionButton(
                            text = "授予权限",
                            onClick = onRequestPermission,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                return@LazyColumn
            }

            if (devices.isEmpty()) {
                item {
                    SectionCard(
                        title = "未发现可用设备", subtitle = "请先在系统蓝牙里完成耳机配对"
                    ) {
                        Text(
                            text = "连接状态：${stateText(connectionState)}",
                            color = Color(0xFF5B6776)
                        )
                    }
                }
                return@LazyColumn
            }

            items(devices, key = { it.address }) { device ->
                SectionCard(
                    title = device.name, subtitle = device.address
                ) {
                    ActionButton(
                        text = "连接",
                        onClick = { onConnect(device.address) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = connectionState != UiConnectionState.CONNECTING
                    )
                }
            }
        }
    }
}

private fun stateText(state: UiConnectionState): String = when (state) {
    UiConnectionState.CONNECTED -> "已连接"
    UiConnectionState.CONNECTING -> "连接中"
    UiConnectionState.DISCONNECTED -> "未连接"
}
