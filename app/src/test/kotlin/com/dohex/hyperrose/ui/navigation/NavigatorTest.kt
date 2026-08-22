package com.dohex.hyperrose.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import top.yukonga.miuix.kmp.nav.core.navBackStackOf

class NavigatorTest {
    @Test
    fun `starts with one main root`() {
        val stack = navBackStackOf(Route.Main)
        val navigator = Navigator(stack)

        assertEquals(listOf(Route.Main), stack.toList())
    }

    @Test
    fun `replacing main discards the previous child history`() {
        val stack = navBackStackOf(Route.Main, Route.DeviceDetail("A"))
        val navigator = Navigator(stack)

        navigator.replaceAll(Route.Main)

        assertEquals(listOf(Route.Main), stack.toList())
    }

    @Test
    fun `push adds children and pops to an existing route`() {
        val stack = navBackStackOf(Route.Main)
        val navigator = Navigator(stack)
        val detail = Route.DeviceDetail("A")
        val debug = Route.BleDebug("A")

        navigator.push(detail)
        navigator.push(debug)
        navigator.push(detail)
        navigator.push(detail)

        assertEquals(listOf(Route.Main, detail), stack.toList())
    }

    @Test
    fun `back pops child routes but leaves main for Activity`() {
        val stack = navBackStackOf(Route.Main, Route.DeviceDetail("A"))
        val navigator = Navigator(stack)

        assertTrue(navigator.pop())
        assertEquals(listOf(Route.Main), stack.toList())
        assertFalse(navigator.pop())
        assertEquals(listOf(Route.Main), stack.toList())
    }

    @Test
    fun `main is the only top-level route`() {
        assertTrue(Route.Main.isTopLevel)
        assertFalse(Route.DeviceDetail("A").isTopLevel)
        assertFalse(Route.ThemeSettings.isTopLevel)
    }
}
