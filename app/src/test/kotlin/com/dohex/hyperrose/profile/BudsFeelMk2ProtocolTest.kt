package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.budsfeel_mk2.BudsFeelMk2Profile
import org.junit.Assert.assertEquals
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
    fun `parseResponse recognizes ANC SET notification`() {
        // dd 00 02 09 01 ck aa
        val payload = byteArrayOf(0xDD.toByte(), 0x00, 0x02, 0x09, 0x01)
        val ck = (payload.sum() and 0xFF).toByte()
        val frame = payload + byteArrayOf(ck, 0xAA.toByte())

        val result = protocol.parseResponse(frame)
        assertTrue("Expected Anc, got $result", result is DeviceResponse.Anc)
        assertEquals(AncMode.NOISE_CANCEL, (result as DeviceResponse.Anc).mode)
    }

    @Test
    fun `parseResponse recognizes low latency notification`() {
        val payload = byteArrayOf(0xDD.toByte(), 0x00, 0x02, 0x0E, 0x01)
        val ck = (payload.sum() and 0xFF).toByte()
        val frame = payload + byteArrayOf(ck, 0xAA.toByte())

        val result = protocol.parseResponse(frame)
        assertTrue("Expected LowLatencyChanged, got $result", result is DeviceResponse.LowLatencyChanged)
        assertEquals(true, (result as DeviceResponse.LowLatencyChanged).enabled)
    }

    @Test
    fun `parseResponse returns Unknown for garbage`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02)
        assertTrue(protocol.parseResponse(garbage) is DeviceResponse.Unknown)
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
