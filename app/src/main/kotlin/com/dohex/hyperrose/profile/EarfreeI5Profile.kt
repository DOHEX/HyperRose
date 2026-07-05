package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.earfree_i5.EarfreeI5CommandSet
import com.dohex.hyperrose.profile.earfree_i5.EarfreeI5GattSpec
import com.dohex.hyperrose.profile.earfree_i5.EarfreeI5GattTiming
import com.dohex.hyperrose.profile.earfree_i5.EarfreeI5Response
import com.dohex.hyperrose.profile.earfree_i5.EarfreeI5ResponseParser

object EarfreeI5Profile : DeviceProfile {
    override val id = "rose-earfree-i5"
    override val displayName = "ROSESELSA EARFREE i5"
    override val nameKeywords = listOf("ROSE EARFREE i5", "ROSE EARFEEL i5")

    override val transport = TransportSpec.Gatt(
        serviceUuid = EarfreeI5GattSpec.SERVICE_UUID,
        writeCharUuid = EarfreeI5GattSpec.WRITE_UUID,
        notifyCharUuid = EarfreeI5GattSpec.NOTIFY_UUID,
        cccdUuid = EarfreeI5GattSpec.CCCD_UUID,
    )

    override val gattTiming = GattTiming(
        initialStatusQueryDelayMs = EarfreeI5GattTiming.INITIAL_STATUS_QUERY_DELAY_MS,
        statusQueryStepDelayMs = EarfreeI5GattTiming.STATUS_QUERY_STEP_DELAY_MS,
        statusRefreshIntervalMs = EarfreeI5GattTiming.STATUS_REFRESH_INTERVAL_MS,
    )

    override val protocol: DeviceProtocol = EarfreeI5Protocol

    override val capabilities = DeviceCapabilities(
        supportedAncModes = AncMode.entries.toSet(),
        supportedAncDepths = AncDepth.entries.toSet(),
        supportedTransLevels = TransparencyLevel.entries.toSet(),
        supportedEqPresets = EqPreset.entries.toSet(),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = true,
    )

    override val debugHexHint = "HEX 指令 (如 08 EE 00 00 00 06...)"
    override val debugQuickCommands = listOf(
        "查询电量" to EarfreeI5CommandSet.QUERY_BATTERY,
        "查询 ANC" to EarfreeI5CommandSet.QUERY_ANC,
        "降噪模式" to EarfreeI5CommandSet.ANC_NOISE_CANCEL,
        "普通模式" to EarfreeI5CommandSet.ANC_NORMAL,
        "通透模式" to EarfreeI5CommandSet.ANC_TRANSPARENT,
        "风噪模式" to EarfreeI5CommandSet.ANC_WIND_NOISE,
    )
}

/** Adapts existing EarfreeI5CommandSet + EarfreeI5ResponseParser → DeviceProtocol. */
private object EarfreeI5Protocol : DeviceProtocol {
    override fun ancCommand(mode: AncMode) = EarfreeI5CommandSet.ancCommand(mode)
    override fun ancDepthCommand(depth: AncDepth) = EarfreeI5CommandSet.ancDepthCommand(depth)
    override fun transLevelCommand(level: TransparencyLevel) =
        EarfreeI5CommandSet.transLevelCommand(level)

    override fun eqCommand(mode: EqPreset) = EarfreeI5CommandSet.eqCommand(mode)
    override fun gameModeCommand(enabled: Boolean) = EarfreeI5CommandSet.gameModeCommand(enabled)
    override val findLeftOn get() = EarfreeI5CommandSet.FIND_LEFT_ON
    override val findRightOn get() = EarfreeI5CommandSet.FIND_RIGHT_ON
    override val findAllOff get() = EarfreeI5CommandSet.FIND_ALL_OFF
    override val queryBattery get() = EarfreeI5CommandSet.QUERY_BATTERY
    override val queryAnc get() = EarfreeI5CommandSet.QUERY_ANC
    override val queryAncDepth get() = EarfreeI5CommandSet.QUERY_ANC_DEPTH
    override val queryTransLevel get() = EarfreeI5CommandSet.QUERY_TRANS_LEVEL
    override val queryEq get() = EarfreeI5CommandSet.QUERY_EQ
    override val queryGameMode get() = EarfreeI5CommandSet.QUERY_GAME_MODE
    override val statusQuerySequence get() = EarfreeI5CommandSet.STATUS_QUERY_SEQUENCE.toList()

    override fun parseResponse(data: ByteArray): DeviceResponse =
        when (val r = EarfreeI5ResponseParser.parse(data)) {
            is EarfreeI5Response.Battery -> DeviceResponse.Battery(r.info)
            is EarfreeI5Response.Anc -> DeviceResponse.Anc(r.mode)
            is EarfreeI5Response.AncDepthChanged -> DeviceResponse.AncDepthChanged(r.depth)
            is EarfreeI5Response.TransparencyChanged -> DeviceResponse.TransparencyChanged(r.level)
            is EarfreeI5Response.Eq -> DeviceResponse.Eq(r.mode)
            is EarfreeI5Response.GameMode -> DeviceResponse.GameMode(r.enabled)
            is EarfreeI5Response.Unknown -> DeviceResponse.Unknown
        }
}
