package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.earfeel_i5.EarFeelI5CommandSet
import com.dohex.hyperrose.profile.earfeel_i5.EarFeelI5GattSpec
import com.dohex.hyperrose.profile.earfeel_i5.EarFeelI5GattTiming
import com.dohex.hyperrose.profile.earfeel_i5.EarFeelI5Response
import com.dohex.hyperrose.profile.earfeel_i5.EarFeelI5ResponseParser

object EarFeelI5Profile : DeviceProfile {
    override val id = "rose-earfeel-i5"
    override val displayName = "EarFeel i5"
    override val nameKeywords = listOf("EarFeel i5", "ROSE EARFREE i5")

    override val transport = TransportSpec.Gatt(
        serviceUuid = EarFeelI5GattSpec.SERVICE_UUID,
        writeCharUuid = EarFeelI5GattSpec.WRITE_UUID,
        notifyCharUuid = EarFeelI5GattSpec.NOTIFY_UUID,
        cccdUuid = EarFeelI5GattSpec.CCCD_UUID,
    )

    override val gattTiming = GattTiming(
        initialStatusQueryDelayMs = EarFeelI5GattTiming.INITIAL_STATUS_QUERY_DELAY_MS,
        statusQueryStepDelayMs = EarFeelI5GattTiming.STATUS_QUERY_STEP_DELAY_MS,
        statusRefreshIntervalMs = EarFeelI5GattTiming.STATUS_REFRESH_INTERVAL_MS,
    )

    override val protocol: DeviceProtocol = EarFeelI5Protocol

    override val capabilities = DeviceCapabilities(
        supportedAncModes = setOf(
            AncMode.NOISE_CANCEL,
            AncMode.WIND_NOISE,
            AncMode.NORMAL,
            AncMode.TRANSPARENT,
        ),
        supportedAncDepths = AncDepth.entries.toSet(),
        supportedTransLevels = setOf(
            TransparencyLevel.COMFORTABLE,
            TransparencyLevel.VOCAL,
            TransparencyLevel.STANDARD,
        ),
        supportedEqPresets = setOf(
            EqPreset.CLASSIC,
            EqPreset.INSTRUMENT,
            EqPreset.FRESH,
        ),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = true,
    )

    override val debugHexHint = "HEX 指令 (如 08 EE 00 00 00 06...)"
    override val debugQuickCommands = listOf(
        "查询电量" to EarFeelI5CommandSet.QUERY_BATTERY,
        "查询 ANC" to EarFeelI5CommandSet.QUERY_ANC,
        "降噪模式" to EarFeelI5CommandSet.ANC_NOISE_CANCEL,
        "普通模式" to EarFeelI5CommandSet.ANC_NORMAL,
        "通透模式" to EarFeelI5CommandSet.ANC_TRANSPARENT,
        "风噪模式" to EarFeelI5CommandSet.ANC_WIND_NOISE,
    )
}

/** Adapts existing EarFeelI5CommandSet + EarFeelI5ResponseParser → DeviceProtocol. */
private object EarFeelI5Protocol : DeviceProtocol {
    override fun ancCommand(mode: AncMode) = EarFeelI5CommandSet.ancCommand(mode)
    override fun ancDepthCommand(depth: AncDepth) = EarFeelI5CommandSet.ancDepthCommand(depth)
    override fun transLevelCommand(level: TransparencyLevel) =
        EarFeelI5CommandSet.transLevelCommand(level)

    override fun eqCommand(mode: EqPreset) = EarFeelI5CommandSet.eqCommand(mode)
    override fun gameModeCommand(enabled: Boolean) = EarFeelI5CommandSet.gameModeCommand(enabled)
    override val findLeftOn get() = EarFeelI5CommandSet.FIND_LEFT_ON
    override val findRightOn get() = EarFeelI5CommandSet.FIND_RIGHT_ON
    override val findAllOff get() = EarFeelI5CommandSet.FIND_ALL_OFF
    override val queryBattery get() = EarFeelI5CommandSet.QUERY_BATTERY
    override val queryAnc get() = EarFeelI5CommandSet.QUERY_ANC
    override val queryAncDepth get() = EarFeelI5CommandSet.QUERY_ANC_DEPTH
    override val queryTransLevel get() = EarFeelI5CommandSet.QUERY_TRANS_LEVEL
    override val queryEq get() = EarFeelI5CommandSet.QUERY_EQ
    override val queryGameMode get() = EarFeelI5CommandSet.QUERY_GAME_MODE
    override val statusQuerySequence get() = EarFeelI5CommandSet.STATUS_QUERY_SEQUENCE.toList()

    override fun parseResponse(data: ByteArray): List<DeviceResponse> =
        when (val r = EarFeelI5ResponseParser.parse(data)) {
            is EarFeelI5Response.Battery -> listOf(DeviceResponse.Battery(r.info))
            is EarFeelI5Response.Anc -> listOf(DeviceResponse.Anc(r.mode))
            is EarFeelI5Response.AncDepthChanged -> listOf(DeviceResponse.AncDepthChanged(r.depth))
            is EarFeelI5Response.TransparencyChanged -> listOf(DeviceResponse.TransparencyChanged(r.level))
            is EarFeelI5Response.Eq -> listOf(DeviceResponse.Eq(r.mode))
            is EarFeelI5Response.GameMode -> listOf(DeviceResponse.GameMode(r.enabled))
            is EarFeelI5Response.Unknown -> listOf(DeviceResponse.Unknown)
        }
}
