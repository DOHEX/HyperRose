package com.dohex.hyperrose.profile.budsfeel_lite

import com.dohex.hyperrose.model.AncMode

object BudsFeelLiteCommandSet {
    private const val HEADER: Byte = 0xFF.toByte()

    fun ancCommand(mode: AncMode): ByteArray = buildFrame(
        cmd = 0x02,
        type = 0x09,
        value = when (mode) {
            AncMode.NOISE_CANCEL -> 0x01
            AncMode.NORMAL -> 0x02
            AncMode.TRANSPARENT -> 0x03
            AncMode.WIND_NOISE -> 0x04
        },
    )

    fun gameModeCommand(enabled: Boolean): ByteArray = buildFrame(
        cmd = 0x02,
        type = 0x0E,
        value = if (enabled) 0x01 else 0x00,
    )

    val QUERY_ALL: ByteArray = buildFrame(
        cmd = 0x1E,
        type = 0xFA.toInt(),
        value = 0x01,
    )

    private fun buildFrame(cmd: Int, type: Int, value: Int, seq: Int = 0): ByteArray {
        val payload = byteArrayOf(cmd.toByte(), type.toByte(), value.toByte())
        val head = byteArrayOf(HEADER, seq.toByte()) + payload
        val ck = (head.sum() and 0xFF).toByte()
        return head + byteArrayOf(ck, 0xAA.toByte())
    }
}
