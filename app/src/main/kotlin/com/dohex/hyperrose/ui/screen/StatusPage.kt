package com.dohex.hyperrose.ui.screen

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.BuildConfig
import com.dohex.hyperrose.R
import com.dohex.hyperrose.ipc.HookStatus
import com.dohex.hyperrose.ipc.HookStatusProvider
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import com.dohex.hyperrose.ui.theme.miuixHomeStatusCardColorActivated
import com.dohex.hyperrose.ui.theme.miuixHomeStatusCardColorDeactivated
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Months
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme.isDynamicColor
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/** 模块总览：Hook、蓝牙、已配对设备和运行环境信息。 */
@Composable
fun StatusPage(
    hookStatus: HookStatus? = null,
    bluetoothEnabled: Boolean = false,
    bondedDeviceCount: Int = 0,
    onBluetoothStatusClick: () -> Unit = {},
    onPairedDeviceClick: () -> Unit = {},
    onOpenDebug: (() -> Unit)? = null,
    onOpenRestart: (() -> Unit)? = null,
    outerPadding: PaddingValues = PaddingValues(),
    modifier: Modifier = Modifier,
) {

    val themeSettings = LocalThemeSettings.current
    val backdrop = rememberBlurBackdrop(themeSettings.blurEnabled)
    val blurActive = themeSettings.blurEnabled && backdrop != null
    val providerStatus by HookStatusProvider.status.collectAsState()
    val status = hookStatus ?: providerStatus

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = stringResource(R.string.app_name),
                    largeTitle = stringResource(R.string.app_name),
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                    actions = {
                        onOpenDebug?.let { onClick ->
                            IconButton(onClick = onClick) {
                                Icon(MiuixIcons.Months, contentDescription = stringResource(R.string.module_debug))
                            }
                        }
                        onOpenRestart?.let { onClick ->
                            IconButton(onClick = onClick) {
                                Icon(MiuixIcons.Refresh, contentDescription = stringResource(R.string.restart_scope))
                            }
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
            contentPadding = TopLevelPageDefaults.contentPadding(
                scaffoldPadding = paddingValues,
                outerPadding = outerPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(TopLevelPageDefaults.ItemSpacing),
        ) {
            item {
                StatusGrid(
                    hookStatus = status,
                    bluetoothEnabled = bluetoothEnabled,
                    bondedDeviceCount = bondedDeviceCount,
                    onBluetoothStatusClick = onBluetoothStatusClick,
                    onPairedDeviceClick = onPairedDeviceClick,
                )
            }
            item { InfoCard(status) }
        }
    }
}

@Composable
private fun StatusGrid(
    hookStatus: HookStatus,
    bluetoothEnabled: Boolean,
    bondedDeviceCount: Int,
    onBluetoothStatusClick: () -> Unit,
    onPairedDeviceClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModuleStatusCard(
                    hookStatus = hookStatus,
                    modifier = Modifier.weight(1f).height(112.dp),
                )
                StatCard(
                    title = stringResource(R.string.bluetooth_status),
                    value = stringResource(
                        if (bluetoothEnabled) R.string.bluetooth_enabled else R.string.bluetooth_disabled,
                    ),
                    onClick = onBluetoothStatusClick,
                    modifier = Modifier.weight(1f).height(112.dp),
                )
                StatCard(
                    title = stringResource(R.string.paired_devices),
                    value = bondedDeviceCount.toString(),
                    onClick = onPairedDeviceClick,
                    modifier = Modifier.weight(1f).height(112.dp),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModuleStatusCard(
                    hookStatus = hookStatus,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                )
                Column(
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = stringResource(R.string.bluetooth_status),
                        value = stringResource(
                            if (bluetoothEnabled) R.string.bluetooth_enabled else R.string.bluetooth_disabled,
                        ),
                        onClick = onBluetoothStatusClick,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = stringResource(R.string.paired_devices),
                        value = bondedDeviceCount.toString(),
                        onClick = onPairedDeviceClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleStatusCard(
    hookStatus: HookStatus,
    modifier: Modifier = Modifier,
) {
    val (title, subtitle) = hookStatusUi(hookStatus)
    val isActive = hookStatus != HookStatus.Inactive
    val isDark = when (MiuixTheme.colorSchemeMode) {
        ColorSchemeMode.Dark, ColorSchemeMode.MonetDark -> true
        ColorSchemeMode.Light, ColorSchemeMode.MonetLight -> false
        else -> isSystemInDarkTheme()
    }

    val containerColor = if (isActive) {
        when {
            isDynamicColor -> MiuixTheme.colorScheme.secondaryContainer
            isDark -> Color(0xFF1A3825)
            else -> Color(0xFFDFFAE4)
        }
    } else {
        when {
            isDynamicColor -> MiuixTheme.colorScheme.errorContainer
            isDark -> Color(0xFF381A1A)
            else -> Color(0xFFFAEEEE)
        }
    }

    val textContentColor = if (isActive) {
        if (isDynamicColor) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface
    } else {
        if (isDynamicColor) MiuixTheme.colorScheme.onErrorContainer else MiuixTheme.colorScheme.onSurface
    }
    val descTextColor = textContentColor.copy(alpha = 0.8f)
    Card(
        modifier = modifier,
        colors = CardDefaults.defaultColors(color = containerColor),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = if (isActive) Icons.Rounded.CheckCircleOutline else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (isActive) {
                    if (isDynamicColor) MiuixTheme.colorScheme.primary.copy(alpha = 0.8f) else miuixHomeStatusCardColorActivated
                } else {
                    if (isDynamicColor) MiuixTheme.colorScheme.error.copy(alpha = 0.8f) else miuixHomeStatusCardColorDeactivated
                },
                modifier = Modifier
                    .size(170.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 50.dp, y = 38.dp),
            )
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textContentColor,
                )
                Text(
                    text = subtitle,
                    color = descTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun InfoCard(hookStatus: HookStatus) {
    Card {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoText(stringResource(R.string.module_status), hookStatusUi(hookStatus).first)
            InfoText(stringResource(R.string.module_version), BuildConfig.VERSION_NAME)
            InfoText(
                stringResource(R.string.android_version),
                "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            )
            InfoText(stringResource(R.string.kernel_version), System.getProperty("os.version") ?: stringResource(R.string.unknown_value))
            InfoText(stringResource(R.string.device_model), Build.MODEL.ifBlank { stringResource(R.string.unknown_value) }, bottomPadding = 0.dp)
        }
    }
}

@Composable
private fun InfoText(
    title: String,
    content: String,
    bottomPadding: androidx.compose.ui.unit.Dp = 20.dp,
) {
    Text(
        text = title,
        fontSize = MiuixTheme.textStyles.headline1.fontSize,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = content,
        fontSize = MiuixTheme.textStyles.body2.fontSize,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}
@Composable
private fun hookStatusUi(hookStatus: HookStatus): Pair<String, String> = when (hookStatus) {
    HookStatus.Active -> Pair(
        stringResource(R.string.module_active),
        stringResource(R.string.module_active_summary),
    )
    HookStatus.ScopePending -> Pair(
        stringResource(R.string.module_scope_pending),
        stringResource(R.string.module_scope_pending_summary),
    )
    HookStatus.Inactive -> Pair(
        stringResource(R.string.module_inactive),
        stringResource(R.string.module_inactive_summary),
    )
}

@Preview(showBackground = true, name = "Module - Active")
@Composable
private fun StatusPagePreview_Active() {
    HyperRoseTheme(settings = ThemeSettings()) {
        StatusPage(hookStatus = HookStatus.Active, bluetoothEnabled = true, bondedDeviceCount = 3)
    }
}

@Preview(showBackground = true, name = "Module - ScopePending")
@Composable
private fun StatusPagePreview_ScopePending() {
    HyperRoseTheme(settings = ThemeSettings()) {
        StatusPage(hookStatus = HookStatus.ScopePending)
    }
}

@Preview(showBackground = true, name = "Module - Inactive")
@Composable
private fun StatusPagePreview_Inactive() {
    HyperRoseTheme(settings = ThemeSettings()) {
        StatusPage(hookStatus = HookStatus.Inactive)
    }
}
