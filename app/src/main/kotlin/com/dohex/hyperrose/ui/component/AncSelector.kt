package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.R
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AncSelector(
    ancMode: AncMode?,
    ancDepth: AncDepth?,
    transLevel: TransparencyLevel?,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    SectionCard(
        title = "噪声控制",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AncMode.entries.forEach { mode ->
                val selected = ancMode == mode
                AncModeIcon(
                    mode = mode,
                    selected = selected,
                    enabled = enabled,
                    onClick = { onAncModeChange(mode) },
                )
            }
        }

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
                    .padding(top = 12.dp),
            )
        }

        if (ancMode == AncMode.TRANSPARENT) {
            val transOptions = TransparencyLevel.entries.map { it.label }
            val transSelectedIndex = TransparencyLevel.entries.indexOf(transLevel).coerceAtLeast(0)

            TabRowWithContour(
                tabs = transOptions,
                selectedTabIndex = transSelectedIndex,
                onTabSelected = { index ->
                    if (enabled) {
                        TransparencyLevel.entries.getOrNull(index)?.let(onTransLevelChange)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AncModeIcon(
    mode: AncMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MiuixTheme.colorScheme

    val iconRes = when (mode) {
        AncMode.NOISE_CANCEL -> if (selected) R.drawable.anc_normal_activate else R.drawable.anc_normal
        AncMode.WIND_NOISE -> if (selected) R.drawable.anc_wind_activate else R.drawable.anc_wind
        AncMode.NORMAL -> if (selected) R.drawable.anc_close_activate else R.drawable.anc_close
        AncMode.TRANSPARENT -> if (selected) R.drawable.anc_trans_activate else R.drawable.anc_trans
    }

    val bgColor = when {
        !enabled -> Color.Transparent
        selected -> colorScheme.primary
        else -> Color.Transparent
    }
    val iconTint = when {
        !enabled -> colorScheme.disabledOnSurface
        !selected -> colorScheme.onSurfaceVariantSummary
        else -> null
    }
    val labelColor = when {
        !enabled -> colorScheme.disabledOnSurface
        selected -> colorScheme.primary
        else -> colorScheme.onSurfaceVariantSummary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = mode.label,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bgColor, CircleShape)
                .clickable(enabled = enabled) { onClick() }
                .padding(10.dp),
            colorFilter = iconTint?.let { ColorFilter.tint(it) },
        )
        Text(
            text = mode.label,
            fontSize = 13.sp,
            color = labelColor,
            fontWeight = if (selected && enabled) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AncSelectorPreview_NoiseCancel() {
    HyperRoseTheme {
        AncSelector(
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = AncDepth.DEEP,
            transLevel = null,
            onAncModeChange = {},
            onAncDepthChange = {},
            onTransLevelChange = {},
            enabled = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AncSelectorPreview_Transparent() {
    HyperRoseTheme {
        AncSelector(
            ancMode = AncMode.TRANSPARENT,
            ancDepth = null,
            transLevel = TransparencyLevel.COMFORTABLE,
            onAncModeChange = {},
            onAncDepthChange = {},
            onTransLevelChange = {},
            enabled = true,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun AncSelectorPreview_Disabled() {
    HyperRoseTheme {
        AncSelector(
            ancMode = AncMode.NOISE_CANCEL,
            ancDepth = AncDepth.LIGHT,
            transLevel = null,
            onAncModeChange = {},
            onAncDepthChange = {},
            onTransLevelChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}
