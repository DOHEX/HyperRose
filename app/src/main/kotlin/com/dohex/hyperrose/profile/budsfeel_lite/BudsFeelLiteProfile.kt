package com.dohex.hyperrose.profile.budsfeel_lite

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.DeviceCapabilities
import com.dohex.hyperrose.profile.DeviceProfile
import com.dohex.hyperrose.profile.DeviceProtocol
import com.dohex.hyperrose.profile.DeviceResponse
import com.dohex.hyperrose.profile.TransportSpec
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

object BudsFeelLiteProfile : DeviceProfile {
    override val id = "rose-budsfeel-lite"
    override val displayName = "ROSE BudsFeel Lite"
    override val nameKeywords = listOf("ROSE BudsFeel Lite", "BudsFeel Lite")

    override val transport = TransportSpec.Rfcomm(
        dataChannelUuid = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"),
        sppChannelUuid = null,
    )

    override val protocol: DeviceProtocol = LiteProtocol

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
        "查询全部状态" to BudsFeelLiteCommandSet.QUERY_ALL,
        "ANC 降噪" to BudsFeelLiteCommandSet.ancCommand(AncMode.NOISE_CANCEL),
        "ANC 普通" to BudsFeelLiteCommandSet.ancCommand(AncMode.NORMAL),
        "游戏模式开" to BudsFeelLiteCommandSet.gameModeCommand(true),
        "游戏模式关" to BudsFeelLiteCommandSet.gameModeCommand(false),
    )
}

private object LiteProtocol : DeviceProtocol {
    private val seq = AtomicInteger(0)

    override fun ancCommand(mode: AncMode): ByteArray =
        BudsFeelLiteCommandSet.ancCommand(mode)

    override fun gameModeCommand(enabled: Boolean): ByteArray =
        BudsFeelLiteCommandSet.gameModeCommand(enabled)

    override fun lowLatencyCommand(enabled: Boolean): ByteArray =
        gameModeCommand(enabled)

    override val queryBattery get() = nextQuery()
    override val queryAnc get() = nextQuery()
    override val queryGameMode get() = nextQuery()
    override val queryLowLatency get() = nextQuery()

    override val statusQuerySequence: List<ByteArray>
        get() = (0..3).map { seqQuery(it) }

    override fun parseResponse(data: ByteArray): List<DeviceResponse> =
        BudsFeelLiteResponseParser.parse(data)

    private fun nextQuery(): ByteArray = seqQuery(seq.getAndIncrement())
    private fun seqQuery(s: Int): ByteArray {
        val frame = BudsFeelLiteCommandSet.QUERY_ALL
        return byteArrayOf(frame[0], s.toByte()) + frame.drop(2).toByteArray()
    }
}
