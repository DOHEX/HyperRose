package com.dohex.hyperrose.model

import androidx.annotation.DrawableRes
import com.dohex.hyperrose.R

enum class EarphoneColorTheme(
    val label: String,
    @DrawableRes val caseRes: Int,
    @DrawableRes val leftRes: Int = 0,
    @DrawableRes val rightRes: Int = 0,
) {
    BLUE(
        "蓝色",
        R.drawable.earphone_i5_blue_case,
        R.drawable.earphone_i5_blue_left,
        R.drawable.earphone_i5_blue_right
    ),
    GOLD(
        "金色",
        R.drawable.earphone_i5_gold_case,
        R.drawable.earphone_i5_gold_left,
        R.drawable.earphone_i5_gold_right
    ),
    GRAY(
        "灰色",
        R.drawable.earphone_i5_gray_case,
        R.drawable.earphone_i5_gray_left,
        R.drawable.earphone_i5_gray_right
    ),
    ;

    companion object {
        val DEFAULT = BLUE
    }
}
