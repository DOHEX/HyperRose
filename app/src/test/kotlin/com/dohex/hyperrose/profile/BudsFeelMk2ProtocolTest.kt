package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.budsfeel_mk2.BudsFeelMk2Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudsFeelMk2ProtocolTest {

    private val protocol = BudsFeelMk2Profile.protocol

    @Test
    fun `ancCommand produces valid MK2 frame`() {
        val frame = protocol.ancCommand(AncMode.NOISE_CANCEL)
        assertTrue(frame.isNotEmpty())
        assertEquals(0xFF.toByte(), frame[0])
        assertEquals(0x02.toByte(), frame[2])
        assertEquals(0xAA.toByte(), frame.last())
        val ckPos = frame.size - 2
        val expected = (frame.copyOfRange(0, ckPos).sum() and 0xFF).toByte()
        assertEquals(expected, frame[ckPos])
    }

    @Test
    fun `gameModeCommand uses type 0x0E`() {
        val frame = protocol.gameModeCommand(true)
        assertTrue(frame.isNotEmpty())
        // FF SEQ 02 0E 01 CK AA
        assertEquals(0xFF.toByte(), frame[0])
        assertEquals(0x02.toByte(), frame[2])
        assertEquals(0x0E.toByte(), frame[3])
        assertEquals(0x01.toByte(), frame[4])
        assertEquals(0xAA.toByte(), frame.last())
    }

    @Test
    fun `lowLatencyCommand delegates to gameModeCommand`() {
        val lowLat = protocol.lowLatencyCommand(true)
        val game = protocol.gameModeCommand(true)
        // Same type (0x0E) — seq may differ, so compare payload bytes
        assertEquals(0x0E.toByte(), lowLat[3])
        assertEquals(0x0E.toByte(), game[3])
        assertEquals(0x01.toByte(), lowLat[4])
    }

    @Test
    fun `parseResponse recognizes ANC unsolicited notification`() {
        // dd 00 02 09 01 ck aa
        val payload = byteArrayOf(0xDD.toByte(), 0x00, 0x02, 0x09, 0x01)
        val ck = (payload.sum() and 0xFF).toByte()
        val frame = payload + byteArrayOf(ck, 0xAA.toByte())

        val results = protocol.parseResponse(frame)
        assertEquals(1, results.size)
        assertTrue("Expected Anc, got ${results[0]}", results[0] is DeviceResponse.Anc)
        assertEquals(AncMode.NOISE_CANCEL, (results[0] as DeviceResponse.Anc).mode)
    }

    @Test
    fun `parseResponse recognizes game mode unsolicited as GameMode`() {
        // dd 00 02 0E 01 ck aa
        val payload = byteArrayOf(0xDD.toByte(), 0x00, 0x02, 0x0E, 0x01)
        val ck = (payload.sum() and 0xFF).toByte()
        val frame = payload + byteArrayOf(ck, 0xAA.toByte())

        val results = protocol.parseResponse(frame)
        assertEquals(1, results.size)
        assertTrue("Expected GameMode, got ${results[0]}", results[0] is DeviceResponse.GameMode)
        assertEquals(true, (results[0] as DeviceResponse.GameMode).enabled)
    }

    @Test
    fun `parseResponse returns Unknown list for garbage`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02)
        val results = protocol.parseResponse(garbage)
        assertEquals(1, results.size)
        assertTrue(results[0] is DeviceResponse.Unknown)
    }

    @Test
    fun `parseResponse extracts battery from status response`() {
        // Build a minimal status response containing battery: 04 0C 64 64 FF
        // DD 00 15 [preamble] 04 0C 64 64 FF [trailer] CK AA
        val inner = byteArrayOf(0x04, 0x0C, 0x64, 0x64, 0xFF.toByte())
        val preamble = byteArrayOf(0x01, 0x01, 0x01)
        val head = byteArrayOf(0xDD.toByte(), 0x00, 0x15)
        val data = head + preamble + inner
        val ck = (data.sum() and 0xFF).toByte()
        val frame = data + byteArrayOf(ck, 0xAA.toByte())

        val results = protocol.parseResponse(frame)
        val battery = results.filterIsInstance<DeviceResponse.Battery>()
        assertEquals("Expected one Battery result in $results", 1, battery.size)
        assertEquals(100, battery[0].info.left?.level)
        assertEquals(100, battery[0].info.right?.level)
        assertNull(battery[0].info.caseBattery) // 0xFF = no case
    }

    @Test
    fun `statusQuerySequence contains 4 capability queries`() {
        assertEquals(4, protocol.statusQuerySequence.size)
    }

    @Test
    fun `unsupported methods throw`() {
        assertThrows(UnsupportedOperationException::class.java) {
            protocol.ancDepthCommand(com.dohex.hyperrose.model.AncDepth.LIGHT)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            protocol.eqCommand(com.dohex.hyperrose.model.EqPreset.CLASSIC)
        }
        assertThrows(UnsupportedOperationException::class.java) {
            protocol.findLeftOn
        }
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${expected.simpleName} but no exception was thrown")
        } catch (e: Throwable) {
            if (!expected.isInstance(e)) throw e
        }
    }
}
