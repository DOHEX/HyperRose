package com.dohex.hyperrose.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusIslandBridgeTest {
    @Test
    fun `uses connected device name when available`() {
        assertEquals(
            "ROSE BudsFeel MK2",
            FocusIslandBridge.resolveDeviceTitle("ROSE BudsFeel MK2", "EarFeel i5"),
        )
    }

    @Test
    fun `uses profile display name when bluetooth name is absent`() {
        assertEquals(
            "ROSE BudsFeel MK2",
            FocusIslandBridge.resolveDeviceTitle(null, "ROSE BudsFeel MK2"),
        )
    }

    @Test
    fun `uses generic title when device is unknown`() {
        assertEquals("HyperRose", FocusIslandBridge.resolveDeviceTitle(null, null))
    }
}
