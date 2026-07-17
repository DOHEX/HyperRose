package com.dohex.hyperrose.profile.rose_cambrian

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.profile.DeviceCapabilities
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceProtocol
import com.dohex.hyperrose.profile.DeviceResponse
import com.dohex.hyperrose.profile.TransportSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object RoseCambrianProfile : DeviceProfile {
    override val id = "rose-cambrian"
    override val displayName = "ROSE CAMBRIAN"
    override val nameKeywords = listOf("ROSE CAMBRIAN")

    override val transport = TransportSpec.Rfcomm(
        dataChannelUuid = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"),
        sppChannelUuid = null,
    )

    override val protocol: DeviceProtocol = CambrianProtocol

    override val capabilities = DeviceCapabilities(
        supportedAncModes = AncMode.entries.toSet(),
        supportedAncDepths = emptySet(),
        supportedTransLevels = emptySet(),
        supportedEqPresets = setOf(EqPreset.POP, EqPreset.HIFI, EqPreset.ROCK),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = false,
    )

    override val hasFrameChecksum = false

    override val debugHexHint = "HEX 指令 (如 FF 00 02 09 01 12 AA)"
    override val debugQuickCommands = listOf(
        "查询全部状态" to RoseCambrianCommandSet.buildFrame(0x0F, RoseCambrianCommandSet.QUERY_PAYLOAD),
        "ANC 降噪" to RoseCambrianCommandSet.ancCommand(AncMode.NOISE_CANCEL),
        "ANC 普通" to RoseCambrianCommandSet.ancCommand(AncMode.NORMAL),
        "ANC 通透" to RoseCambrianCommandSet.ancCommand(AncMode.TRANSPARENT),
        "ANC 风噪" to RoseCambrianCommandSet.ancCommand(AncMode.WIND_NOISE),
        "游戏模式开" to RoseCambrianCommandSet.gameModeCommand(true),
        "游戏模式关" to RoseCambrianCommandSet.gameModeCommand(false),
        "EQ POP" to RoseCambrianCommandSet.eqCommand(EqPreset.POP),
        "EQ HiFi" to RoseCambrianCommandSet.eqCommand(EqPreset.HIFI),
        "EQ ROCK" to RoseCambrianCommandSet.eqCommand(EqPreset.ROCK),
    )
}

private object CambrianProtocol : DeviceProtocol {
    private val seq = AtomicInteger(0)

    override fun ancCommand(mode: AncMode): ByteArray {
        val payload = RoseCambrianCommandSet.ancCommand(mode).copyOfRange(3, 5)
        return RoseCambrianCommandSet.buildFrame(0x02, payload, seq.getAndIncrement())
    }

    override fun gameModeCommand(enabled: Boolean): ByteArray {
        val payload = RoseCambrianCommandSet.gameModeCommand(enabled).copyOfRange(3, 5)
        return RoseCambrianCommandSet.buildFrame(0x02, payload, seq.getAndIncrement())
    }

    override fun lowLatencyCommand(enabled: Boolean): ByteArray =
        gameModeCommand(enabled)

    override fun eqCommand(mode: EqPreset): ByteArray {
        val payload = RoseCambrianCommandSet.eqCommand(mode).copyOfRange(3, 5)
        return RoseCambrianCommandSet.buildFrame(0x02, payload, seq.getAndIncrement())
    }

    override val queryBattery get() = nextSeqQuery()
    override val queryAnc get() = nextSeqQuery()
    override val queryEq get() = nextSeqQuery()
    override val queryGameMode get() = nextSeqQuery()
    override val queryLowLatency get() = nextSeqQuery()

    override val statusQuerySequence: List<ByteArray>
        get() = listOf(
            RoseCambrianCommandSet.buildFrame(0x0F, RoseCambrianCommandSet.QUERY_PAYLOAD, seq.getAndIncrement()),
            RoseCambrianCommandSet.buildFrame(0x0F, RoseCambrianCommandSet.QUERY_PAYLOAD, seq.getAndIncrement()),
        )

    override fun parseResponse(data: ByteArray): List<DeviceResponse> =
        RoseCambrianResponseParser.parse(data)

    private fun nextSeqQuery(): ByteArray =
        RoseCambrianCommandSet.buildFrame(0x0F, RoseCambrianCommandSet.QUERY_PAYLOAD, seq.getAndIncrement())
}
