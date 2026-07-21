package com.dohex.hyperrose.profile

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileRegistryTest {
    @Test
    fun `matches EARFREE i5`() =
        assertNotNull(DeviceProfileRegistry.findByName("ROSE EARFREE i5"))

    @Test
    fun `matches EARFEEL i5 alias`() =
        assertNotNull(DeviceProfileRegistry.findByName("ROSE EARFEEL i5"))

    @Test
    fun `matches BudsFeel MK2`() =
        assertEquals(
            "rose-budsfeel-mk2",
            DeviceProfileRegistry.findByName("ROSE BudsFeel MK2")?.id,
        )

    @Test
    fun `does not match unknown device`() =
        assertNull(DeviceProfileRegistry.findByName("Some Random Buds"))

    @Test
    fun `empty capabilities expose no device controls`() {
        assertTrue(DeviceCapabilities.NONE.supportedAncModes.isEmpty())
        assertEquals(false, DeviceCapabilities.NONE.hasGameMode)
        assertEquals(false, DeviceCapabilities.NONE.hasLowLatency)
        assertEquals(false, DeviceCapabilities.NONE.hasFindEarphone)
    }
}
