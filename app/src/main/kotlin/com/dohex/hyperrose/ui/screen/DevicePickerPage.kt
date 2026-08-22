package com.dohex.hyperrose.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.R
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.ui.component.ActionButton
import com.dohex.hyperrose.ui.state.DeviceConnectionState
import com.dohex.hyperrose.ui.state.RoseDeviceItem
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Link
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun DevicePickerPage(
    hasPermission: Boolean,
    devices: List<RoseDeviceItem>,
    connectionState: DeviceConnectionState,
    activeAddress: String? = null,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit = {},
    outerPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.blurEnabled)
    val blurActive = themeSettings.blurEnabled && backdrop != null
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by rememberSaveable { mutableStateOf(false) }
    val requestRefresh = {
        if (hasPermission && !isRefreshing) isRefreshing = true
    }

    LaunchedEffect(isRefreshing) {
        if (!isRefreshing) return@LaunchedEffect
        onRefresh()
        delay(500.milliseconds)
        isRefreshing = false
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = androidx.compose.ui.res.stringResource(R.string.earphones_title),
                    largeTitle = androidx.compose.ui.res.stringResource(R.string.earphones_title),
                    actions = {
                        IconButton(onClick = requestRefresh) {
                            if (isRefreshing) {
                                InfiniteProgressIndicator()
                            } else {
                                Icon(
                                    MiuixIcons.Refresh,
                                    contentDescription = androidx.compose.ui.res.stringResource(R.string.refresh_devices),
                                )
                            }
                        }
                    },
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = requestRefresh,
            pullToRefreshState = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            refreshTexts = listOf(
                androidx.compose.ui.res.stringResource(R.string.pull_refresh_devices),
                androidx.compose.ui.res.stringResource(R.string.release_refresh_devices),
                androidx.compose.ui.res.stringResource(R.string.refreshing_devices),
                androidx.compose.ui.res.stringResource(R.string.refresh_devices_done),
            ),
            contentPadding = paddingValues,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(TopLevelPageDefaults.ItemSpacing),
                contentPadding = TopLevelPageDefaults.contentPadding(
                scaffoldPadding = paddingValues,
                outerPadding = outerPadding,
                ),
            ) {
                if (!hasPermission) {
                    item {
                        PickerStateCard(
                            title = androidx.compose.ui.res.stringResource(R.string.bluetooth_permission_title),
                            subtitle = androidx.compose.ui.res.stringResource(R.string.bluetooth_permission_summary),
                        ) {
                            ActionButton(
                                text = androidx.compose.ui.res.stringResource(R.string.grant_permission),
                                onClick = onRequestPermission,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    return@LazyColumn
                }

                if (devices.isEmpty()) {
                    item {
                        PickerStateCard(
                            title = androidx.compose.ui.res.stringResource(R.string.no_paired_devices_title),
                            subtitle = androidx.compose.ui.res.stringResource(R.string.no_paired_devices_summary),
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(
                                    R.string.bluetooth_state,
                                    androidx.compose.ui.res.stringResource(stateTextRes(connectionState)),
                                ),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    return@LazyColumn
                }

                items(devices, key = { it.address }) { device ->
                    DeviceRow(
                        device = device,
                        connected = device.address.equals(activeAddress, ignoreCase = true) &&
                            connectionState != DeviceConnectionState.DISCONNECTED,
                        connecting = device.address.equals(activeAddress, ignoreCase = true) &&
                            connectionState == DeviceConnectionState.CONNECTING,
                        onClick = { onConnect(device.address) },
                        onDisconnect = onDisconnect,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerStateCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            content()
        }
    }
}

@Composable
private fun DeviceRow(
    device: RoseDeviceItem,
    connected: Boolean,
    connecting: Boolean,
    onClick: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val enabled = device.isSupported && !connecting
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    color = if (connected) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = device.address,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
                when {
                    !device.isSupported -> Text(
                        text = androidx.compose.ui.res.stringResource(R.string.unsupported_device),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )

                    connected -> Text(
                        text = androidx.compose.ui.res.stringResource(
                            if (connecting) R.string.connection_connecting else R.string.connection_connected,
                        ),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            when {
                connecting -> InfiniteProgressIndicator()
                connected -> IconButton(onClick = onDisconnect) {
                    Icon(
                        MiuixIcons.Close,
                        contentDescription = androidx.compose.ui.res.stringResource(R.string.disconnect_device),
                    )
                }

                device.isSupported -> Icon(
                    imageVector = MiuixIcons.Link,
                    contentDescription = androidx.compose.ui.res.stringResource(R.string.connect_device),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

private fun stateTextRes(state: DeviceConnectionState): Int = when (state) {
    DeviceConnectionState.CONNECTED -> R.string.connection_connected
    DeviceConnectionState.CONNECTING -> R.string.connection_connecting
    DeviceConnectionState.DISCONNECTED -> R.string.connection_disconnected
}

@Preview(showBackground = true)
@Composable
private fun DevicePickerPagePreview_NoPermission() {
    HyperRoseTheme(settings = ThemeSettings()) {
        DevicePickerPage(
            hasPermission = false,
            devices = emptyList(),
            connectionState = DeviceConnectionState.DISCONNECTED,
            onRequestPermission = {},
            onRefresh = {},
            onConnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicePickerPagePreview_Devices() {
    val devices = DeviceCatalog.devices.mapIndexed { index, descriptor ->
        RoseDeviceItem(
            name = descriptor.displayName,
            address = "00:11:22:44:55:${60 + index}",
            profileId = descriptor.id,
        )
    } + RoseDeviceItem(
        name = "Redmi Buds 5 Pro",
        address = "AA:BB:CC:DD:EE:FF",
    )

    HyperRoseTheme(settings = ThemeSettings()) {
        DevicePickerPage(
            hasPermission = true,
            devices = devices,
            connectionState = DeviceConnectionState.DISCONNECTED,
            onRequestPermission = {},
            onRefresh = {},
            onConnect = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DevicePickerPagePreview_Connecting() {
    HyperRoseTheme(settings = ThemeSettings()) {
        DevicePickerPage(
            hasPermission = true,
            devices = listOf(
                RoseDeviceItem(name = "EarFeel i5", address = "00:11:22:33:44:55"),
            ),
            connectionState = DeviceConnectionState.CONNECTING,
            activeAddress = "00:11:22:33:44:55",
            onRequestPermission = {},
            onRefresh = {},
            onConnect = {},
        )
    }
}
