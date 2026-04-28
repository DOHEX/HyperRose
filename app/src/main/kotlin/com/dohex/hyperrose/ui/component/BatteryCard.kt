package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.domain.battery.asBatteryLevelOrNull
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun BatteryCard(
    battery: TwsBatteryState?,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "电量",
        subtitle = batterySubtitle(battery),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            BatteryCell(
                label = "左耳",
                value = battery?.left?.level,
                charging = battery?.left?.isCharging == true,
                modifier = Modifier.weight(1f)
            )
            BatteryCell(
                label = "右耳",
                value = battery?.right?.level,
                charging = battery?.right?.isCharging == true,
                modifier = Modifier.weight(1f)
            )
            BatteryCell(
                label = "充电盒",
                value = battery?.caseBattery,
                charging = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun BatteryCell(
    label: String,
    value: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = Color(0xFF5F6D7D))
        Text(
            text = formatBatteryLevel(value),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 4.dp)
        )
        if (charging) {
            Text(
                text = "充电中",
                color = Color(0xFF2A6AA0),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private fun batterySubtitle(battery: TwsBatteryState?): String {
    if (battery == null) return "等待电量数据"
    val left = formatBatteryLevel(battery.left?.level)
    val right = formatBatteryLevel(battery.right?.level)
    val caseLevel = formatBatteryLevel(battery.caseBattery)
    return "L $left  ·  R $right  ·  C $caseLevel"
}

private fun formatBatteryLevel(level: Int?): String {
    return level?.asBatteryLevelOrNull()?.let { "$it%" } ?: "-"
}
