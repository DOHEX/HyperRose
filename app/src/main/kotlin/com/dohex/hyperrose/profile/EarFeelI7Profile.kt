package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommCommandSet
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommProtocol
import java.util.UUID

/** EarFeel i7 profile. Its confirmed RFCOMM wire protocol matches BudsFeel MK2. */
object EarFeelI7Profile : DeviceProfile {
    override val id = "rose-earfeel-i7"
    override val displayName = "EarFeel i7"
    override val nameKeywords = listOf("EarFeel i7")

    override val transport = TransportSpec.Rfcomm(
        dataChannelUuid = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"),
        sppChannelUuid = null,
    )

    override val protocol: DeviceProtocol = RoseRfcommProtocol(supportsAdvancedControls = false)

    override val capabilities = DeviceCapabilities(
        supportedAncModes = setOf(
            AncMode.NOISE_CANCEL,
            AncMode.WIND_NOISE,
            AncMode.NORMAL,
            AncMode.TRANSPARENT,
        ),
        supportedAncDepths = emptySet(),
        supportedTransLevels = emptySet(),
        supportedEqPresets = emptySet(),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = false,
    )

    override val debugHexHint = "HEX 指令 (如 FF 00 02 09 01 ... AA)"
    override val debugQuickCommands = listOf(
        "查询全部状态" to RoseRfcommCommandSet.buildFrame(
            0x1E,
            RoseRfcommCommandSet.QUERY_PAYLOAD,
        ),
        "ANC 降噪" to protocol.ancCommand(AncMode.NOISE_CANCEL),
        "ANC 普通" to protocol.ancCommand(AncMode.NORMAL),
        "游戏模式" to protocol.gameModeCommand(true),
    )
}
