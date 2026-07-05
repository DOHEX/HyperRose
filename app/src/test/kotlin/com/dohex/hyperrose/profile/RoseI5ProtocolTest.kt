package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoseI5ProtocolTest {

    private val protocol = EarfreeI5Profile.protocol

    @Test
    fun `parseResponse returns Battery for known battery frame`() {
        // Header: 09 FF 00 00 01 01 01 11 00 00 01, data: left=100 right=100 charging=0,0 case=100, checksum=0x49
        val batteryFrame = byteArrayOf(
            0x09, 0xFF.toByte(), 0x00, 0x00, 0x01, 0x01, 0x01, 0x11, 0x00, 0x00, 0x01,
            0x64, 0x64, 0x00, 0x00, 0x64, 0x49
        )
        val result = protocol.parseResponse(batteryFrame)
        assertTrue("Expected Battery, got $result", result is DeviceResponse.Battery)
        val battery = result as DeviceResponse.Battery
        assertEquals(100, battery.info.left?.level)
    }

    @Test
    fun `parseResponse returns Anc for known ANC frame`() {
        // ANC response: header 8B 09 FF 00 00 01 06 02 0E, data[9]=1 for noise cancel
        val ancFrame = byteArrayOf(
            0x09, 0xFF.toByte(), 0x00, 0x00, 0x01, 0x06, 0x02, 0x0E,
            0x00, 0x01, 0x00, 0x00, 0x00, 0xFF.toByte()
        )
        val result = protocol.parseResponse(ancFrame)
        assertTrue("Expected Anc, got $result", result is DeviceResponse.Anc)
        assertEquals(AncMode.NOISE_CANCEL, (result as DeviceResponse.Anc).mode)
    }

    @Test
    fun `parseResponse returns Unknown for garbage data`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03)
        val result = protocol.parseResponse(garbage)
        assertTrue("Expected Unknown, got $result", result is DeviceResponse.Unknown)
    }

    @Test
    fun `commands return non-empty ByteArrays`() {
        assertTrue(protocol.ancCommand(AncMode.NOISE_CANCEL).isNotEmpty())
        assertTrue(protocol.ancDepthCommand(AncDepth.LIGHT).isNotEmpty())
        assertTrue(protocol.transLevelCommand(TransparencyLevel.COMFORTABLE).isNotEmpty())
        assertTrue(protocol.eqCommand(EqPreset.CLASSIC).isNotEmpty())
        assertTrue(protocol.gameModeCommand(true).isNotEmpty())
        assertTrue(protocol.findLeftOn.isNotEmpty())
        assertTrue(protocol.findRightOn.isNotEmpty())
        assertTrue(protocol.findAllOff.isNotEmpty())
    }

    @Test
    fun `query commands return non-empty ByteArrays`() {
        assertTrue(protocol.queryBattery.isNotEmpty())
        assertTrue(protocol.queryAnc.isNotEmpty())
        assertTrue(protocol.queryAncDepth.isNotEmpty())
        assertTrue(protocol.queryTransLevel.isNotEmpty())
        assertTrue(protocol.queryEq.isNotEmpty())
        assertTrue(protocol.queryGameMode.isNotEmpty())
    }

    @Test
    fun `statusQuerySequence contains 6 items`() {
        assertEquals(6, protocol.statusQuerySequence.size)
    }
}
