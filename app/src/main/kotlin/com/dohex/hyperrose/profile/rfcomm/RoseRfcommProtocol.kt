package com.dohex.hyperrose.profile.rfcomm

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.DeviceProtocol
import com.dohex.hyperrose.profile.DeviceResponse
import java.util.concurrent.atomic.AtomicInteger

/** Device-independent protocol adapter for the confirmed ROSE RFCOMM wire format. */
class RoseRfcommProtocol(
    private val supportsAdvancedControls: Boolean = true,
) : DeviceProtocol {
    private val sequence = AtomicInteger(0)

    override fun ancCommand(mode: AncMode): ByteArray =
        RoseRfcommCommandSet.buildSetFrame(
            0x09,
            when (mode) {
                AncMode.NOISE_CANCEL -> 0x01
                AncMode.NORMAL -> 0x02
                AncMode.TRANSPARENT -> 0x03
                AncMode.WIND_NOISE -> 0x04
                AncMode.ADAPTIVE_NOISE_CANCEL -> 0x05
                AncMode.EXTREME_NOISE_CANCEL -> 0x06
            },
            sequence.getAndIncrement(),
        )

    override fun ancDepthCommand(depth: AncDepth): ByteArray {
        requireAdvancedControls()
        return setValue(
            0x2C,
            when (depth) {
                AncDepth.LIGHT -> 0x01
                AncDepth.MEDIUM -> 0x03
                AncDepth.DEEP -> 0x05
            },
        )
    }

    override fun transLevelCommand(level: TransparencyLevel): ByteArray {
        requireAdvancedControls()
        return setValue(
            0x2D,
            when (level) {
                TransparencyLevel.STANDARD -> 0x01
                TransparencyLevel.DEEP -> 0x05
                else -> throw UnsupportedOperationException(
                    "Transparency level not supported by ROSE RFCOMM",
                )
            },
        )
    }

    override fun eqCommand(mode: EqPreset): ByteArray {
        requireAdvancedControls()
        return setValue(
            0x2A,
            when (mode) {
                EqPreset.HIFI -> 0x00
                EqPreset.POP -> 0x01
                EqPreset.ROCK -> 0x02
                EqPreset.ROSE_CLASSIC -> 0x03
                else -> throw UnsupportedOperationException("EQ preset not supported by ROSE RFCOMM")
            },
        )
    }

    override fun gameModeCommand(enabled: Boolean): ByteArray =
        setValue(0x0E, if (enabled) 0x01 else 0x00)

    override fun lowLatencyCommand(enabled: Boolean): ByteArray = gameModeCommand(enabled)

    override val findLeftOn: ByteArray
        get() {
            requireAdvancedControls()
            return setValue(0x2F, 0x01)
        }

    override val findRightOn: ByteArray
        get() {
            requireAdvancedControls()
            return setValue(0x2F, 0x02)
        }

    override val findAllOff: ByteArray
        get() {
            requireAdvancedControls()
            return setValue(0x2F, 0x04)
        }

    override fun gainCommand(level: Int): ByteArray {
        requireAdvancedControls()
        return setValue(0x45, requireByte(level))
    }

    override fun promptToneLanguageCommand(language: Int): ByteArray {
        requireAdvancedControls()
        return setValue(0x07, requireByte(language))
    }

    override fun promptToneLevelCommand(level: Int): ByteArray {
        requireAdvancedControls()
        return setValue(0x2E, requireByte(level))
    }

    override fun touchCommand(key: Int, action: Int): ByteArray {
        requireAdvancedControls()
        requireByte(key)
        requireByte(action)
        return RoseRfcommCommandSet.buildFrame(
            0x03,
            byteArrayOf(0x01, key.toByte(), action.toByte()),
            sequence.getAndIncrement(),
        )
    }

    override val queryBattery: ByteArray get() = nextStatusQuery()
    override val queryAnc: ByteArray get() = nextStatusQuery()
    override val queryGameMode: ByteArray get() = nextStatusQuery()
    override val queryLowLatency: ByteArray get() = nextStatusQuery()

    override val statusQuerySequence: List<ByteArray>
        get() = (0..3).map { sequenceNumber ->
            RoseRfcommCommandSet.buildFrame(
                0x1E,
                RoseRfcommCommandSet.QUERY_PAYLOAD,
                sequenceNumber,
            )
        }

    override fun parseResponse(data: ByteArray): List<DeviceResponse> =
        RoseRfcommResponseParser.parse(data)

    private fun setValue(type: Int, value: Int): ByteArray =
        RoseRfcommCommandSet.buildSetFrame(type, value, sequence.getAndIncrement())

    private fun requireAdvancedControls() {
        if (!supportsAdvancedControls) {
            throw UnsupportedOperationException(
                "Advanced controls are not supported by this ROSE profile",
            )
        }
    }

    private fun requireByte(value: Int): Int {
        require(value in 0..0xFF) { "Value must fit in one unsigned byte: $value" }
        return value
    }

    private fun nextStatusQuery(): ByteArray =
        RoseRfcommCommandSet.buildFrame(
            0x1E,
            RoseRfcommCommandSet.QUERY_PAYLOAD,
            sequence.getAndIncrement(),
        )
}
