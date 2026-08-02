package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommCommandSet
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommProtocol
import java.util.UUID

object CeramicsUProfile : DeviceProfile {
    override val id = "rose-ceramics-u"
    override val displayName = "ROSE Ceramics U"
    override val nameKeywords = listOf("ROSE Ceramics U")

    override val transport = TransportSpec.Rfcomm(
        dataChannelUuid = UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"),
        sppChannelUuid = null,
    )

    override val protocol = RoseRfcommProtocol()

    override val capabilities = DeviceCapabilities(
        supportedAncModes = setOf(
            AncMode.NOISE_CANCEL,
            AncMode.WIND_NOISE,
            AncMode.NORMAL,
            AncMode.TRANSPARENT,
            AncMode.ADAPTIVE_NOISE_CANCEL,
            AncMode.EXTREME_NOISE_CANCEL,
        ),
        supportedAncDepths = setOf(AncDepth.LIGHT, AncDepth.MEDIUM, AncDepth.DEEP),
        supportedTransLevels = setOf(TransparencyLevel.STANDARD, TransparencyLevel.DEEP),
        supportedEqPresets = setOf(
            EqPreset.HIFI,
            EqPreset.POP,
            EqPreset.ROCK,
            EqPreset.ROSE_CLASSIC,
        ),
        hasGameMode = true,
        hasLowLatency = false,
        hasFindEarphone = true,
    )

    override val debugHexHint = "HEX 指令 (如 FF 00 02 09 01 12 AA)"
    override val debugQuickCommands = listOf(
        "查询全部状态" to RoseRfcommCommandSet.buildFrame(
            0x1E,
            RoseRfcommCommandSet.QUERY_PAYLOAD,
        ),
        "自适应降噪" to protocol.ancCommand(AncMode.ADAPTIVE_NOISE_CANCEL),
        "极限降噪" to protocol.ancCommand(AncMode.EXTREME_NOISE_CANCEL),
        "HIFI" to protocol.eqCommand(EqPreset.HIFI),
        "增益3" to protocol.gainCommand(2),
        "英文提示音" to protocol.promptToneLanguageCommand(1),
        "提示音5级" to protocol.promptToneLevelCommand(5),
        "触控键11动作04" to protocol.touchCommand(0x11, 0x04),
        "查找左耳" to protocol.findLeftOn,
        "查找右耳" to protocol.findRightOn,
        "停止查找" to protocol.findAllOff,
    )
}
