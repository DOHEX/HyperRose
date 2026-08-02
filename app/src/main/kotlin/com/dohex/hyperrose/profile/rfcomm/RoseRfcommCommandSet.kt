package com.dohex.hyperrose.profile.rfcomm

/** Shared ROSE RFCOMM command framing used by compatible device families. */
object RoseRfcommCommandSet {
    private const val HEADER_CMD: Byte = 0xFF.toByte()

    val QUERY_PAYLOAD: ByteArray = byteArrayOf(
        0xFA.toByte(), 0x01,
        0x07, 0x08, 0x09, 0x0C, 0x0D, 0x0E, 0x12,
        0x2A.toByte(), 0x2B.toByte(), 0x2C.toByte(), 0x2D.toByte(),
        0x2E.toByte(), 0x2F.toByte(),
        0x31, 0x32, 0x33,
        0x36, 0x37, 0x38, 0x39, 0x3A.toByte(), 0x3B.toByte(),
        0x3C.toByte(), 0x3D.toByte(), 0x3F.toByte(),
        0x45, 0x46, 0x49,
    )

    fun buildSetFrame(type: Int, value: Int, seq: Int = 0): ByteArray =
        buildFrame(0x02, byteArrayOf(type.toByte(), value.toByte()), seq)

    fun buildFrame(cmd: Int, payload: ByteArray, seq: Int = 0): ByteArray {
        val head = byteArrayOf(HEADER_CMD, seq.toByte(), cmd.toByte()) + payload
        val checksum = (head.sum() and 0xFF).toByte()
        return head + byteArrayOf(checksum, 0xAA.toByte())
    }
}
