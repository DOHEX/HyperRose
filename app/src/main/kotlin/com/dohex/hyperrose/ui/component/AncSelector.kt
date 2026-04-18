package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.TransLevel
import top.yukonga.miuix.kmp.basic.TabRowWithContour

@Composable
fun AncSelector(
    ancMode: AncMode?,
    ancDepth: AncDepth?,
    transLevel: TransLevel?,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransLevel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "降噪/通透",
        subtitle = ancMode?.label ?: "未获取模式",
        modifier = modifier
    ) {
        val modeOptions = AncMode.entries.map { it.label }
        val modeSelectedIndex = AncMode.entries.indexOf(ancMode).coerceAtLeast(0)

        TabRowWithContour(
            tabs = modeOptions,
            selectedTabIndex = modeSelectedIndex,
            onTabSelected = { index ->
                if (enabled) {
                    AncMode.entries.getOrNull(index)?.let(onAncModeChange)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (ancMode == AncMode.NOISE_CANCEL) {
            val depthOptions = AncDepth.entries.map { it.label }
            val depthSelectedIndex = AncDepth.entries.indexOf(ancDepth).coerceAtLeast(0)

            TabRowWithContour(
                tabs = depthOptions,
                selectedTabIndex = depthSelectedIndex,
                onTabSelected = { index ->
                    if (enabled) {
                        AncDepth.entries.getOrNull(index)?.let(onAncDepthChange)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }

        if (ancMode == AncMode.TRANSPARENT) {
            val transOptions = TransLevel.entries.map { it.label }
            val transSelectedIndex = TransLevel.entries.indexOf(transLevel).coerceAtLeast(0)

            TabRowWithContour(
                tabs = transOptions,
                selectedTabIndex = transSelectedIndex,
                onTabSelected = { index ->
                    if (enabled) {
                        TransLevel.entries.getOrNull(index)?.let(onTransLevelChange)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            )
        }
    }
}
