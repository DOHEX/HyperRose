package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.R
import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AncSelector(
    modifier: Modifier = Modifier,
    ancMode: AncMode?,
    ancDepth: AncDepth?,
    transLevel: TransparencyLevel?,
    supportedAncModes: Set<AncMode>,
    supportedAncDepths: Set<AncDepth>,
    supportedTransLevels: Set<TransparencyLevel>,
    onAncModeChange: (AncMode) -> Unit,
    onAncDepthChange: (AncDepth) -> Unit,
    onTransLevelChange: (TransparencyLevel) -> Unit,
    enabled: Boolean,
    showAncDepth: Boolean = true,
    showTransLevel: Boolean = true,
) {
    val modes = AncMode.entries.filter { it in supportedAncModes }
    val depths = AncDepth.entries.filter { it in supportedAncDepths }
    val transLevels = TransparencyLevel.entries.filter { it in supportedTransLevels }

    SectionCard(
        title = "噪声控制", modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            modes.forEach { mode ->
                val selected = ancMode == mode
                AncModeIcon(
                    mode = mode,
                    selected = selected,
                    enabled = enabled,
                    onClick = { onAncModeChange(mode) },
                )
            }
        }

        if (showAncDepth && depths.isNotEmpty() && ancMode == AncMode.NOISE_CANCEL && ancDepth != null) {
            val depthOptions = depths.map { it.label }
            val depthSelectedIndex = depths.indexOf(ancDepth).coerceAtLeast(0)

            TabRowWithContour(
                tabs = depthOptions,
                selectedTabIndex = depthSelectedIndex,
                onTabSelected = { index ->
                    if (enabled) depths.getOrNull(index)?.let(onAncDepthChange)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            )
        }

        if (showTransLevel && transLevels.isNotEmpty() &&
            ancMode == AncMode.TRANSPARENT && transLevel != null
        ) {
            val transOptions = transLevels.map { it.label }
            val transSelectedIndex = transLevels.indexOf(transLevel).coerceAtLeast(0)

            TabRowWithContour(
                tabs = transOptions,
                selectedTabIndex = transSelectedIndex,
                onTabSelected = { index ->
                    if (enabled) transLevels.getOrNull(index)?.let(onTransLevelChange)
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
    val colors = MiuixTheme.colorScheme

    val iconRes = when (mode) {
        AncMode.NOISE_CANCEL,
        AncMode.ADAPTIVE_NOISE_CANCEL,
        AncMode.EXTREME_NOISE_CANCEL,
            -> R.drawable.anc_normal
        AncMode.WIND_NOISE -> R.drawable.anc_wind
        AncMode.NORMAL -> R.drawable.anc_close
        AncMode.TRANSPARENT -> R.drawable.anc_trans
    }

    val bgColor = when {
        !enabled -> colors.disabledSecondary
        selected -> colors.primary
        else -> colors.secondary
    }
    val iconTint = when {
        !enabled -> colors.disabledOnSecondary
        selected -> colors.onPrimary
        else -> colors.onSurfaceSecondary
    }
    val labelColor = when {
        !enabled -> colors.disabledSecondary
        selected -> colors.primary
        else -> colors.onSurfaceSecondary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(bgColor, CircleShape),
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(id = iconRes),
                contentDescription = mode.label,
                tint = iconTint,
                modifier = Modifier
                    .size(54.dp)
                    .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 10.dp),
            )
        }
        Text(
            text = mode.label,
            fontSize = 14.sp,
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
            supportedAncModes = setOf(AncMode.NOISE_CANCEL, AncMode.NORMAL, AncMode.TRANSPARENT),
            supportedAncDepths = AncDepth.entries.toSet(),
            supportedTransLevels = TransparencyLevel.entries.toSet(),
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
            supportedAncModes = setOf(AncMode.NOISE_CANCEL, AncMode.NORMAL, AncMode.TRANSPARENT),
            supportedAncDepths = AncDepth.entries.toSet(),
            supportedTransLevels = TransparencyLevel.entries.toSet(),
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
            supportedAncModes = setOf(AncMode.NOISE_CANCEL, AncMode.NORMAL),
            supportedAncDepths = AncDepth.entries.toSet(),
            supportedTransLevels = TransparencyLevel.entries.toSet(),
            onAncModeChange = {},
            onAncDepthChange = {},
            onTransLevelChange = {},
            enabled = false,
            modifier = Modifier.padding(16.dp),
        )
    }
}
