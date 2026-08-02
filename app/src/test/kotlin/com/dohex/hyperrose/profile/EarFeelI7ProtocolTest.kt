package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.profile.budsfeel_mk2.BudsFeelMk2Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class EarFeelI7ProtocolTest {
    private val profile = EarFeelI7Profile
    private val protocol = profile.protocol

    @Test
    fun `profile selects unauthenticated i7 RFCOMM`() {
        val transport = profile.transport as TransportSpec.Rfcomm
        assertEquals(UUID.fromString("0cf12d31-fac3-4553-bd80-d6832e7b3931"), transport.dataChannelUuid)
        assertEquals(null, transport.sppChannelUuid)
    }
    @Test
    fun `i7 and MK2 use independent protocol instances`() {
        assertTrue(EarFeelI7Profile.protocol !== BudsFeelMk2Profile.protocol)
    }

    @Test
    fun `profile exposes only confirmed i7 capabilities`() {
        assertEquals(
            setOf(
                AncMode.NOISE_CANCEL,
                AncMode.WIND_NOISE,
                AncMode.NORMAL,
                AncMode.TRANSPARENT,
            ),
            profile.capabilities.supportedAncModes,
        )
        assertTrue(profile.capabilities.hasGameMode)
        assertTrue(profile.capabilities.supportedAncDepths.isEmpty())
        assertTrue(profile.capabilities.supportedTransLevels.isEmpty())
        assertTrue(profile.capabilities.supportedEqPresets.isEmpty())
        assertTrue(!profile.capabilities.hasFindEarphone)
    }

    @Test
    fun `anc and game commands use documented i7 parameter values`() {
        val anc = protocol.ancCommand(AncMode.WIND_NOISE)
        assertEquals(0xFF.toByte(), anc[0])
        assertEquals(0x02.toByte(), anc[2])
        assertEquals(0x09.toByte(), anc[3])
        assertEquals(0x04.toByte(), anc[4])
        assertEquals(0xAA.toByte(), anc.last())

        val game = protocol.gameModeCommand(true)
        assertEquals(0x0E.toByte(), game[3])
        assertEquals(0x01.toByte(), game[4])
        assertEquals(0xAA.toByte(), game.last())
    }

    @Test
    fun `status response decodes i7 battery anc and game mode`() {
        val payload = byteArrayOf(
            0x01, 0x01, 0x00,
            0x02, 0x01, 0x03,
            0x02, 0x04, 0x08,
            0x05, 0x00, 0x11, 0x00, 0x12, 0x01,
            0x13, 0x03, 0x14, 0x08, 0x15, 0x00,
            0x02, 0x07, 0x00, 0x02, 0x08, 0x01, 0x02, 0x09, 0x01,
            0x04, 0x0C, 0x64, 0x64, 0x5B,
            0x04, 0x0D, 0x03, 0x00, 0x03,
            0x02, 0x0E, 0x01,
            0x02, 0x12, 0x01,
            0x02, 0x2A, 0x01, 0x02, 0x2B, 0x02, 0x02, 0x2C, 0x05,
            0x02, 0x2D, 0x03, 0x02, 0x2E, 0x02, 0x02, 0x2F, 0x04,
            0x02, 0x31, 0x01, 0x02, 0x32, 0x01, 0x02, 0x33, 0x00,
            0x05, 0x36, 0x01, 0x01, 0x00, 0x00,
            0x02, 0x45, 0x01, 0x02, 0x46, 0x00,
        )
        val head = byteArrayOf(0xDD.toByte(), 0x00, 0x15) + payload
        val frame = head + byteArrayOf((head.sum() and 0xFF).toByte(), 0xAA.toByte())

        val results = protocol.parseResponse(frame)
        assertTrue(results.any { it is DeviceResponse.Anc && it.mode == AncMode.NOISE_CANCEL })
        assertTrue(results.any { it is DeviceResponse.GameMode && it.enabled })
        val battery = results.filterIsInstance<DeviceResponse.Battery>().single().info
        assertEquals(100, battery.left?.level)
        assertEquals(100, battery.right?.level)
        assertEquals(91, battery.caseBattery)
    }

    @Test
    fun `invalid checksum produces no state`() {
        val head = byteArrayOf(0xDD.toByte(), 0x00, 0x02, 0x09, 0x01)
        val valid = head + byteArrayOf((head.sum() and 0xFF).toByte(), 0xAA.toByte())
        val invalid = valid.copyOf().also {
            it[it.lastIndex - 1] = (it[it.lastIndex - 1] + 1).toByte()
        }
        assertEquals(listOf(DeviceResponse.Unknown), protocol.parseResponse(invalid))
    }
}
