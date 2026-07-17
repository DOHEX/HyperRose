package com.dohex.hyperrose.profile.rose_cambrian

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import java.util.concurrent.atomic.AtomicInteger

object RoseCambrianCommandSet {
    private val seq = AtomicInteger(0)

    val QUERY_PAYLOAD = byteArrayOf(0x01, 0x00)

    fun ancCommand(mode: AncMode): ByteArray = when (mode) {
        AncMode.NOISE_CANCEL -> buildFrame(0x02, byteArrayOf(0x09, 0x01))
        AncMode.NORMAL -> buildFrame(0x02, byteArrayOf(0x09, 0x02))
        AncMode.TRANSPARENT -> buildFrame(0x02, byteArrayOf(0x09, 0x03))
        AncMode.WIND_NOISE -> buildFrame(0x02, byteArrayOf(0x09, 0x04))
    }

    fun gameModeCommand(enabled: Boolean): ByteArray =
        buildFrame(0x02, byteArrayOf(0x0E, if (enabled) 0x01 else 0x00))

    fun eqCommand(mode: EqPreset): ByteArray = when (mode) {
        EqPreset.HIFI -> buildFrame(0x02, byteArrayOf(0x2A, 0x00))
        EqPreset.POP -> buildFrame(0x02, byteArrayOf(0x2A, 0x01))
        EqPreset.ROCK -> buildFrame(0x02, byteArrayOf(0x2A, 0x02))
        else -> buildFrame(0x02, byteArrayOf(0x2A, 0x00))
    }

    fun buildFrame(cmd: Int, payload: ByteArray, sequence: Int = seq.getAndIncrement()): ByteArray {
        val seqByte = (sequence and 0xFF).toByte()
        val header = byteArrayOf(
            0xDD.toByte(), 0x00, cmd.toByte(), 0x00, 0x00, seqByte,
        )
        val length = (payload.size + 3).toByte()
        header[1] = length
        val full = header + payload + byteArrayOf(0xAA.toByte())
        return full
    }
}
