package com.dohex.hyperrose.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.dohex.hyperrose.R

/**
 * A color variant reusable across device models.
 */
enum class EarphoneColor(
    val label: String,
    val displayColor: Color,
) {
    BLUE("蓝色", Color(0xFF4285F4)),
    GOLD("金色", Color(0xFFFFB300)),
    GRAY("灰色", Color(0xFF9E9E9E)),
    SILVER("银色", Color(0xFFC0C0C0)),
    BLACK("黑色", Color(0xFF333333)),
}

/**
 * Drawable resource triplet for a device-color combination.
 */
data class DeviceColorImages(
    @DrawableRes val caseRes: Int,
    @DrawableRes val leftRes: Int,
    @DrawableRes val rightRes: Int,
)

/**
 * A resolved color theme: an [EarphoneColor] paired with its device-specific images.
 * Delegation properties preserve a flat [label]/[displayColor]/[caseRes] API for UI.
 */
data class DeviceColorTheme(
    val color: EarphoneColor,
    val images: DeviceColorImages,
) {
    val label: String get() = color.label
    val displayColor: Color get() = color.displayColor
    val caseRes: Int @DrawableRes get() = images.caseRes
    val leftRes: Int @DrawableRes get() = images.leftRes
    val rightRes: Int @DrawableRes get() = images.rightRes
}

/**
 * Per-device-model registry of available colors mapped to drawable resources.
 *
 * To add a device: append an enum entry with its [deviceId] and a [colorMap] of
 * every supported [EarphoneColor] → [DeviceColorImages]. Drawables follow the
 * convention `earphone_{device}_{color}_{case|left|right}`.
 */
enum class DeviceColorProfile(
    val deviceId: String,
    private val colorMap: Map<EarphoneColor, DeviceColorImages>,
) {
    EARFREE_I5(
        "rose-earfree-i5",
        mapOf(
            EarphoneColor.BLUE to DeviceColorImages(
                R.drawable.earphone_i5_blue_case,
                R.drawable.earphone_i5_blue_left,
                R.drawable.earphone_i5_blue_right,
            ),
            EarphoneColor.GOLD to DeviceColorImages(
                R.drawable.earphone_i5_gold_case,
                R.drawable.earphone_i5_gold_left,
                R.drawable.earphone_i5_gold_right,
            ),
            EarphoneColor.GRAY to DeviceColorImages(
                R.drawable.earphone_i5_gray_case,
                R.drawable.earphone_i5_gray_left,
                R.drawable.earphone_i5_gray_right,
            ),
        ),
    ),
    CAMBRIAN(
        "rose-cambrian",
        mapOf(
            EarphoneColor.GRAY to DeviceColorImages(
                R.drawable.earphone_i5_gray_case,
                R.drawable.earphone_i5_gray_left,
                R.drawable.earphone_i5_gray_right,
            ),
            EarphoneColor.BLUE to DeviceColorImages(
                R.drawable.earphone_i5_blue_case,
                R.drawable.earphone_i5_blue_left,
                R.drawable.earphone_i5_blue_right,
            ),
            EarphoneColor.BLACK to DeviceColorImages(
                R.drawable.earphone_mk2_black_case,
                R.drawable.earphone_mk2_black_left,
                R.drawable.earphone_mk2_black_right,
            ),
        ),
    ),
    BUDSFEEL_MK2(
        "rose-budsfeel-mk2",
        mapOf(
            EarphoneColor.BLUE to DeviceColorImages(
                R.drawable.earphone_mk2_blue_case,
                R.drawable.earphone_mk2_blue_left,
                R.drawable.earphone_mk2_blue_right,
            ),
            EarphoneColor.SILVER to DeviceColorImages(
                R.drawable.earphone_mk2_silver_case,
                R.drawable.earphone_mk2_silver_left,
                R.drawable.earphone_mk2_silver_right,
            ),
            EarphoneColor.BLACK to DeviceColorImages(
                R.drawable.earphone_mk2_black_case,
                R.drawable.earphone_mk2_black_left,
                R.drawable.earphone_mk2_black_right,
            ),
        ),
    ),
    ;

    val availableColors: Set<EarphoneColor> get() = colorMap.keys

    fun themeFor(color: EarphoneColor): DeviceColorTheme? =
        colorMap[color]?.let { DeviceColorTheme(color, it) }

    fun defaultTheme(): DeviceColorTheme =
        themeFor(availableColors.first())!!

    companion object {
        val DEFAULT_PROFILE: DeviceColorProfile = EARFREE_I5

        fun forDevice(deviceId: String?): DeviceColorProfile? =
            entries.find { it.deviceId == deviceId }
    }
}
