package com.dohex.hyperrose.ui.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dohex.hyperrose.R
import com.dohex.hyperrose.model.EarBatteryState
import com.dohex.hyperrose.model.TwsBatteryState
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.dohex.hyperrose.ui.theme.HyperRoseTheme
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BatteryCard(
    battery: TwsBatteryState?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme

    SectionCard(title = "电量", modifier = modifier) {
        val b = battery
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (b?.left != null) {
                BatteryCell(
                    iconRes = if (b.right == null) R.drawable.battery_charge else R.drawable.battery_left,
                    label = if (b.right == null) "耳机" else "左耳",
                    value = b.left.level,
                    charging = b.left.isCharging,
                    modifier = Modifier.weight(1f),
                )
            }
            if (b?.right != null) {
                BatteryCell(
                    iconRes = R.drawable.battery_right,
                    label = "右耳",
                    value = b.right.level,
                    charging = b.right.isCharging,
                    modifier = Modifier.weight(1f),
                )
            }
            if (b?.caseBattery != null) {
                BatteryCell(
                    iconRes = R.drawable.battery_charge,
                    label = "充电盒",
                    value = b.caseBattery,
                    charging = false,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun BatteryCell(
    iconRes: Int,
    label: String,
    value: Int?,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MiuixTheme.colorScheme

    Column(
        modifier = modifier.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(id = iconRes),
            contentDescription = label,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = formatBatteryLevel(value),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (charging) {
            Text(
                text = "充电中",
                color = colorScheme.primary,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun formatBatteryLevel(level: Int?): String =
    level?.asBatteryLevelOrNull()?.let { "$it%" } ?: "-"

@Preview(showBackground = true)
@Composable
private fun BatteryCardPreview_Full() {
    HyperRoseTheme {
        BatteryCard(
            battery = TwsBatteryState(
                left = EarBatteryState(level = 85, isCharging = false),
                right = EarBatteryState(level = 72, isCharging = false),
                caseBattery = 90,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BatteryCardPreview_Charging() {
    HyperRoseTheme {
        BatteryCard(
            battery = TwsBatteryState(
                left = EarBatteryState(level = 45, isCharging = true),
                right = EarBatteryState(level = 60, isCharging = false),
                caseBattery = 30,
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BatteryCardPreview_NullBattery() {
    HyperRoseTheme {
        BatteryCard(
            battery = null,
            modifier = Modifier.padding(16.dp),
        )
    }
}
