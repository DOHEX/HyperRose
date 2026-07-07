package com.dohex.hyperrose.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.data.DeviceImageStore
import com.dohex.hyperrose.data.LocalDeviceImageStore
import com.dohex.hyperrose.model.DeviceColorProfile
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.ThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun EarphoneColorSettingsPage(
    address: String,
    deviceId: String?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val deviceImageStore = LocalDeviceImageStore.current
    val themeMode = LocalThemeMode.current
    val backdrop = rememberBlurBackdrop(themeMode.enableBlur)
    val blurActive = themeMode.enableBlur && backdrop != null
    val scrollBehavior = MiuixScrollBehavior()
    val scope = rememberCoroutineScope()

    val colorProfile = DeviceColorProfile.forDevice(deviceId)
    val colors = colorProfile?.availableColors?.toList() ?: emptyList()
    val defaultTheme =
        colorProfile?.defaultTheme() ?: DeviceColorProfile.DEFAULT_PROFILE.defaultTheme()
    val currentTheme by deviceImageStore.colorThemeFlow(address, colorProfile)
        .collectAsState(initial = defaultTheme)

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = backdrop, blurEnabled = blurActive) {
                TopAppBar(
                    title = "耳机颜色",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (backdrop != null) Modifier.layerBackdrop(backdrop) else Modifier),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding(),
                    start = 10.dp,
                    end = 10.dp,
                ),
            ) {
                item {
                    Card {
                        Image(
                            painter = painterResource(currentTheme.caseRes),
                            contentDescription = currentTheme.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .padding(16.dp),
                        )
                    }
                }

                item {
                    Card {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            for (color in colors) {
                                val theme = colorProfile!!.themeFor(color) ?: continue
                                val isSelected = color == currentTheme.color
                                val chipColor = color.displayColor
                                val borderMod = if (isSelected) {
                                    Modifier.border(3.dp, chipColor, RoundedCornerShape(12.dp))
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        Color.Gray.copy(alpha = 0.3f),
                                        RoundedCornerShape(12.dp)
                                    )
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .then(borderMod)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            scope.launch {
                                                deviceImageStore.setColorTheme(address, theme)
                                            }
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(chipColor),
                                    )
                                    Image(
                                        painter = painterResource(theme.caseRes),
                                        contentDescription = theme.label,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(56.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ─── Previews ────────────────────────────────────────────────────

@Preview(showBackground = true, name = "i5 颜色设置")
@Composable
private fun EarphoneColorSettingsPagePreview_I5() {
    val context = LocalContext.current
    val imageStore = remember { DeviceImageStore(context) }
    HyperRoseTheme {
        CompositionLocalProvider(
            LocalDeviceImageStore provides imageStore,
            LocalThemeMode provides ThemeMode(),
        ) {
            EarphoneColorSettingsPage(
                address = "00:00:00:00:00:00",
                deviceId = "rose-earfree-i5",
                onBack = {},
            )
        }
    }
}

@Preview(showBackground = true, name = "MK2 颜色设置")
@Composable
private fun EarphoneColorSettingsPagePreview_MK2() {
    val context = LocalContext.current
    val imageStore = remember { DeviceImageStore(context) }
    HyperRoseTheme {
        CompositionLocalProvider(
            LocalDeviceImageStore provides imageStore,
            LocalThemeMode provides ThemeMode(),
        ) {
            EarphoneColorSettingsPage(
                address = "00:00:00:00:00:01",
                deviceId = "rose-budsfeel-mk2",
                onBack = {},
            )
        }
    }
}
