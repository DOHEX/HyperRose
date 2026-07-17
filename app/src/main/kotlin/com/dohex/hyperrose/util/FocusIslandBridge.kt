package com.dohex.hyperrose.util

import android.os.Bundle
import com.dohex.hyperrose.model.asBatteryLevelOrNull
import com.xzakota.hyper.notification.focus.FocusNotification

object FocusIslandBridge {
    private const val TICKER_TEXT = "HyperRose"

    fun buildBatteryIslandExtras(
        leftLevel: Int,
        rightLevel: Int,
        caseLevel: Int,
        leftCharging: Boolean,
        rightCharging: Boolean,
        islandTimeoutSeconds: Int,
        deviceName: String = "ROSE CAMBRIAN",
    ): Bundle {
        val normalizedLeftLevel = leftLevel.asBatteryLevelOrNull()
        val normalizedRightLevel = rightLevel.asBatteryLevelOrNull()
        val normalizedCaseLevel = caseLevel.asBatteryLevelOrNull()

        val isMonoCase = normalizedLeftLevel == null && normalizedRightLevel == null && normalizedCaseLevel != null
        val leftText = if (isMonoCase) normalizedCaseLevel.toString() else (normalizedLeftLevel?.toString() ?: "-")
        val rightText = if (isMonoCase) "" else (normalizedRightLevel?.toString() ?: "-")
        val baseContent =
            buildBaseContent(
                leftLevel = if (isMonoCase) normalizedCaseLevel else normalizedLeftLevel,
                rightLevel = normalizedRightLevel,
                caseLevel = if (isMonoCase) null else normalizedCaseLevel,
                leftCharging = leftCharging,
                rightCharging = rightCharging,
            )
        val aodTitle =
            if (isMonoCase) "$normalizedCaseLevel%"
            else if (normalizedRightLevel == null) "$leftText%${if (leftCharging) "⚡" else ""}"
            else buildString {
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
                    if (rightText.isNotEmpty()) {
                        imageTextInfoRight {
                            type = 2
                            textInfo {
                                title = rightText
                                content = "%"
                            }
                        }
                    }
                }
                baseInfo {
                    type = 2
                    title = deviceName
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
