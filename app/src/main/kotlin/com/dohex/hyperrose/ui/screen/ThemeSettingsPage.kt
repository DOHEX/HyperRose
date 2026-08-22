package com.dohex.hyperrose.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.model.ColorMode
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

private val ThemeColorModeOptions =
    listOf(
        "跟随系统",
        "浅色",
        "深色",
    )

/** 主题设置二级页：颜色模式、Monet、底栏和基础模糊。 */
@Composable
fun ThemeSettingsPage(
    onBack: () -> Unit,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = LocalThemeSettings.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurSupported = isRenderEffectSupported()
    val backdrop = rememberBlurBackdrop(settings.blurEnabled)
    val blurActive = settings.blurEnabled && backdrop != null

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "主题设置",
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surface,
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier)
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                top = paddingValues.calculateTopPadding(),
                start = 8.dp,
                end = 8.dp,
                bottom = paddingValues.calculateBottomPadding(),
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                SmallTitle(text = "外观")
                Card {
                    OverlayDropdownPreference(
                        title = "颜色模式",
                        items = ThemeColorModeOptions,
                        selectedIndex = settings.colorMode.ordinal,
                        onSelectedIndexChange = { index ->
                            ColorMode.entries.getOrNull(index)?.let { mode ->
                                onUpdateThemeSettings { current ->
                                    current.copy(colorMode = mode)
                                }
                            }
                        },
                        enabled = true,
                    )
                    SwitchPreference(
                        title = "Monet 动态颜色",
                        summary = "使用系统动态颜色生成主题",
                        checked = settings.monetEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateThemeSettings { current ->
                                current.copy(monetEnabled = enabled)
                            }
                        },
                    )
                    SwitchPreference(
                        title = "悬浮底栏",
                        summary = "使用悬浮玻璃底栏替代标准导航栏",
                        checked = settings.floatingBottomBarEnabled,
                        onCheckedChange = { enabled ->
                            onUpdateThemeSettings { current ->
                                current.copy(floatingBottomBarEnabled = enabled)
                            }
                        },
                    )
                    if (blurSupported) {
                        SwitchPreference(
                            title = "基础模糊",
                            summary = "控制页面顶栏和标准底栏的模糊效果",
                            checked = settings.blurEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateThemeSettings { current ->
                                    current.copy(blurEnabled = enabled)
                                }
                            },
                        )
                        SwitchPreference(
                            title = "液态玻璃",
                            summary = when {
                                !settings.floatingBottomBarEnabled -> "需先开启悬浮底栏"
                                !settings.blurEnabled -> "需开启基础模糊"
                                else -> "悬浮底栏使用折射与色散的玻璃效果"
                            },
                            checked = settings.liquidGlassEnabled,
                            enabled = settings.floatingBottomBarEnabled && settings.blurEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateThemeSettings { current ->
                                    current.copy(liquidGlassEnabled = enabled)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, name = "Theme Settings - Default")
@Composable
private fun ThemeSettingsPagePreview_Default() {
    HyperRoseTheme(settings = ThemeSettings()) {
        ThemeSettingsPage(
            onBack = {},
            onUpdateThemeSettings = {},
        )
    }
}
