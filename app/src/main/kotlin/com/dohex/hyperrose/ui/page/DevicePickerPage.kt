package com.dohex.hyperrose.ui.page

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.RoseDeviceItem
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DevicePickerPage(
    hasPermission: Boolean,
    devices: List<RoseDeviceItem>,
    connectionState: DeviceConnectionState,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) return@LaunchedEffect
        onRefresh()
        delay(500)
        isRefreshing = false
    }

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "设备列表",
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(MiuixIcons.Settings, contentDescription = "设置")
                        }
                    },
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }

        },
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing, onRefresh = {
                if (hasPermission && !isRefreshing) {
                    isRefreshing = true
                }
            }, pullToRefreshState = pullToRefreshState, modifier = modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            refreshTexts = listOf("下拉刷新设备", "释放刷新设备", "正在刷新设备", "刷新完成"),
            contentPadding = paddingValues
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(), start = 10.dp, end = 10.dp,
                    bottom = 16.dp
                ),
            ) {
                if (!hasPermission) {
                    item {
                        SectionCard(
                            title = "需要蓝牙权限", subtitle = "请授予连接和扫描权限后再选择设备"
                        ) {
                            ActionButton(
                                text = "授予权限", onClick = onRequestPermission,
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
                            Text(
                                text = "可下拉刷新设备列表", color = Color(0xFF5B6776),
                                modifier = Modifier.padding(top = 6.dp)
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
                            text = "连接", onClick = { onConnect(device.address) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = connectionState != DeviceConnectionState.CONNECTING
                        )
                    }
                }
                item {
                    Text(
                        text = "下拉可刷新设备状态", color = Color(0xFF5B6776),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

private fun stateText(state: DeviceConnectionState): String = when (state) {
    DeviceConnectionState.CONNECTED -> "已连接"
    DeviceConnectionState.CONNECTING -> "连接中"
    DeviceConnectionState.DISCONNECTED -> "未连接"
}
