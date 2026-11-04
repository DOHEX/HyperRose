package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommCommandSet
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommProtocol
import java.util.UUID

/**
 * 弱水时砂 锆势（原神联名「芙宁娜·不休独舞」版本）。
 *
 * 平面钛金属振膜 × HR+ 旗舰动圈、32 位 RISC-V + HiFi4 DSP、LHDC-V5 / LC3、宽频深海降噪、
 * 双设备连接、ROSELINK 3.0 APP。
 *
 * 与 BudsFeel MK2 / EarFeel i7 / Ceramics U 同属 ROSE RFCOMM 协议族：控制通道自定义服务
 * UUID 逐字节一致，帧格式 `FF <seq> <len> <cmd> <payload> <CK> AA` 由共享
 * [RoseRfcommCommandSet] 编码。实机已验证连接、降噪与通透切换生效。
 *
 * 该机型**没有自适应降噪**（琉璃 Ultra 独有的 `0x09` 值 `0x05`），能力集不开放该项；
 * 极限降噪（`0x09` 值 `0x06`）可用。协议层保留全部编码能力供同族其它型号使用。
 */
object GaoshiProfile : DeviceProfile {
    override val id = "rose-gaoshi"
    override val displayName = "ROSE 锆势 · 芙宁娜"
    /**
     * 实测蓝牙名：`Furina Endless Solo of Solitude`。
     * 联名款命名不带 `ROSE` 前缀，故不能靠厂牌前缀兜底；短名 `Furina` 用于容忍系统截断。
     * 与仓库既有约定一致：不把中文昵称写进关键词，避免误吞同族其它型号。
     */
    override val nameKeywords = listOf(
        "Furina Endless Solo of Solitude",
        "Furina",
    )

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
        "降噪" to protocol.ancCommand(AncMode.NOISE_CANCEL),
        "风噪" to protocol.ancCommand(AncMode.WIND_NOISE),
        "普通" to protocol.ancCommand(AncMode.NORMAL),
        "通透" to protocol.ancCommand(AncMode.TRANSPARENT),
        "极限降噪" to protocol.ancCommand(AncMode.EXTREME_NOISE_CANCEL),
        "HIFI" to protocol.eqCommand(EqPreset.HIFI),
        "游戏模式开" to protocol.gameModeCommand(true),
        "查找左耳" to protocol.findLeftOn,
        "查找右耳" to protocol.findRightOn,
        "停止查找" to protocol.findAllOff,
    )
}
