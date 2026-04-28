package com.dohex.hyperrose.ui.page

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.domain.scope.ScopePackagesToRestart
import com.dohex.hyperrose.domain.scope.restartScopePackages
import com.dohex.hyperrose.ui.theme.BlurredBar
import com.dohex.hyperrose.ui.theme.ColorModeOptions
import com.dohex.hyperrose.ui.theme.LocalCanUpdateThemeMode
import com.dohex.hyperrose.ui.theme.LocalThemeMode
import com.dohex.hyperrose.ui.theme.LocalUpdateThemeMode
import com.dohex.hyperrose.ui.theme.rememberBlurBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.core.net.toUri

private data class GithubLink(
    val title: String,
    val summary: String,
    val url: String
)

private val HyperRoseGithubLink = GithubLink(
    title = "HyperRose",
    summary = "DOHEX/HyperRose",
    url = "https://github.com/DOHEX/HyperRose"
)

private val ThanksGithubLinks = listOf(
    GithubLink(
        title = "OppoPods",
        summary = "Leaf-lsgtky/OppoPods",
        url = "https://github.com/Leaf-lsgtky/OppoPods"
    ),
    GithubLink(
        title = "HyperPods",
        summary = "Art-Chen/HyperPods",
        url = "https://github.com/Art-Chen/HyperPods"
    ),
    GithubLink(
        title = "HyperOriG",
        summary = "KiriChen-Wind/HyperOriG",
        url = "https://github.com/KiriChen-Wind/HyperOriG"
    ),
    GithubLink(
        title = "Miuix",
        summary = "compose-miuix-ui/miuix",
        url = "https://github.com/compose-miuix-ui/miuix"
    )
)

@Composable
fun SettingsPage(
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
                            Icon(MiuixIcons.Refresh, contentDescription = "重启作用域")
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
                        onSelectedIndexChange = {
                            updateThemeMode { state ->
                                state.copy(colorMode = it)
                            }
                        },
                    )
                    if (isRenderEffectSupported()) {
                        SwitchPreference(
                            title = "启用模糊",
                            checked = themeMode.enableBlur,
                            enabled = canUpdateThemeMode,
                            onCheckedChange = {
                                updateThemeMode { state ->
                                    state.copy(enableBlur = it)
                                }
                            }
                        )
                    }
                    SwitchPreference(
                        title = "平滑圆角",
                        checked = themeMode.smoothRounding,
                        enabled = canUpdateThemeMode,
                        onCheckedChange = {
                            updateThemeMode { state ->
                                state.copy(smoothRounding = it)
                            }
                        }
                    )
                }
            }
            item {
                Card {
                    ArrowPreference(
                        title = HyperRoseGithubLink.title,
                        summary = HyperRoseGithubLink.summary,
                        onClick = { openExternalUrl(context, HyperRoseGithubLink.url) }
                    )
                }
            }
            item {
                Card {
                    ThanksGithubLinks.forEach { link ->
                        ArrowPreference(
                            title = link.title,
                            summary = link.summary,
                            onClick = { openExternalUrl(context, link.url) }
                        )
                    }
                }
            }
        }

        OverlayDialog(
            title = "确认重启作用域？",
            show = showRestartDialog,
            onDismissRequest = { showRestartDialog = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScopePackagesToRestart.forEach { pkg ->
                    Text(
                        text = pkg,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        text = "取消",
                        onClick = { showRestartDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = "确定",
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = {
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
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun openExternalUrl(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "未找到可打开链接的应用", Toast.LENGTH_SHORT).show()
    }
}
