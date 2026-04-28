package com.dohex.hyperrose.domain.battery

private const val MIN_BATTERY_LEVEL = 0
private const val MAX_BATTERY_LEVEL = 100

const val UNKNOWN_BATTERY_LEVEL = 0xFF

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

fun Int.asBatteryLevelOrNull(): Int? = takeIf { it in MIN_BATTERY_LEVEL..MAX_BATTERY_LEVEL }

fun Int.isBatteryLevelOrUnknown(): Boolean {
    return asBatteryLevelOrNull() != null || this == UNKNOWN_BATTERY_LEVEL
}

fun TwsBatteryState.withLastKnownCaseBattery(previous: TwsBatteryState?): TwsBatteryState {
    val fallbackCaseBattery = previous?.caseBattery ?: return this
    return if (caseBattery != null) this else copy(caseBattery = fallbackCaseBattery)
}
