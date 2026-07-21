package com.dohex.hyperrose.util

import android.os.Bundle
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.xzakota.hyper.notification.focus.FocusNotification

object FocusIslandBridge {
    private const val TICKER_TEXT = "HyperRose"

    fun resolveDeviceTitle(
        deviceName: String?,
        profileDisplayName: String?,
    ): String = deviceName ?: profileDisplayName ?: TICKER_TEXT

    fun buildBatteryIslandExtras(
        deviceTitle: String,
        leftLevel: Int,
        rightLevel: Int,
        caseLevel: Int,
        leftCharging: Boolean,
        rightCharging: Boolean,
        islandTimeoutSeconds: Int,
    ): Bundle {
        val normalizedLeftLevel = leftLevel.asBatteryLevelOrNull()
        val normalizedRightLevel = rightLevel.asBatteryLevelOrNull()
        val normalizedCaseLevel = caseLevel.asBatteryLevelOrNull()
        val leftText = normalizedLeftLevel?.toString() ?: "-"
        val rightText = normalizedRightLevel?.toString() ?: "-"
        val baseContent =
            buildBaseContent(
                leftLevel = normalizedLeftLevel,
                rightLevel = normalizedRightLevel,
                caseLevel = normalizedCaseLevel,
                leftCharging = leftCharging,
                rightCharging = rightCharging,
            )
        val aodTitle =
            buildString {
                append("L$leftText%")
                if (leftCharging) append("⚡")
                append(" R$rightText%")
                if (rightCharging) append("⚡")
            }
        return FocusNotification.buildV3 {
            enableFloat = false
            islandFirstFloat = true
            ticker = TICKER_TEXT
            updatable = true
            isShowNotification = true
            // aodPic = "miui.focus.pic_aod"
            this.aodTitle = aodTitle
            island {
                islandProperty = 1
                islandTimeout = islandTimeoutSeconds
                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        textInfo {
                            title = leftText
                            content = "%"
                        }
                    }
                    imageTextInfoRight {
                        type = 2
                        textInfo {
                            title = rightText
                            content = "%"
                        }
                    }
                }
                baseInfo {
                    type = 2
                    title = deviceTitle
                    content = baseContent
                }
            }
        }
    }

    private fun buildBaseContent(
        leftLevel: Int?,
        rightLevel: Int?,
        caseLevel: Int?,
        leftCharging: Boolean,
        rightCharging: Boolean,
    ): String {
        val segments = mutableListOf<String>()
        if (leftLevel != null) {
            segments += "L ${formatEarBattery(leftLevel, leftCharging)}"
        }
        if (rightLevel != null) {
            segments += "R ${formatEarBattery(rightLevel, rightCharging)}"
        }
        if (caseLevel != null) {
            segments += "C $caseLevel%"
        }
        return if (segments.isEmpty()) "电量未知" else segments.joinToString(" | ")
    }

    private fun formatEarBattery(
        level: Int,
        charging: Boolean,
    ): String = if (charging) "$level% ⚡" else "$level%"
}
