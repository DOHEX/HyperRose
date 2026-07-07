package com.dohex.hyperrose.profile.budsfeel_mk2

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.DeviceCapabilities
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceProtocol
import com.dohex.hyperrose.profile.DeviceResponse
import com.dohex.hyperrose.profile.TransportSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object BudsFeelMk2Profile : DeviceProfile {
    override val id = "rose-budsfeel-mk2"
    override val displayName = "ROSE BudsFeel MK2"
    override val nameKeywords = listOf("ROSE BudsFeel", "BudsFeel MK2")

    override val transport = TransportSpec.Rfcomm(
        dataChannelUuid = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"),
        sppChannelUuid = null,  // try without auth first
    )

    override val protocol: DeviceProtocol = Mk2Protocol

    override val capabilities = DeviceCapabilities(
        supportedAncModes = AncMode.entries.toSet(),
        supportedAncDepths = emptySet(),
        supportedTransLevels = emptySet(),
        supportedEqPresets = emptySet(),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = false,
    )

    override val debugHexHint = "HEX 指令 (如 FF 00 02 09 01 12 AA)"
    override val debugQuickCommands = listOf(
        "查询全部状态" to BudsFeelMk2CommandSet.buildFrame(
            0x1E, BudsFeelMk2CommandSet.QUERY_PAYLOAD
        ),
        "ANC 降噪" to BudsFeelMk2CommandSet.ancCommand(AncMode.NOISE_CANCEL),
        "ANC 普通" to BudsFeelMk2CommandSet.ancCommand(AncMode.NORMAL),
        "游戏模式" to BudsFeelMk2CommandSet.gameModeCommand(true),
    )
}

private object Mk2Protocol : DeviceProtocol {
    private val seq = AtomicInteger(0)

    override fun ancCommand(mode: AncMode): ByteArray {
        val payload = BudsFeelMk2CommandSet.ancCommand(mode).copyOfRange(3, 5)
        return BudsFeelMk2CommandSet.buildFrame(0x02, payload, seq.getAndIncrement())
    }

    override fun gameModeCommand(enabled: Boolean): ByteArray {
        val payload = BudsFeelMk2CommandSet.gameModeCommand(enabled).copyOfRange(3, 5)
        return BudsFeelMk2CommandSet.buildFrame(0x02, payload, seq.getAndIncrement())
    }

    override fun lowLatencyCommand(enabled: Boolean): ByteArray = gameModeCommand(enabled)

    // All individual queries use the capability query (MK2 has no individual query commands)
    override val queryBattery get() = nextSeqQuery()
    override val queryAnc get() = nextSeqQuery()
    override val queryGameMode get() = nextSeqQuery()
    override val queryLowLatency get() = nextSeqQuery()

    override val statusQuerySequence: List<ByteArray>
        get() = (0..3).map { s ->
            BudsFeelMk2CommandSet.buildFrame(0x1E, BudsFeelMk2CommandSet.QUERY_PAYLOAD, s)
        }

    override fun parseResponse(data: ByteArray): List<DeviceResponse> =
        BudsFeelMk2ResponseParser.parse(data)

    private fun nextSeqQuery(): ByteArray =
        BudsFeelMk2CommandSet.buildFrame(
            0x1E,
            BudsFeelMk2CommandSet.QUERY_PAYLOAD,
            seq.getAndIncrement()
        )
}
