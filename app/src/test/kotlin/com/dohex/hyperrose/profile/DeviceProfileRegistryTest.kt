package com.dohex.hyperrose.profile

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceProfileRegistryTest {
    @Test
    fun `matches EARFREE i5`() =
        assertNotNull(DeviceProfileRegistry.findByName("ROSE EARFREE i5"))

    @Test
    fun `matches EARFEEL i5 alias`() =
        assertNotNull(DeviceProfileRegistry.findByName("ROSE EARFEEL i5"))

    @Test
    fun `does not match unknown device`() =
        assertNull(DeviceProfileRegistry.findByName("Some Random Buds"))
}
