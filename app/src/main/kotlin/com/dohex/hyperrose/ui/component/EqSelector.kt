package com.dohex.hyperrose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.ThemeSettings
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun EqSelector(
    eqMode: EqPreset?,
    onSelect: (EqPreset) -> Unit,
    enabled: Boolean,
    supportedEqPresets: Set<EqPreset>,
    modifier: Modifier = Modifier,
) {
    val presets = EqPreset.entries.filter { it in supportedEqPresets }
    val options = presets.map { it.label }
    val selectedIndex = presets.indexOf(eqMode).coerceAtLeast(0)

    Card(modifier = modifier) {
        OverlayDropdownPreference(
            title = "音色",
            items = options,
            selectedIndex = selectedIndex,
            onSelectedIndexChange = { index ->
                if (enabled) {
                    presets.getOrNull(index)?.let(onSelect)
                }
            },
            enabled = enabled,
        )
    }
}

@Preview(showBackground = true, name = "EQ - Classic")
@Composable
private fun EqSelectorPreview_Classic() {
    HyperRoseTheme(settings = ThemeSettings()) {
        EqSelector(
            eqMode = EqPreset.CLASSIC,
            onSelect = {},
            enabled = true,
            supportedEqPresets = EqPreset.entries.toSet(),
        )
    }
}

@Preview(showBackground = true, name = "EQ - Japanese")
@Composable
private fun EqSelectorPreview_Japanese() {
    HyperRoseTheme(settings = ThemeSettings()) {
        EqSelector(
            eqMode = EqPreset.JAPANESE,
            onSelect = {},
            supportedEqPresets = EqPreset.entries.toSet(),
            enabled = true,
        )
    }
}

@Preview(showBackground = true, name = "EQ - Disabled")
@Composable
private fun EqSelectorPreview_Disabled() {
    HyperRoseTheme(settings = ThemeSettings()) {
        EqSelector(
            eqMode = null,
            onSelect = {},
            supportedEqPresets = EqPreset.entries.toSet(),
            enabled = false,
        )
    }
}
