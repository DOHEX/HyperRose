package com.dohex.hyperrose.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.dohex.hyperrose.model.EqPreset
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference

@Composable
fun EqSelector(
    eqMode: EqPreset?,
    onSelect: (EqPreset) -> Unit,
    enabled: Boolean,
    presets: List<EqPreset> = EqPreset.entries.toList(),
    modifier: Modifier = Modifier,
) {
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
