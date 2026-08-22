package com.dohex.hyperrose.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 一级页共用的内容安全区，统一处理顶栏和导航栏的占位。 */
internal object TopLevelPageDefaults {
    val ItemSpacing: Dp = 12.dp

    private val HorizontalPadding: Dp = 12.dp
    private val VerticalPadding: Dp = 12.dp

    @Composable
    fun contentPadding(
        scaffoldPadding: PaddingValues,
        outerPadding: PaddingValues,
    ): PaddingValues {
        val layoutDirection = LocalLayoutDirection.current
        return PaddingValues(
            top = scaffoldPadding.calculateTopPadding() + outerPadding.calculateTopPadding() + VerticalPadding,
            start =
                scaffoldPadding.calculateStartPadding(layoutDirection) +
                    outerPadding.calculateStartPadding(layoutDirection) +
                    HorizontalPadding,
            end =
                scaffoldPadding.calculateEndPadding(layoutDirection) +
                    outerPadding.calculateEndPadding(layoutDirection) +
                    HorizontalPadding,
            bottom = scaffoldPadding.calculateBottomPadding() + outerPadding.calculateBottomPadding() + VerticalPadding,
        )
    }
}
