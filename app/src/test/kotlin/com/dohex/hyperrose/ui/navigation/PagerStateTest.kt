package com.dohex.hyperrose.ui.navigation

import androidx.compose.foundation.pager.PagerState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PagerStateTest {

    private fun pagerState(currentPage: Int = 0): PagerState =
        object : PagerState(
            currentPage = currentPage,
            currentPageOffsetFraction = 0f,
        ) {
            override val pageCount: Int = 3
        }

    @Test
    fun `syncPage follows user swipe when not navigating`() = runTest(UnconfinedTestDispatcher()) {
        val pager = pagerState(currentPage = 2)
        val state = MainPagerState(pager, backgroundScope)

        state.syncPage()

        assertEquals(2, state.selectedPage)
        assertFalse(state.isNavigating)
    }

    @Test
    fun `syncPage does not override during navigation`() = runTest(UnconfinedTestDispatcher()) {
        val pager = pagerState(currentPage = 0)
        val state = MainPagerState(pager, backgroundScope)

        state.animateToPage(2)
        // 导航进行中：syncPage 不得把 selectedPage 拉回 pager 位置。
        state.syncPage()

        assertEquals(2, state.selectedPage)
        assertTrue(state.isNavigating)
    }

    @Test
    fun `animateToPage to current page is a no-op`() = runTest(UnconfinedTestDispatcher()) {
        val pager = pagerState(currentPage = 1)
        val state = MainPagerState(pager, backgroundScope)

        state.animateToPage(1)

        assertFalse(state.isNavigating)
        assertEquals(1, state.selectedPage)
    }

    @Test
    fun `animateToPage selects target immediately`() = runTest(UnconfinedTestDispatcher()) {
        val pager = pagerState(currentPage = 0)
        val state = MainPagerState(pager, backgroundScope)

        state.animateToPage(2)

        assertEquals(2, state.selectedPage)
        assertTrue(state.isNavigating)
    }
}
