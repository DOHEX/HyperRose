package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CeramicsUProtocolTest {
    private fun bytes(value: String): ByteArray =
        value.split(" ").filter(String::isNotBlank).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `ceramics U profile matches documented device name`() {
        assertEquals("rose-ceramics-u", DeviceCatalog.findByName("ROSE Ceramics U")?.id)
    }

    @Test
    fun `ceramics U protocol encodes adaptive and extreme ANC`() {
        assertEquals(
            bytes("FF 00 02 09 05 0F AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.ADAPTIVE_NOISE_CANCEL).toList(),
        )
        assertEquals(
            bytes("FF 00 02 09 06 10 AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.EXTREME_NOISE_CANCEL).toList(),
        )
    }

    @Test
    fun `ceramics U protocol encodes documented controls`() {
        assertEquals(
            bytes("FF 00 02 2C 05 32 AA").toList(),
            RoseRfcommProtocol().ancDepthCommand(AncDepth.DEEP).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2D 05 33 AA").toList(),
            RoseRfcommProtocol().transLevelCommand(TransparencyLevel.DEEP).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2A 02 2D AA").toList(),
            RoseRfcommProtocol().eqCommand(EqPreset.ROCK).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2F 01 31 AA").toList(),
            RoseRfcommProtocol().findLeftOn.toList(),
        )
        assertEquals(
            bytes("FF 00 02 2F 02 32 AA").toList(),
            RoseRfcommProtocol().findRightOn.toList(),
        )
        assertEquals(
            bytes("FF 00 02 2F 04 34 AA").toList(),
            RoseRfcommProtocol().findAllOff.toList(),
        )
    }

    @Test
    fun `ceramics U protocol encodes gain prompt and touch commands`() {
        assertEquals(
            bytes("FF 00 02 45 02 48 AA").toList(),
            RoseRfcommProtocol().gainCommand(2).toList(),
        )
        assertEquals(
            bytes("FF 00 02 07 01 09 AA").toList(),
            RoseRfcommProtocol().promptToneLanguageCommand(1).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2E 05 34 AA").toList(),
            RoseRfcommProtocol().promptToneLevelCommand(5).toList(),
        )
        assertEquals(
            bytes("FF 00 03 01 11 04 18 AA").toList(),
            RoseRfcommProtocol().touchCommand(0x11, 0x04).toList(),
        )
    }

    @Test
    fun `ceramics U full status skips touch section and parses later TLVs`() {
        val fullStatus = bytes(
            "DD 01 15 01 01 03 02 01 03 08 04 02 05 00 " +
                "11 05 12 01 13 08 14 03 15 00 " +
                "02 07 00 02 09 01 04 0C 64 64 35 " +
                "04 0D 00 03 04 02 0E 00 02 12 01 02 2A 02 " +
                "02 2B 00 02 2C 01 02 2D 01 02 2E 01 02 31 00 " +
                "02 32 01 02 33 00 05 36 01 00 00 01 A5 AA",
        )

        val responses = CeramicsUProfile.protocol.parseResponse(fullStatus)
        val battery = responses.filterIsInstance<DeviceResponse.Battery>().single().info

        assertEquals(100, battery.left?.level)
        assertEquals(100, battery.right?.level)
        assertEquals(53, battery.caseBattery)
        assertTrue(responses.any { it == DeviceResponse.Anc(AncMode.NOISE_CANCEL) })
        assertTrue(responses.any { it == DeviceResponse.Eq(EqPreset.ROCK) })
        assertTrue(responses.any { it == DeviceResponse.AncDepthChanged(AncDepth.LIGHT) })
        assertTrue(
            responses.any {
                it == DeviceResponse.TransparencyChanged(TransparencyLevel.STANDARD)
            },
        )
        assertTrue(responses.any { it == DeviceResponse.GameMode(false) })
        assertTrue(responses.any { it == DeviceResponse.PromptToneLanguageChanged(0) })
        assertTrue(responses.any { it == DeviceResponse.PromptToneLevelChanged(1) })
    }

    @Test
    fun `active battery push is parsed immediately`() {
        val responses = CeramicsUProfile.protocol.parseResponse(
            bytes("DD 0D 04 0C 64 63 35 F6 AA"),
        )
        val battery = (responses.single() as DeviceResponse.Battery).info

        assertEquals(100, battery.left?.level)
        assertEquals(99, battery.right?.level)
        assertEquals(53, battery.caseBattery)
    }

    @Test
    fun `unknown TLV does not desynchronize following known TLV`() {
        val payload = bytes("01 01 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00") +
            bytes("02 55 7F 02 09 01")
        val frameWithoutChecksum = bytes("DD 01 15") + payload
        val checksum = (frameWithoutChecksum.sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
        val frame = frameWithoutChecksum + byteArrayOf(checksum, 0xAA.toByte())

        val responses = CeramicsUProfile.protocol.parseResponse(frame)

        assertTrue(responses.any { it == DeviceResponse.Anc(AncMode.NOISE_CANCEL) })
    }

    @Test
    fun `invalid frames return unknown`() {
        val invalidChecksum = bytes("DD 0D 04 0C 64 63 35 00 AA")
        val invalidTail = bytes("DD 0D 04 0C 64 63 35 F6 AB")

        assertTrue(CeramicsUProfile.protocol.parseResponse(invalidChecksum).single() is DeviceResponse.Unknown)
        assertTrue(CeramicsUProfile.protocol.parseResponse(invalidTail).single() is DeviceResponse.Unknown)
        assertTrue(CeramicsUProfile.protocol.parseResponse(bytes("DD 01 15 01")).single() is DeviceResponse.Unknown)
    }
}
