package com.dohex.hyperrose.profile

import com.dohex.hyperrose.model.DeviceVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCatalogTest {
    @Test
    fun `catalog resolves profile and visual metadata together`() {
        val i5 = DeviceCatalog.findById("rose-earfeel-i5")
        assertNotNull(i5)
        assertSame(EarFeelI5Profile, i5?.profile)
        assertEquals(DeviceVisuals.EARFEEL_I5, i5?.visuals)

        val i7 = DeviceCatalog.findByName("EarFeel i7")
        assertNotNull(i7)
        assertSame(EarFeelI7Profile, i7?.profile)
        assertNull(i7?.visuals)
    }

    @Test
    fun `catalog contains unique profile identities`() {
        val ids = DeviceCatalog.devices.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertTrue(ids.all(String::isNotBlank))
    }
}
