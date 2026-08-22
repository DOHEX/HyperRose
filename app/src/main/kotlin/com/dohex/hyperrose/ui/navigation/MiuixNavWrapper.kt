// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 HyperRose contributors
//
// Miuix 导航壳：布局分流（紧凑/宽屏）、悬浮底栏模式矩阵与双 Backdrop 采样层隔离。
// 参照 InstallerX Revived 的 MiuixNavWrapper 结构。
package com.dohex.hyperrose.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.navigationBars
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.theme.LocalThemeSettings
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 底部导航 tab 数据。 */
data class NavigationTab(
    val destination: TopLevelTab,
    val label: String,
    val icon: ImageVector,
)

/** Miuix 主页面壳：根据主题设置分流紧凑/宽屏布局并装配悬浮底栏。 */
@Composable
fun MiuixNavWrapper(
    pagerState: PagerState,
    mainPagerState: MainPagerState,
    deviceControlStore: DeviceControlStore,
    onTabChanged: (TopLevelTab) -> Unit,
    onUpdateThemeSettings: ((ThemeSettings) -> ThemeSettings) -> Unit,
    pendingRestart: State<Boolean>,
    onConsumeRestart: () -> Unit,
    onRequestRestart: () -> Unit,
    tabs: List<NavigationTab>,
    modifier: Modifier = Modifier,
) {
    val themeSettings = LocalThemeSettings.current
    val useFloatingBottomBar = themeSettings.floatingBottomBarEnabled
    val floatingBottomBarMode = floatingBottomBarMode(
        blurEnabled = themeSettings.blurEnabled,
        liquidGlassEnabled = themeSettings.liquidGlassEnabled,
    )
    val selectedTab =
        tabs.getOrNull(mainPagerState.selectedPage)?.destination ?: tabs.first().destination

    // 分离不同采样层：悬浮底栏自身 vs 普通顶栏/标准底栏，避免自采样与重复模糊。
    val floatingBackdrop = if (useFloatingBottomBar) rememberLayerBackdrop() else null
    val miuixBackdrop = rememberBlurBackdrop(themeSettings.blurEnabled)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 600.dp

        if (useFloatingBottomBar) {
            Box(modifier = Modifier.fillMaxSize()) {
                MainPager(
                    pagerState = pagerState,
                    deviceControlStore = deviceControlStore,
                    onTabChanged = onTabChanged,
                    onUpdateThemeSettings = onUpdateThemeSettings,
                    pendingRestart = pendingRestart,
                    onConsumeRestart = onConsumeRestart,
                    onRequestRestart = onRequestRestart,
                    outerPadding = PaddingValues(bottom = FloatingBottomBarDefaults.ContentBottomPadding),
                    backdrop = floatingBackdrop,
                    modifier = Modifier.fillMaxSize(),
                )
                FloatingBottomBar(
                    items = tabs,
                    selectedIndex = { mainPagerState.selectedPage },
                    onSelected = { index -> mainPagerState.animateToPage(index) },
                    backdrop = floatingBackdrop ?: rememberLayerBackdrop(),
                    mode = if (floatingBackdrop != null) floatingBottomBarMode else FloatingBottomBarMode.None,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .windowInsetsPadding(
                            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                        ),
                    iconContent = { tab, _ ->
                        Icon(imageVector = tab.icon, contentDescription = null)
                    },
                    labelContent = { tab, _ ->
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                )
            }
        } else if (isWideLayout) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    color = MiuixTheme.colorScheme.surface,
                    showDivider = false,
                ) {
                    tabs.forEach { tab ->
                        NavigationRailItem(
                            selected = selectedTab == tab.destination,
                            onClick = { onTabChanged(tab.destination) },
                            icon = tab.icon,
                            label = tab.label,
                        )
                    }
                }
                MainPager(
                    pagerState = pagerState,
                    deviceControlStore = deviceControlStore,
                    onTabChanged = onTabChanged,
                    onUpdateThemeSettings = onUpdateThemeSettings,
                    pendingRestart = pendingRestart,
                    onConsumeRestart = onConsumeRestart,
                    onRequestRestart = onRequestRestart,
                    outerPadding = PaddingValues(),
                    backdrop = miuixBackdrop,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                bottomBar = {
                    StandardBottomBar(
                        selectedTab = selectedTab,
                        backdrop = miuixBackdrop,
                        tabs = tabs,
                        onTabChanged = onTabChanged,
                    )
                },
            ) { paddingValues ->
                MainPager(
                    pagerState = pagerState,
                    deviceControlStore = deviceControlStore,
                    onTabChanged = onTabChanged,
                    onUpdateThemeSettings = onUpdateThemeSettings,
                    pendingRestart = pendingRestart,
                    onConsumeRestart = onConsumeRestart,
                    onRequestRestart = onRequestRestart,
                    outerPadding = paddingValues,
                    backdrop = miuixBackdrop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** 标准底栏：模糊可用时对采样层做 25dp 模糊。 */
@Composable
private fun StandardBottomBar(
    selectedTab: TopLevelTab,
    backdrop: LayerBackdrop?,
    tabs: List<NavigationTab>,
    onTabChanged: (TopLevelTab) -> Unit,
) {
    val surfaceColor = MiuixTheme.colorScheme.surface
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = RectangleShape,
                        blurRadius = 25f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(
                                    color = surfaceColor.copy(alpha = 0.8f),
                                ),
                            ),
                        ),
                    )
                } else {
                    Modifier.background(surfaceColor)
                },
            ),
    ) {
        NavigationBar(
            modifier = Modifier.fillMaxWidth(),
            color = if (backdrop != null) Color.Transparent else surfaceColor,
            showDivider = false,
        ) {
            tabs.forEach { tab ->
                NavigationBarItem(
                    selected = selectedTab == tab.destination,
                    onClick = { onTabChanged(tab.destination) },
                    icon = tab.icon,
                    label = tab.label,
                )
            }
        }
    }
}
