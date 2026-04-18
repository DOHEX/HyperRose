package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch

@Composable
fun FeatureToggle(
    title: String,
    value: Boolean,
    onToggle: (Boolean) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = title,
        subtitle = if (value) "已开启" else "已关闭",
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (value) "低延迟优先" else "标准模式",
                color = Color(0xFF5D6A7A),
                fontWeight = FontWeight.Medium
            )
            Switch(
                checked = value,
                onCheckedChange = onToggle,
                enabled = enabled
            )
        }
    }
}
