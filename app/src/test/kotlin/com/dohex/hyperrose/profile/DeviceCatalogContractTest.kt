package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.DeviceVisuals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCatalogContractTest {
    @Test
    fun `matches official i5 name`() =
        assertEquals("rose-earfeel-i5", DeviceCatalog.findByName("EarFeel i5")?.id)

    @Test
    fun `matches actual i5 Bluetooth name`() {
        assertEquals("rose-earfeel-i5", DeviceCatalog.findByName("ROSE EARFREE i5")?.id)
    }

    @Test
    fun `matches BudsFeel MK2`() =
        assertEquals(
            "rose-budsfeel-mk2",
            DeviceCatalog.findByName("ROSE BudsFeel MK2")?.id,
        )

    @Test
    fun `matches exact Ceramics U capture name`() {
        assertEquals("rose-ceramics-u", DeviceCatalog.findByName("ROSE Ceramics U")?.id)
    }

    @Test
    fun `does not match other Ceramics or Chinese nicknames`() {
        assertNull(DeviceCatalog.findByName("ROSE Ceramics X"))
        assertNull(DeviceCatalog.findByName("琉璃"))
    }

    @Test
    fun `does not match a BudsFeel family name without model`() {
        assertNull(DeviceCatalog.findByName("ROSE BudsFeel"))
        assertNull(DeviceCatalog.findByName("ROSE BudsFeel MK3"))
    }

    @Test
    fun `catalog exposes official canonical display names`() {
        assertEquals("EarFeel i5", DeviceCatalog.findById("rose-earfeel-i5")?.displayName)
        assertEquals("EarFeel i7", DeviceCatalog.findById("rose-earfeel-i7")?.displayName)
        assertEquals("ROSE BudsFeel MK2", DeviceCatalog.findById("rose-budsfeel-mk2")?.displayName)
        assertEquals("ROSE Ceramics U", DeviceCatalog.findById("rose-ceramics-u")?.displayName)
    }

    @Test
    fun `matches official i7 name and rejects legacy EARFREE name`() {
        assertEquals("rose-earfeel-i7", DeviceCatalog.findByName("EarFeel i7")?.id)
        assertNull(DeviceCatalog.findByName("ROSE EARFREE i7"))
        assertNull(DeviceCatalog.findByName("EARFREE-i7"))
    }

    @Test
    fun `does not match unknown device`() =
        assertNull(DeviceCatalog.findByName("Some Random Buds"))

    @Test
    fun `empty capabilities expose no device controls`() {
        assertTrue(DeviceCapabilities.NONE.supportedAncModes.isEmpty())
        assertEquals(false, DeviceCapabilities.NONE.hasGameMode)
        assertEquals(false, DeviceCapabilities.NONE.hasLowLatency)
        assertEquals(false, DeviceCapabilities.NONE.hasFindEarphone)
    }
    @Test
    fun `rejects duplicate profile identities`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCatalog.validateDevices(
                listOf(
                    DeviceDescriptor(EarFeelI5Profile, null),
                    DeviceDescriptor(EarFeelI5Profile, null),
                ),
            )
        }
    }

    @Test
    fun `all registered profiles satisfy the base contract`() {
        val profiles = DeviceCatalog.profiles
        assertEquals(profiles.size, profiles.map { it.id }.distinct().size)
        profiles.forEach { profile ->
            assertTrue(profile.id.isNotBlank())
            assertTrue(profile.displayName.isNotBlank())
            assertTrue(profile.nameKeywords.isNotEmpty())
            assertTrue(profile.protocol.statusQuerySequence.isNotEmpty())
        }
    }

    @Test
    fun `rejects visuals assigned to another profile`() {
        assertThrows(IllegalArgumentException::class.java) {
            DeviceCatalog.validateDevices(
                listOf(DeviceDescriptor(EarFeelI5Profile, DeviceVisuals.BUDSFEEL_MK2)),
            )
        }
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected ${expected.simpleName} but no exception was thrown")
        } catch (error: Throwable) {
            if (!expected.isInstance(error)) throw error
        }
    }
}
