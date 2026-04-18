package com.dohex.hyperrose.domain.battery

/** 单耳电池状态 */
data class EarBatteryState(
    val level: Int,
    val isCharging: Boolean
)

/** TWS 完整电池信息（左耳 + 右耳 + 充电盒） */
data class TwsBatteryState(
    val left: EarBatteryState? = null,
    val right: EarBatteryState? = null,
    val caseBattery: Int? = null
)
