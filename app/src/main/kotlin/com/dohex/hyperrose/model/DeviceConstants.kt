package com.dohex.hyperrose.model

import com.dohex.hyperrose.model.DeviceConstants.DEVICE_NAME_KEYWORDS


/**
 * 设备识别与显示常量。
 *
 * [DEVICE_NAME_KEYWORDS] 用于蓝牙设备名子串匹配（大小写不敏感），
 * 包含所有已知设备名变体。
 */
object DeviceConstants {

    /** 蓝牙设备名匹配关键字列表 */
    val DEVICE_NAME_KEYWORDS = listOf("ROSE EARFREE", "ROSE EARFEEL")

    /** UI 默认显示的设备名 */
    const val DEFAULT_DEVICE_NAME = "ROSE EARFEEL"

    /** 检查设备名是否匹配任意关键字 */
    fun matchesDeviceName(name: String): Boolean =
        DEVICE_NAME_KEYWORDS.any { name.contains(it, ignoreCase = true) }
}
