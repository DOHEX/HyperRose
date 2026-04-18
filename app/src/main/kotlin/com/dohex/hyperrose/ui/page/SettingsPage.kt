package com.dohex.hyperrose.ui.page

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.BuildConfig
import com.dohex.hyperrose.domain.scope.ScopePackagesToRestart
import com.dohex.hyperrose.domain.scope.restartScopePackages
import com.dohex.hyperrose.ui.ControlTransport
import com.dohex.hyperrose.ui.UiConnectionState
import com.dohex.hyperrose.ui.component.RestartScopeDialog
import com.dohex.hyperrose.ui.component.SectionCard
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.ColorModeOptions
import com.dohex.hyperrose.ui.theme.LocalCanUpdateThemeMode
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.LocalUpdateThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsPage(
    connectionState: UiConnectionState,
    transport: ControlTransport,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeMode = LocalThemeMode.current
    val updateThemeMode = LocalUpdateThemeMode.current
    val canUpdateThemeMode = LocalCanUpdateThemeMode.current
    val scrollBehavior = MiuixScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showRestartDialog by remember { mutableStateOf(false) }
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null

    Scaffold(
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "设置",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { showRestartDialog = true }) {
                            Icon(
                                MiuixIcons.Refresh,
                                contentDescription = "重启作用域"
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                start = 8.dp,
                end = 8.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Card {
                    OverlayDropdownPreference(
                        title = "颜色模式",
                        items = ColorModeOptions,
                        selectedIndex = themeMode.colorMode.coerceIn(0, ColorModeOptions.lastIndex),
                        enabled = canUpdateThemeMode,
                        onSelectedIndexChange = { updateThemeMode { state -> state.copy(colorMode = it) } },
                    )
                    if (isRenderEffectSupported()) {
                        SwitchPreference(
                            title = "启用模糊",
                            checked = themeMode.enableBlur,
                            enabled = canUpdateThemeMode,
                            onCheckedChange = { updateThemeMode { state -> state.copy(enableBlur = it) } }
                        )
                    }
                    SwitchPreference(
                        title = "平滑圆角",
                        checked = themeMode.smoothRounding,
                        enabled = canUpdateThemeMode,
                        onCheckedChange = { updateThemeMode { state -> state.copy(smoothRounding = it) } }
                    )
                }
            }
            item {
                SectionCard(
                    title = "模块状态",
                    subtitle = stateText(connectionState, transport)
                ) {
                    Text("LSPosed 作用域：com.android.bluetooth / com.xiaomi.bluetooth / com.android.systemui")
                }

                SectionCard(
                    title = "版本信息",
                    subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                ) {
                    Text("包名：${BuildConfig.APPLICATION_ID}")
                }

                SectionCard(
                    title = "说明",
                    subtitle = "可作为独立 App 使用，也可接管 HyperOS 蓝牙生态"
                ) {
                    Text("若桥接未生效，请检查 LSPosed 是否启用并重启目标进程。")
                }
            }
        }
        RestartScopeDialog(showRestartDialog,
            ScopePackagesToRestart,
            { showRestartDialog = false },
            { showRestartDialog = false },
            {
                showRestartDialog = false
                scope.launch {
                    val results = withContext(Dispatchers.IO) { restartScopePackages() }
                    val successCount = results.count { it.success }
                    val failItems = results.filterNot { it.success }
                    val message = if (failItems.isEmpty()) {
                        "已重启作用域进程（$successCount/${results.size}）"
                    } else {
                        val failedPkgText = failItems.joinToString("、") { it.packageName }
                        "部分失败（$successCount/${results.size}），失败：$failedPkgText"
                    }
                    Toast.makeText(
                        context,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }
}

private fun stateText(state: UiConnectionState, transport: ControlTransport): String {
    if (state == UiConnectionState.DISCONNECTED) return "未连接"
    if (state == UiConnectionState.CONNECTING) return "连接中"

    return when (transport) {
        ControlTransport.DIRECT_BLE -> "已连接 · 独立 BLE"
        ControlTransport.HOOK_BRIDGE -> "已连接 · LSPosed 桥接"
        ControlTransport.NONE -> "已连接"
    }
}
