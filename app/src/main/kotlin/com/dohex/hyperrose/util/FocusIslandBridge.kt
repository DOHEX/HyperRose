package com.dohex.hyperrose.util

import android.graphics.drawable.Icon
import android.os.Bundle
import com.dohex.hyperrose.BuildConfig
import com.dohex.hyperrose.R
import com.xzakota.hyper.notification.focus.FocusNotification

object FocusIslandBridge {
    private const val TICKER_TEXT = "HyperRose"

    fun buildBatteryIslandExtras(
        leftLevel: Int,
        rightLevel: Int,
        islandTimeoutSeconds: Int
    ): Bundle {
        val leftText = if (leftLevel >= 0) leftLevel.toString() else "-"
        val rightText = if (rightLevel >= 0) rightLevel.toString() else "-"

        val leftIcon = Icon.createWithResource(BuildConfig.APPLICATION_ID, R.drawable.img_left)
        val rightIcon = Icon.createWithResource(BuildConfig.APPLICATION_ID, R.drawable.img_right)

        return FocusNotification.buildV3 {
            enableFloat = false
            ticker = TICKER_TEXT
            updatable = true
            isShowNotification = true

            val leftPic = createPicture("miui.focus.pic_left", leftIcon)
            val rightPic = createPicture("miui.focus.pic_right", rightIcon)

            island {
                islandProperty = 1
                islandTimeout = islandTimeoutSeconds

                bigIslandArea {
                    imageTextInfoLeft {
                        type = 1
                        picInfo {
                            type = 1
                            pic = leftPic
                        }
                        textInfo {
                            title = leftText
                            content = "%"
                        }
                    }

                    imageTextInfoRight {
                        type = 2
                        picInfo {
                            type = 1
                            pic = rightPic
                        }
                        textInfo {
                            title = rightText
                            content = "%"
                        }
                    }
                }
                baseInfo {
                    type = 2
                    title = "ROSESELSA EARFREE i5"
                    content = "左耳：右耳：充电盒："
                }
            }
        }
    }
}
