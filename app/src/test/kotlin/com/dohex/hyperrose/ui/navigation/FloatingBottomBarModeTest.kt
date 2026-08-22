package com.dohex.hyperrose.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class FloatingBottomBarModeTest {

    @Test
    fun `blur off forces none regardless of liquid glass`() {
        assertEquals(FloatingBottomBarMode.None, floatingBottomBarMode(blurEnabled = false, liquidGlassEnabled = true))
        assertEquals(FloatingBottomBarMode.None, floatingBottomBarMode(blurEnabled = false, liquidGlassEnabled = false))
    }

    @Test
    fun `blur on with liquid glass renders liquid glass`() {
        assertEquals(FloatingBottomBarMode.LiquidGlass, floatingBottomBarMode(blurEnabled = true, liquidGlassEnabled = true))
    }

    @Test
    fun `blur on without liquid glass falls back to blur`() {
        assertEquals(FloatingBottomBarMode.Blur, floatingBottomBarMode(blurEnabled = true, liquidGlassEnabled = false))
    }
}
