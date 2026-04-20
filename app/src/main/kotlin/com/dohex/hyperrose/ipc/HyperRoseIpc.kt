package com.dohex.hyperrose.ipc

/**
 * 广播 Action 常量。
 * 用于 Hook 进程间通信（Bluetooth → MiBluetooth → App）。
 */
object HyperRoseIpc {
    const val PACKAGE_APP = "com.dohex.hyperrose"
    const val PACKAGE_BLUETOOTH = "com.android.bluetooth"
    const val PACKAGE_MI_BLUETOOTH = "com.xiaomi.bluetooth"
    const val PACKAGE_SYSTEM_UI = "com.android.systemui"

    const val QUICK_CONTROL_ACTIVITY = "$PACKAGE_APP.entry.QuickControlActivity"

    private const val PREFIX = "com.dohex.hyperrose.action"

    private const val EXTRA_PREFIX = "com.dohex.hyperrose.extra"

    // Bluetooth 进程 → MiBluetooth 进程
    const val SHOW_ISLAND = "$PREFIX.show_island"

    // Bluetooth 进程 ↔ App
    const val DEVICE_CONNECTED = "$PREFIX.device_connected"
    const val DEVICE_DISCONNECTED = "$PREFIX.device_disconnected"
    const val BATTERY_CHANGED = "$PREFIX.battery_changed"
    const val ANC_CHANGED = "$PREFIX.anc_changed"
    const val ANC_DEPTH_CHANGED = "$PREFIX.anc_depth_changed"
    const val TRANS_LEVEL_CHANGED = "$PREFIX.trans_level_changed"
    const val EQ_CHANGED = "$PREFIX.eq_changed"
    const val GAME_MODE_CHANGED = "$PREFIX.game_mode_changed"

    // App → Bluetooth 进程（控制命令）
    const val SET_ANC = "$PREFIX.set_anc"
    const val SET_ANC_DEPTH = "$PREFIX.set_anc_depth"
    const val SET_TRANS_LEVEL = "$PREFIX.set_trans_level"
    const val SET_EQ = "$PREFIX.set_eq"
    const val SET_GAME_MODE = "$PREFIX.set_game_mode"
    const val FIND_EARPHONE = "$PREFIX.find_earphone"
    const val REFRESH_STATUS = "$PREFIX.refresh_status"
    const val DISCONNECT_GATT = "$PREFIX.disconnect_gatt"

    // 广播 Extra 键
    const val EXTRA_DEVICE = "$EXTRA_PREFIX.device"
    const val EXTRA_DEVICE_NAME = "$EXTRA_PREFIX.device_name"
    const val EXTRA_FORCE_CONNECTED = "$EXTRA_PREFIX.force_connected"
    const val EXTRA_MODE = "$EXTRA_PREFIX.mode"
    const val EXTRA_DEPTH = "$EXTRA_PREFIX.depth"
    const val EXTRA_LEVEL = "$EXTRA_PREFIX.level"
    const val EXTRA_ENABLED = "$EXTRA_PREFIX.enabled"
    const val EXTRA_SIDE = "$EXTRA_PREFIX.side"
    const val EXTRA_LEFT_LEVEL = "$EXTRA_PREFIX.left_level"
    const val EXTRA_RIGHT_LEVEL = "$EXTRA_PREFIX.right_level"
    const val EXTRA_LEFT_CHARGING = "$EXTRA_PREFIX.left_charging"
    const val EXTRA_RIGHT_CHARGING = "$EXTRA_PREFIX.right_charging"
    const val EXTRA_CASE_LEVEL = "$EXTRA_PREFIX.case_level"

    const val SIDE_LEFT = "LEFT"
    const val SIDE_RIGHT = "RIGHT"
    const val SIDE_STOP = "STOP"

    val SCOPE_PACKAGES: List<String> = listOf(
        PACKAGE_BLUETOOTH,
        PACKAGE_MI_BLUETOOTH,
        PACKAGE_SYSTEM_UI
    )

    val BRIDGE_STATE_ACTIONS: List<String> = listOf(
        DEVICE_CONNECTED,
        DEVICE_DISCONNECTED,
        BATTERY_CHANGED,
        ANC_CHANGED,
        ANC_DEPTH_CHANGED,
        TRANS_LEVEL_CHANGED,
        EQ_CHANGED,
        GAME_MODE_CHANGED
    )

    val APP_CONTROL_ACTIONS: List<String> = listOf(
        SET_ANC,
        SET_ANC_DEPTH,
        SET_TRANS_LEVEL,
        SET_EQ,
        SET_GAME_MODE,
        FIND_EARPHONE,
        REFRESH_STATUS,
        DISCONNECT_GATT
    )
}
