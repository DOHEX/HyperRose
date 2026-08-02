package com.dohex.hyperrose.ui.screen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.debug.BleLog
import com.dohex.hyperrose.ipc.HyperRoseIpc
import com.dohex.hyperrose.profile.DeviceCatalog
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.ui.state.DeviceControlStore
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.Clear
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BleDebugPage(
    deviceControlStore: DeviceControlStore,
    profile: DeviceProfile,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val bleEntries by BleLog.entries.collectAsState()
    var clearRequest by remember { mutableIntStateOf(0) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != HyperRoseIpc.BLE_LOG) return
                BleLog.log(
                    source = intent.getStringExtra(HyperRoseIpc.EXTRA_LOG_SOURCE).orEmpty(),
                    direction = intent.getStringExtra(HyperRoseIpc.EXTRA_LOG_DIRECTION).orEmpty(),
                    data = intent.getStringExtra(HyperRoseIpc.EXTRA_LOG_DATA).orEmpty(),
                    parsed = intent.getStringExtra(HyperRoseIpc.EXTRA_LOG_PARSED).orEmpty(),
                    time = intent.getStringExtra(HyperRoseIpc.EXTRA_LOG_TIME).orEmpty(),
                )
            }
        }
        context.registerReceiver(
            receiver, IntentFilter(HyperRoseIpc.BLE_LOG), Context.RECEIVER_EXPORTED
        )
        Intent(HyperRoseIpc.BLE_LOG_CONNECT).apply {
            setPackage(HyperRoseIpc.PACKAGE_BLUETOOTH)
            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            context.sendBroadcast(this)
        }
        onDispose {
            Intent(HyperRoseIpc.BLE_LOG_DISCONNECT).apply {
                setPackage(HyperRoseIpc.PACKAGE_BLUETOOTH)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                context.sendBroadcast(this)
            }
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(clearRequest) {
        if (clearRequest > 0) {
            BleLog.clear()
            Intent(HyperRoseIpc.BLE_LOG_CLEAR).apply {
                setPackage(HyperRoseIpc.PACKAGE_BLUETOOTH)
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                context.sendBroadcast(this)
            }
        }
    }


    BleDebugPageContent(
        profile = profile,
        entries = bleEntries,
        onBack = onBack,
        onClear = { clearRequest++ },
        onSendRaw = deviceControlStore::sendRawCommand,
        modifier = modifier,
    )
}

@Composable
private fun BleDebugPageContent(
    profile: DeviceProfile,
    entries: List<BleLog.Entry>,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onSendRaw: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    var hexInput by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            listState.animateScrollBy(50_000f, tween(280))
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            SmallTopAppBar(
                title = "调试 · ${profile.displayName}",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(MiuixIcons.ChevronBackward, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onClear) {
                        Icon(MiuixIcons.Clear, contentDescription = "清空日志")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 12.dp,
                    top = paddingValues.calculateTopPadding() + 12.dp,
                    end = 12.dp,
                    bottom = paddingValues.calculateBottomPadding() + 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (profile.debugQuickCommands.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profile.debugQuickCommands.forEach { (label, command) ->
                        Button(onClick = { onSendRaw(command.toHexStr()) }) {
                            Text(label)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (entries.isEmpty()) {
                    item(key = "empty") {
                        Card {
                            Text(
                                text = "等待指令…\n连接耳机后收发指令会在此显示",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                items(entries, key = { "${it.time}_${entries.indexOf(it)}" }) { entry ->
                    BleLogCard(entry = entry) {
                        clipboardManager.setText(AnnotatedString(entry.data))
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HexInputField(
                    value = hexInput,
                    onValueChange = { hexInput = it.uppercase() },
                    hint = profile.debugHexHint,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    text = "发送",
                    onClick = {
                        if (hexInput.isNotBlank()) {
                            onSendRaw(hexInput)
                            hexInput = ""
                        }
                    },
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        }
    }
}

@Composable
private fun BleLogCard(entry: BleLog.Entry, onCopy: () -> Unit) {
    val directionColor = when (entry.direction) {
        "TX" -> Color(0xFFFF9800)
        "RX" -> Color(0xFF00BCD4)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    val sourceColor = when (entry.source) {
        "App" -> Color(0xFF4CAF50)
        "Hook" -> Color(0xFF2196F3)
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }

    Card(onClick = onCopy) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.time,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = entry.source,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = sourceColor,
                )
                Text(
                    text = entry.direction,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = directionColor,
                )
            }
            if (entry.parsed.isNotEmpty()) {
                Text(
                    text = entry.parsed,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            Text(
                text = entry.data,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 4,
            )
        }
    }
}

@Composable
private fun HexInputField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .heightIn(min = 44.dp)
            .background(MiuixTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        textStyle = TextStyle(
            color = MiuixTheme.colorScheme.onSurface,
            fontSize = 14.sp,
            fontFamily = FontFamily.Monospace,
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = hint,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Preview(showBackground = true, name = "BLE Debug - i7 Empty")
@Composable
private fun BleDebugPagePreview_I7_Empty() {
    val profile = requireNotNull(DeviceCatalog.findById("rose-earfeel-i7")?.profile)
    HyperRoseTheme {
        BleDebugPageContent(
            profile = profile,
            entries = emptyList(),
            onBack = {},
            onClear = {},
            onSendRaw = {},
        )
    }
}

@Preview(showBackground = true, name = "BLE Debug - i7 Log")
@Composable
private fun BleDebugPagePreview_I7_Log() {
    val profile = requireNotNull(DeviceCatalog.findById("rose-earfeel-i7")?.profile)
    HyperRoseTheme {
        BleDebugPageContent(
            profile = profile,
            entries = listOf(
                BleLog.Entry(
                    source = "App",
                    direction = "TX",
                    data = "FF 00 02 09 01 11 AA",
                    parsed = "Set ANC: NOISE_CANCEL",
                    time = "12:00:00.000",
                ),
            ),
            onBack = {},
            onClear = {},
            onSendRaw = {},
        )
    }
}

private fun ByteArray.toHexStr(): String = joinToString(" ") { "%02X".format(it) }
