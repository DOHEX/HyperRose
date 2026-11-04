package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.AncDepth
import com.dohex.hyperrose.model.AncMode
import com.dohex.hyperrose.model.EqPreset
import com.dohex.hyperrose.model.TransparencyLevel
import com.dohex.hyperrose.profile.rfcomm.RoseRfcommProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GaoshiProfileTest {
    private fun bytes(value: String): ByteArray =
        value.split(" ").filter(String::isNotBlank).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun `gaoshi profile matches the measured bluetooth name`() {
        assertEquals(
            "rose-gaoshi",
            DeviceCatalog.findByName("Furina Endless Solo of Solitude")?.id,
        )
        // 系统中被截断时仍要认出来
        assertEquals("rose-gaoshi", DeviceCatalog.findByName("Furina")?.id)
        assertEquals("rose-gaoshi", DeviceCatalog.findByName("Furina Endless Solo")?.id)
    }

    @Test
    fun `gaoshi does not swallow sibling models or chinese nicknames`() {
        assertEquals("rose-ceramics-u", DeviceCatalog.findByName("ROSE Ceramics U")?.id)
        assertNull(DeviceCatalog.findByName("ROSE Ceramics X"))
        // 与仓库既有约定一致：中文昵称不参与匹配
        assertNull(DeviceCatalog.findByName("锆势"))
    }

    @Test
    fun `gaoshi uses the shared ROSE rfcomm control channel`() {
        val transport = GaoshiProfile.transport
        assertEquals(
            "0cf12d31-fac3-4553-bd80-d6832e7b3931",
            transport.dataChannelUuid.toString(),
        )
        assertNull(transport.sppChannelUuid)
        assertNull(GaoshiProfile.gattTiming)
    }

    @Test
    fun `gaoshi status query matches the captured ROSE query frame`() {
        assertEquals(
            bytes(
                "FF 00 1E FA 01 07 08 09 0C 0D 0E 12 2A 2B 2C 2D 2E 2F 31 32 33 " +
                    "36 37 38 39 3A 3B 3C 3D 3F 45 46 49 E9 AA",
            ).toList(),
            GaoshiProfile.protocol.statusQuerySequence.first().toList(),
        )
        assertEquals(4, GaoshiProfile.protocol.statusQuerySequence.size)
    }

    @Test
    fun `gaoshi encodes anc modes with family values`() {
        assertEquals(
            bytes("FF 00 02 09 01 0B AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.NOISE_CANCEL).toList(),
        )
        assertEquals(
            bytes("FF 00 02 09 02 0C AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.NORMAL).toList(),
        )
        assertEquals(
            bytes("FF 00 02 09 03 0D AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.TRANSPARENT).toList(),
        )
        assertEquals(
            bytes("FF 00 02 09 04 0E AA").toList(),
            RoseRfcommProtocol().ancCommand(AncMode.WIND_NOISE).toList(),
        )
    }

    @Test
    fun `gaoshi does not expose adaptive or extreme anc`() {
        // 锆势没有自适应降噪与极限降噪（琉璃 Ultra 独有的 0x09 值 0x05 / 0x06）
        val modes = GaoshiProfile.capabilities.supportedAncModes

        assertFalse(modes.contains(AncMode.ADAPTIVE_NOISE_CANCEL))
        assertFalse(modes.contains(AncMode.EXTREME_NOISE_CANCEL))
        assertEquals(4, modes.size)
    }

    @Test
    fun `gaoshi encodes advanced controls with family values`() {
        assertEquals(
            bytes("FF 00 02 2C 01 2E AA").toList(),
            RoseRfcommProtocol().ancDepthCommand(AncDepth.LIGHT).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2D 05 33 AA").toList(),
            RoseRfcommProtocol().transLevelCommand(TransparencyLevel.DEEP).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2A 03 2E AA").toList(),
            RoseRfcommProtocol().eqCommand(EqPreset.ROSE_CLASSIC).toList(),
        )
        assertEquals(
            bytes("FF 00 02 0E 00 0F AA").toList(),
            RoseRfcommProtocol().gameModeCommand(false).toList(),
        )
        assertEquals(
            bytes("FF 00 02 2F 04 34 AA").toList(),
            RoseRfcommProtocol().findAllOff.toList(),
        )
        assertEquals(
            bytes("FF 00 02 07 01 09 AA").toList(),
            RoseRfcommProtocol().promptToneLanguageCommand(1).toList(),
        )
    }

    @Test
    fun `gaoshi reuses the shared response parser`() {
        val responses = GaoshiProfile.protocol.parseResponse(bytes("DD 0D 04 0C 64 63 35 F6 AA"))
        val battery = (responses.single() as DeviceResponse.Battery).info

        assertEquals(100, battery.left?.level)
        assertEquals(99, battery.right?.level)
        assertEquals(53, battery.caseBattery)
    }

    @Test
    fun `gaoshi capabilities expose anc eq game mode and find`() {
        val capabilities = GaoshiProfile.capabilities
        assertTrue(capabilities.supportedAncModes.contains(AncMode.NOISE_CANCEL))
        assertTrue(capabilities.supportedAncModes.contains(AncMode.TRANSPARENT))
        assertTrue(capabilities.supportedEqPresets.contains(EqPreset.HIFI))
        assertTrue(capabilities.hasGameMode)
        assertTrue(capabilities.hasFindEarphone)
    }

    @Test
    fun `gaoshi debug presets are wire-valid frames`() {
        assertTrue(GaoshiProfile.debugQuickCommands.size >= 8)
        GaoshiProfile.debugQuickCommands.forEach { (label, frame) ->
            assertTrue("empty frame for $label", frame.size >= 6)
            assertEquals(0xFF.toByte(), frame.first())
            assertEquals(0xAA.toByte(), frame.last())
            val checksum = (frame.dropLast(2).sumOf { it.toInt() and 0xFF } and 0xFF).toByte()
            assertEquals("bad checksum for $label", checksum, frame[frame.size - 2])
        }
    }
}
