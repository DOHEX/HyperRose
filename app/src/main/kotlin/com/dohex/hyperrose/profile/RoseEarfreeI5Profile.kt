package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.earfree_i5.RoseCommandSet
import com.dohex.hyperrose.profile.earfree_i5.RoseGattSpec
import com.dohex.hyperrose.profile.earfree_i5.RoseGattTiming
import com.dohex.hyperrose.profile.earfree_i5.RoseResponse
import com.dohex.hyperrose.profile.earfree_i5.RoseResponseParser

object RoseEarfreeI5Profile : DeviceProfile {
    override val id = "rose-earfree-i5"
    override val displayName = "ROSESELSA EARFREE i5"
    override val nameKeywords = listOf("ROSE EARFREE", "ROSE EARFEEL")

    override val gattSpec = GattSpec(
        serviceUuid = RoseGattSpec.SERVICE_UUID,
        writeCharUuid = RoseGattSpec.WRITE_UUID,
        notifyCharUuid = RoseGattSpec.NOTIFY_UUID,
        cccdUuid = RoseGattSpec.CCCD_UUID,
    )

    override val timing = GattTiming(
        initialStatusQueryDelayMs = RoseGattTiming.INITIAL_STATUS_QUERY_DELAY_MS,
        statusQueryStepDelayMs = RoseGattTiming.STATUS_QUERY_STEP_DELAY_MS,
        batteryPollIntervalMs = RoseGattTiming.BATTERY_POLL_INTERVAL_MS,
    )

    override val protocol: DeviceProtocol = RoseI5Protocol

    override val capabilities = DeviceCapabilities(
        supportedAncModes = AncMode.entries.toSet(),
        supportedAncDepths = AncDepth.entries.toSet(),
        supportedTransLevels = TransparencyLevel.entries.toSet(),
        supportedEqPresets = EqPreset.entries.toSet(),
        hasGameMode = true,
        hasFindEarphone = true,
    )
}

/** Adapts existing RoseCommandSet + RoseResponseParser → DeviceProtocol. */
private object RoseI5Protocol : DeviceProtocol {
    override fun ancCommand(mode: AncMode) = RoseCommandSet.ancCommand(mode)
    override fun ancDepthCommand(depth: AncDepth) = RoseCommandSet.ancDepthCommand(depth)
    override fun transLevelCommand(level: TransparencyLevel) = RoseCommandSet.transLevelCommand(level)
    override fun eqCommand(mode: EqPreset) = RoseCommandSet.eqCommand(mode)
    override fun gameModeCommand(enabled: Boolean) = RoseCommandSet.gameModeCommand(enabled)
    override val findLeftOn get() = RoseCommandSet.FIND_LEFT_ON
    override val findRightOn get() = RoseCommandSet.FIND_RIGHT_ON
    override val findAllOff get() = RoseCommandSet.FIND_ALL_OFF
    override val queryBattery get() = RoseCommandSet.QUERY_BATTERY
    override val queryAnc get() = RoseCommandSet.QUERY_ANC
    override val queryAncDepth get() = RoseCommandSet.QUERY_ANC_DEPTH
    override val queryTransLevel get() = RoseCommandSet.QUERY_TRANS_LEVEL
    override val queryEq get() = RoseCommandSet.QUERY_EQ
    override val queryGameMode get() = RoseCommandSet.QUERY_GAME_MODE
    override val statusQuerySequence get() = RoseCommandSet.STATUS_QUERY_SEQUENCE.toList()

    override fun parseResponse(data: ByteArray): DeviceResponse =
        when (val r = RoseResponseParser.parse(data)) {
            is RoseResponse.Battery -> DeviceResponse.Battery(r.info)
            is RoseResponse.Anc -> DeviceResponse.Anc(r.mode)
            is RoseResponse.AncDepthChanged -> DeviceResponse.AncDepthChanged(r.depth)
            is RoseResponse.TransparencyChanged -> DeviceResponse.TransparencyChanged(r.level)
            is RoseResponse.Eq -> DeviceResponse.Eq(r.mode)
            is RoseResponse.GameMode -> DeviceResponse.GameMode(r.enabled)
            is RoseResponse.Unknown -> DeviceResponse.Unknown
        }
}
