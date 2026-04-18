package com.dohex.hyperrose.model

import com.dohex.hyperrose.domain.audio.EqPreset
import com.dohex.hyperrose.domain.audio.AncDepth as DomainAncDepth
import com.dohex.hyperrose.domain.audio.AncMode as DomainAncMode
import com.dohex.hyperrose.domain.audio.TransparencyLevel
import com.dohex.hyperrose.domain.battery.EarBatteryState
import com.dohex.hyperrose.domain.battery.TwsBatteryState
import com.dohex.hyperrose.ipc.HyperRoseIpc

typealias TransLevel = TransparencyLevel
typealias EqMode = EqPreset
typealias AncMode = DomainAncMode
typealias AncDepth = DomainAncDepth
typealias EarBattery = EarBatteryState
typealias TwsBatteryInfo = TwsBatteryState

object HyperRoseAction {
    const val SHOW_ISLAND = HyperRoseIpc.SHOW_ISLAND

    const val DEVICE_CONNECTED = HyperRoseIpc.DEVICE_CONNECTED
    const val DEVICE_DISCONNECTED = HyperRoseIpc.DEVICE_DISCONNECTED
    const val BATTERY_CHANGED = HyperRoseIpc.BATTERY_CHANGED
    const val ANC_CHANGED = HyperRoseIpc.ANC_CHANGED
    const val ANC_DEPTH_CHANGED = HyperRoseIpc.ANC_DEPTH_CHANGED
    const val TRANS_LEVEL_CHANGED = HyperRoseIpc.TRANS_LEVEL_CHANGED
    const val EQ_CHANGED = HyperRoseIpc.EQ_CHANGED
    const val GAME_MODE_CHANGED = HyperRoseIpc.GAME_MODE_CHANGED

    const val SET_ANC = HyperRoseIpc.SET_ANC
    const val SET_ANC_DEPTH = HyperRoseIpc.SET_ANC_DEPTH
    const val SET_TRANS_LEVEL = HyperRoseIpc.SET_TRANS_LEVEL
    const val SET_EQ = HyperRoseIpc.SET_EQ
    const val SET_GAME_MODE = HyperRoseIpc.SET_GAME_MODE
    const val FIND_EARPHONE = HyperRoseIpc.FIND_EARPHONE
    const val REFRESH_STATUS = HyperRoseIpc.REFRESH_STATUS
    const val DISCONNECT_GATT = HyperRoseIpc.DISCONNECT_GATT

    const val EXTRA_DEVICE = HyperRoseIpc.EXTRA_DEVICE
    const val EXTRA_MODE = HyperRoseIpc.EXTRA_MODE
    const val EXTRA_DEPTH = HyperRoseIpc.EXTRA_DEPTH
    const val EXTRA_LEVEL = HyperRoseIpc.EXTRA_LEVEL
    const val EXTRA_ENABLED = HyperRoseIpc.EXTRA_ENABLED
    const val EXTRA_SIDE = HyperRoseIpc.EXTRA_SIDE
    const val EXTRA_LEFT_LEVEL = HyperRoseIpc.EXTRA_LEFT_LEVEL
    const val EXTRA_RIGHT_LEVEL = HyperRoseIpc.EXTRA_RIGHT_LEVEL
    const val EXTRA_LEFT_CHARGING = HyperRoseIpc.EXTRA_LEFT_CHARGING
    const val EXTRA_RIGHT_CHARGING = HyperRoseIpc.EXTRA_RIGHT_CHARGING
    const val EXTRA_CASE_LEVEL = HyperRoseIpc.EXTRA_CASE_LEVEL
}
